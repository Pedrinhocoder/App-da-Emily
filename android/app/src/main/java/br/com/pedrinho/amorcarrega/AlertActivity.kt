package br.com.pedrinho.amorcarrega

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

class AlertActivity : ComponentActivity() {
    companion object {
        const val EXTRA_LEVEL = "alert_level"
        const val EXTRA_MESSAGE = "alert_message"
        const val EXTRA_TEST = "alert_test"
    }

    // Em teste o celular geralmente está no carregador; não pode fechar sozinho por isso.
    private var isTest = false

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_BATTERY_CHANGED) {
                val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
                if (plugged && !isTest) finishAlert()
            } else if (intent.action == BatteryMonitorService.ACTION_STOP_ALERT) {
                finishAlert()
            }
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun color(id: Int) = ContextCompat.getColor(this, id)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }

        val level = intent.getIntExtra(EXTRA_LEVEL, 5).coerceIn(0, 100)
        isTest = intent.getBooleanExtra(EXTRA_TEST, false)
        val message = intent.getStringExtra(EXTRA_MESSAGE)
            ?: LoveMessages.pickFor(level, 30)

        val critical = level <= 5
        val emergency = level <= 1

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(28), dp(36), dp(28), dp(36))
        }

        val icon = TextView(this).apply {
            text = if (emergency) "🚨" else if (critical) "❤️‍🔥" else "🔋"
            textSize = 60f
            gravity = Gravity.CENTER
        }

        val title = TextView(this).apply {
            val base = when {
                emergency -> "1% DE BATERIA"
                critical -> "BATERIA CRÍTICA"
                else -> "AMOR, CARREGA O CELULAR"
            }
            text = if (isTest) "🧪 TESTE\n$base" else base
            textSize = if (emergency) 26f else 24f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            setTextColor(color(R.color.wine_text))
            gravity = Gravity.CENTER
            setPadding(0, dp(14), 0, dp(6))
        }

        val percent = TextView(this).apply {
            text = "$level%"
            textSize = 52f
            typeface = Typeface.create("serif", Typeface.BOLD)
            setTextColor(color(R.color.rose_accent))
            gravity = Gravity.CENTER
        }

        val msg = TextView(this).apply {
            text = message
            textSize = 17f
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            setTextColor(color(R.color.wine_text))
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.24f)
            setPadding(dp(6), dp(14), dp(6), 0)
        }

        val messageCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(28), dp(24), dp(26))
            background = GradientDrawable().apply {
                cornerRadius = dp(28).toFloat()
                setColor(color(R.color.card))
                setStroke(dp(1), color(R.color.card_stroke))
            }
            elevation = dp(10).toFloat()
            if (Build.VERSION.SDK_INT >= 28) {
                outlineAmbientShadowColor = color(R.color.wine_shadow)
                outlineSpotShadowColor = color(R.color.wine_shadow)
            }
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
            addView(icon)
            addView(title)
            addView(percent)
            addView(msg)
        }

        // Botão de desligar o alerta, igual ao "desligar" de um despertador:
        // para o som e a vibração na hora e fecha a tela.
        val dismissButton = Button(this).apply {
            text = "🔕  Desligar alerta"
            isAllCaps = false
            textSize = 18f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            stateListAnimator = null
            minHeight = dp(60)
            minimumHeight = dp(60)
            setTextColor(android.graphics.Color.WHITE)
            val shape = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(color(R.color.btn_start), color(R.color.wine_deep))
            ).apply { cornerRadius = dp(30).toFloat() }
            background = RippleDrawable(ColorStateList.valueOf(color(R.color.ripple_light)), shape, null)
            elevation = dp(6).toFloat()
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(24) }
            setOnClickListener { finishAlert() }
        }

        val hint = TextView(this).apply {
            text = "🔌 Coloque o telefone no carregador\nou toque no botão acima para desligar este aviso."
            textSize = 14f
            setTextColor(color(R.color.muted_text))
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.2f)
            setPadding(dp(10), dp(16), dp(10), 0)
        }

        root.addView(messageCard)
        root.addView(dismissButton)
        root.addView(hint)

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            isVerticalScrollBarEnabled = false
            setBackgroundResource(R.drawable.bg_romantic)
            addView(root)
        }

        ViewCompat.setOnApplyWindowInsetsListener(scroll) { _, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            root.setPadding(
                root.paddingLeft + bars.left, dp(36) + bars.top,
                root.paddingRight + bars.right, dp(36) + bars.bottom
            )
            insets
        }

        setContentView(scroll)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Se o alerta de 1% chegar com o de 5% ainda aberto, reaproveita esta tela
        // (singleTask) e a redesenha com o nível novo, em vez de empilhar duas telas.
        setIntent(intent)
        recreate()
    }

    override fun onBackPressed() {
        // O alerta permanece aberto até o carregador ser conectado
        // ou a pessoa tocar em "Desligar alerta".
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(
            this,
            batteryReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_BATTERY_CHANGED)
                addAction(BatteryMonitorService.ACTION_STOP_ALERT)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStop() {
        try { unregisterReceiver(batteryReceiver) } catch (_: Exception) {}
        super.onStop()
    }

    private fun finishAlert() {
        AlertEngine.cancelBatteryAlerts(this)
        finishAndRemoveTask()
    }
}
