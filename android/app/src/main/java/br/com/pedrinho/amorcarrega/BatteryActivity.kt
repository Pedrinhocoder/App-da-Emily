package br.com.pedrinho.amorcarrega

import android.Manifest
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.Spanned
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

class BatteryActivity : ComponentActivity() {
    private val prefs by lazy { getSharedPreferences("config", MODE_PRIVATE) }

    private lateinit var ring: BatteryRingView
    private lateinit var levelsTextView: TextView
    private lateinit var thresholdSeekBar: SeekBar
    private lateinit var valueDisplay: TextView
    private lateinit var sliderMarkers: LinearLayout
    private lateinit var checkItem1: LinearLayout
    private lateinit var checkItem2: LinearLayout
    private lateinit var checkItem3: LinearLayout
    private lateinit var checkItem4: LinearLayout
    private lateinit var checkIcon1: TextView
    private lateinit var checkIcon2: TextView
    private lateinit var checkIcon3: TextView
    private lateinit var checkIcon4: TextView
    private lateinit var alertSwitch: Switch
    private lateinit var testReminderBtn: Button

    private val batteryReceiver = object : BroadcastReceiver() {
        private var wasPlugged = false
        override fun onReceive(context: Context, intent: Intent) {
            val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
            if (!wasPlugged && plugged) {
                BatteryMonitorService.stopSound()
            }
            wasPlugged = plugged
            updateRing(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
        super.onCreate(savedInstanceState)
        buildUi()
        // A personalização antiga não é mais usada; as mensagens são sempre escolhidas pelo sistema.
        prefs.edit().remove("message").apply()

        if (Build.VERSION.SDK_INT >= 33) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                100
            )
        }

        startService()
    }

    override fun onStart() {
        super.onStart()
        val sticky = ContextCompat.registerReceiver(
            this,
            batteryReceiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        sticky?.let { updateRing(it) }
        refreshChecklist()
    }

    override fun onResume() {
        super.onResume()
        refreshChecklist()
    }

    override fun onStop() {
        try { unregisterReceiver(batteryReceiver) } catch (_: Exception) {}
        super.onStop()
    }

    private fun updateRing(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        if (level < 0) return
        val pct = if (scale > 0) level * 100 / scale else level

        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val full = status == BatteryManager.BATTERY_STATUS_FULL

        ring.setBattery(pct, plugged, full)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun dp(v: Float) = (v * resources.displayMetrics.density + 0.5f).toInt()
    private fun color(id: Int) = ContextCompat.getColor(this, id)

    private fun card(radiusDp: Int = 24, content: LinearLayout.() -> Unit): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
            background = GradientDrawable().apply {
                cornerRadius = dp(radiusDp).toFloat()
                setColor(color(R.color.card))
                setStroke(dp(1), color(R.color.card_stroke))
            }
            elevation = dp(8).toFloat()
            if (Build.VERSION.SDK_INT >= 28) {
                outlineAmbientShadowColor = color(R.color.wine_shadow)
                outlineSpotShadowColor = color(R.color.wine_shadow)
            }
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(16) }
            content()
        }

    private enum class BtnStyle { FILLED, OUTLINE, SOFT, PROMINENT }

    private fun pillButton(
        label: String,
        style: BtnStyle,
        topMargin: Int = 0,
        icon: String? = null,
        onClick: () -> Unit
    ): Button = Button(this).apply {
        text = if (icon != null) "$icon  $label" else label
        isAllCaps = false
        textSize = if (style == BtnStyle.PROMINENT) 17f else 16f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        stateListAnimator = null
        minHeight = dp(if (style == BtnStyle.PROMINENT) 60 else 52)
        minimumHeight = dp(if (style == BtnStyle.PROMINENT) 60 else 52)
        setPadding(dp(20), 0, dp(20), 0)

        val shape = when (style) {
            BtnStyle.FILLED, BtnStyle.PROMINENT -> GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(color(R.color.btn_start), color(R.color.wine_deep))
            )
            else -> GradientDrawable()
        }
        shape.cornerRadius = dp(if (style == BtnStyle.PROMINENT) 30 else 26).toFloat()
        when (style) {
            BtnStyle.OUTLINE -> {
                shape.setColor(color(R.color.outline_fill))
                shape.setStroke(dp(2f), color(R.color.rose_accent))
            }
            BtnStyle.SOFT -> shape.setColor(color(R.color.blush))
            BtnStyle.PROMINENT -> {
                elevation = dp(6).toFloat()
            }
            BtnStyle.FILLED -> {}
        }

        setTextColor(if (style == BtnStyle.FILLED || style == BtnStyle.PROMINENT) Color.WHITE else color(R.color.wine_text))
        val ripple = if (style == BtnStyle.FILLED || style == BtnStyle.PROMINENT) R.color.ripple_light else R.color.ripple_rose
        background = RippleDrawable(ColorStateList.valueOf(color(ripple)), shape, null)

        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { this.topMargin = topMargin }
        setOnClickListener { onClick() }
    }

    private fun computeLevels(threshold: Int): Triple<Int, Int, Int> {
        val t1 = threshold.coerceIn(16, 50)
        val t2 = 15
        val t3 = 5
        return Triple(t1, t2, t3)
    }

    private fun levelsText(threshold: Int): CharSequence {
        val (t1, t2, t3) = computeLevels(threshold)
        val sb = SpannableStringBuilder()
        fun row(pct: String, desc: String, bulletColor: Int) {
            val start = sb.length
            sb.append("● ")
            sb.setSpan(
                ForegroundColorSpan(bulletColor),
                start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            val pctStart = sb.length
            sb.append(pct)
            sb.setSpan(StyleSpan(Typeface.BOLD), pctStart, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.setSpan(
                ForegroundColorSpan(color(R.color.rose_accent)),
                pctStart, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            sb.append(" → $desc\n")
        }
        row("$t1%", "lembrete", 0xFF4CAF50.toInt())
        row("$t2%", "alerta forte", 0xFFFFB300.toInt())
        row("$t3%", "alerta crítico", 0xFFF44336.toInt())
        val start = sb.length
        sb.append("\n15% e 5% são níveis fixos; 1% é emergência máxima. Os alertas podem ser silenciados a qualquer momento.")
        sb.setSpan(
            ForegroundColorSpan(color(R.color.muted_text)),
            start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        return sb
    }

    private fun buildSliderMarkers(threshold: Int): LinearLayout {
        val (t1, t2, t3) = computeLevels(threshold)
        val max = thresholdSeekBar.max
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply {
                topMargin = dp(4)
                leftMargin = dp(8)
                rightMargin = dp(8)
            }
            for (pct in listOf(t1, t2, t3)) {
                val container = LinearLayout(this@BatteryActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, WRAP, (pct.toFloat() / max)).apply {
                        weight = pct.toFloat() / max
                    }
                }
                val dotColors = listOf(0xFF4CAF50.toInt(), 0xFFFFB300.toInt(), 0xFFF44336.toInt())
                val idx = listOf(t1, t2, t3).indexOf(pct)
                val dot = View(this@BatteryActivity).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(8), dp(8)).apply {
                        gravity = Gravity.START
                    }
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(dotColors[idx])
                        setStroke(dp(1), Color.WHITE)
                    }
                }
                val label = TextView(this@BatteryActivity).apply {
                    text = "${pct}%"
                    textSize = 11f
                    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                    setTextColor(dotColors[idx])
                    setPadding(0, dp(2), 0, 0)
                }
                container.addView(dot)
                container.addView(label)
                addView(container)
            }
            val spacer = View(this@BatteryActivity).apply {
                layoutParams = LinearLayout.LayoutParams(0, dp(1), (max - t1).toFloat() / max).apply {
                    weight = (max - t1).toFloat() / max
                }
            }
            addView(spacer)
        }
    }

    private fun updateLevelsForThreshold(threshold: Int) {
        levelsTextView.text = levelsText(threshold)
        val parent = sliderMarkers.parent as? ViewGroup
        parent?.let {
            val idx = it.indexOfChild(sliderMarkers)
            it.removeView(sliderMarkers)
            sliderMarkers = buildSliderMarkers(threshold)
            it.addView(sliderMarkers, idx)
        }
    }

    private fun checkIconView(ok: Boolean): TextView {
        return TextView(this).apply {
            text = if (ok) "✓" else "!"
            textSize = 18f
            typeface = Typeface.create("sans-serif-bold", Typeface.BOLD)
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(if (ok) 0xFF4CAF50.toInt() else 0xFFFF9800.toInt())
                cornerRadius = dp(14).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(dp(28), dp(28)).apply {
                marginEnd = dp(12)
            }
        }
    }

    private fun buildChecklistRow(
        titleStr: String,
        descStr: String,
        initialOk: Boolean
    ): Pair<LinearLayout, TextView> {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(color(R.color.blush))
                setStroke(dp(1), color(R.color.rose_line))
            }
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply {
                bottomMargin = dp(10)
            }
        }
        val icon = checkIconView(initialOk)
        val textContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        }
        val titleTv = TextView(this).apply {
            text = titleStr
            textSize = 15f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(color(R.color.wine_text))
        }
        val descTv = TextView(this).apply {
            text = descStr
            textSize = 13f
            setTextColor(color(R.color.muted_text))
            setPadding(0, dp(2), 0, 0)
        }
        textContainer.addView(titleTv)
        textContainer.addView(descTv)
        row.addView(icon)
        row.addView(textContainer)
        return row to icon
    }

    private fun blendColors(c1: Int, c2: Int, ratio: Float): Int {
        val r = (Color.red(c1) * (1 - ratio) + Color.red(c2) * ratio).toInt()
        val g = (Color.green(c1) * (1 - ratio) + Color.green(c2) * ratio).toInt()
        val b = (Color.blue(c1) * (1 - ratio) + Color.blue(c2) * ratio).toInt()
        return Color.rgb(r, g, b)
    }

    private fun refreshChecklist() {
        val nm = getSystemService(NotificationManager::class.java)
        val notificationsOk = Build.VERSION.SDK_INT < 33 || nm.areNotificationsEnabled()

        val pm = getSystemService(POWER_SERVICE) as PowerManager
        val batteryOk = pm.isIgnoringBatteryOptimizations(packageName)

        val am = getSystemService(AUDIO_SERVICE) as AudioManager
        val volumeOk = am.getStreamVolume(AudioManager.STREAM_ALARM) > 0

        // A partir do Android 14, o app precisa dessa permissão especial para
        // a tela acender sozinha no alerta de 5% e 1% quando estiver desligada.
        // Em versões antigas ela já vem concedida.
        val fullScreenOk = Build.VERSION.SDK_INT < 34 || nm.canUseFullScreenIntent()

        val okGreen = 0xFF4CAF50.toInt()
        val warnOrange = 0xFFFF9800.toInt()
        val blush = color(R.color.blush)

        fun updateRow(row: View, icon: TextView, ok: Boolean) {
            icon.text = if (ok) "✓" else "!"
            (icon.background as GradientDrawable).setColor(if (ok) okGreen else warnOrange)
            val bg = row.background as GradientDrawable
            bg.setColor(if (ok) blendColors(blush, okGreen, 0.12f) else blush)
        }

        updateRow(checkItem1, checkIcon1, notificationsOk)
        updateRow(checkItem2, checkIcon2, batteryOk)
        updateRow(checkItem3, checkIcon3, volumeOk)
        updateRow(checkItem4, checkIcon4, fullScreenOk)
    }

    private fun buildUi() {
        val padH = dp(20)
        val padTop = dp(16)
        val padBottom = dp(32)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            setPadding(padH, padTop, padH, padBottom)
        }

        val heart = HeartView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(72), dp(72)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = dp(8)
            }
        }
        val title = TextView(this).apply {
            text = "Sua bateria, cuidada"
            textSize = 32f
            typeface = Typeface.create("serif", Typeface.BOLD_ITALIC)
            setTextColor(color(R.color.wine_text))
            gravity = Gravity.CENTER
        }
        val subtitle = TextView(this).apply {
            text = "Um lembrete para o celular dela não morrer."
            textSize = 15f
            typeface = Typeface.create("sans-serif-light", Typeface.ITALIC)
            setTextColor(color(R.color.wine_text))
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.15f)
            setPadding(dp(16), dp(4), dp(16), dp(20))
        }

        val ringSize = minOf(dp(220), resources.displayMetrics.widthPixels - 2 * padH - dp(40))
        ring = BatteryRingView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ringSize, ringSize).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
        }
        val ringCard = card(radiusDp = 32) { addView(ring) }

        val initialThreshold = prefs.getInt("threshold", 30).coerceIn(16, 50)
        prefs.edit().putInt("threshold", initialThreshold).apply()
        thresholdSeekBar = SeekBar(this).apply {
            max = 50
            progress = initialThreshold
            contentDescription = "Primeiro alerta"
            progressTintList = ColorStateList.valueOf(color(R.color.rose_accent))
            progressBackgroundTintList = ColorStateList.valueOf(color(R.color.rose_line))
            thumbTintList = ColorStateList.valueOf(color(R.color.rose_accent))
            splitTrack = false
            setPadding(dp(4), 0, dp(4), 0)
        }
        val valueLabel = TextView(this).apply {
            text = "Primeiro alerta"
            textSize = 15f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(color(R.color.muted_text))
        }
        valueDisplay = TextView(this).apply {
            textSize = 44f
            typeface = Typeface.create("serif", Typeface.BOLD)
            setTextColor(color(R.color.wine_text))
            text = "${thresholdSeekBar.progress}%"
        }
        sliderMarkers = buildSliderMarkers(initialThreshold)

        thresholdSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                val v = p.coerceIn(16, 50)
                valueDisplay.text = "$v%"
                prefs.edit().putInt("threshold", v).apply()
                updateLevelsForThreshold(v)
                if (this@BatteryActivity::testReminderBtn.isInitialized) testReminderBtn.text = "🔋 $v%\nLembrete"
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        levelsTextView = TextView(this).apply {
            text = levelsText(initialThreshold)
            textSize = 15f
            setTextColor(color(R.color.wine_text))
            setLineSpacing(0f, 1.3f)
            setPadding(0, dp(16), 0, 0)
        }
        val thresholdCard = card {
            addView(valueLabel)
            addView(valueDisplay)
            addView(thresholdSeekBar)
            addView(sliderMarkers)
            addView(levelsTextView)
        }

        val alertCard = card {
            val titleTv = TextView(this@BatteryActivity).apply {
                text = "🔔 Alertas de bateria"
                textSize = 17f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setTextColor(color(R.color.wine_text))
            }
            val row = LinearLayout(this@BatteryActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(10), 0, 0)
            }
            val textContainer = LinearLayout(this@BatteryActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
            }
            val statusTv = TextView(this@BatteryActivity).apply {
                textSize = 15f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setTextColor(color(R.color.wine_text))
            }
            val descTv = TextView(this@BatteryActivity).apply {
                text = "Desligue quando quiser silêncio. O monitoramento da bateria continua funcionando normalmente."
                textSize = 13f
                setTextColor(color(R.color.muted_text))
                setLineSpacing(0f, 1.2f)
                setPadding(0, dp(3), 0, 0)
            }
            alertSwitch = Switch(this@BatteryActivity).apply {
                isChecked = prefs.getBoolean(BatteryMonitorService.ALERTS_ENABLED_KEY, true)
                contentDescription = "Ativar ou desligar alertas de bateria"
            }
            fun updateStatus() {
                statusTv.text = if (alertSwitch.isChecked) "Alertas ligados" else "Alertas desligados"
            }
            alertSwitch.setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean(BatteryMonitorService.ALERTS_ENABLED_KEY, checked).apply()
                if (!checked) {
                    BatteryMonitorService.stopSound()
                    sendBroadcast(Intent(BatteryMonitorService.ACTION_STOP_ALERT).setPackage(packageName))
                    Toast.makeText(this@BatteryActivity, "Alertas desligados. O monitoramento continua.", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@BatteryActivity, "Alertas ligados novamente.", Toast.LENGTH_SHORT).show()
                }
                updateStatus()
            }
            updateStatus()
            textContainer.addView(statusTv)
            textContainer.addView(descTv)
            row.addView(textContainer)
            row.addView(alertSwitch)
            addView(titleTv)
            addView(row)
        }

        // ---------- Teste dos alertas ----------
        val testCard = card {
            val titleTv = TextView(this@BatteryActivity).apply {
                text = "🧪 Testar alertas"
                textSize = 17f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setTextColor(color(R.color.wine_text))
            }
            val descTv = TextView(this@BatteryActivity).apply {
                text = "Dispara o alerta de verdade (som, vibração, notificação e tela cheia). Funciona mesmo com os alertas desligados."
                textSize = 13f
                setTextColor(color(R.color.muted_text))
                setLineSpacing(0f, 1.2f)
                setPadding(0, dp(3), 0, dp(12))
            }

            // Espera 10 s antes de disparar: dá tempo de bloquear o celular
            // e conferir como o alerta chega com a tela apagada.
            val delaySwitch = Switch(this@BatteryActivity).apply {
                contentDescription = "Esperar 10 segundos antes de disparar o teste"
            }
            val delayRow = LinearLayout(this@BatteryActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(10), dp(12), dp(10))
                background = GradientDrawable().apply {
                    cornerRadius = dp(16).toFloat()
                    setColor(color(R.color.blush))
                    setStroke(dp(1), color(R.color.rose_line))
                }
                layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(12) }
            }
            val delayText = LinearLayout(this@BatteryActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
            }
            delayText.addView(TextView(this@BatteryActivity).apply {
                text = "Esperar 10 segundos"
                textSize = 15f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setTextColor(color(R.color.wine_text))
            })
            delayText.addView(TextView(this@BatteryActivity).apply {
                text = "Ative, toque em um nível e bloqueie o celular para testar com a tela apagada."
                textSize = 13f
                setTextColor(color(R.color.muted_text))
                setPadding(0, dp(2), 0, 0)
            })
            delayRow.addView(delayText)
            delayRow.addView(delaySwitch)

            val statusTv = TextView(this@BatteryActivity).apply {
                text = "Escolha um nível para testar."
                textSize = 13f
                setTextColor(color(R.color.muted_text))
                setLineSpacing(0f, 1.2f)
                setPadding(0, dp(12), 0, 0)
            }

            fun runTest(level: Int) {
                val nm = getSystemService(NotificationManager::class.java)
                val delay = if (delaySwitch.isChecked) 10 else 0
                AlertTester.run(this@BatteryActivity, level, delay)
                statusTv.text = when {
                    !nm.areNotificationsEnabled() ->
                        "⚠️ As notificações estão desativadas: você vai ouvir o alarme, mas sem botão para desligar. Use \"Parar teste\"."
                    delay > 0 ->
                        "⏳ Alerta de $level% em ${delay}s. Bloqueie o celular agora e confira se a tela acende e se o botão \"Desligar alerta\" aparece."
                    else ->
                        "▶ Alerta de $level% disparado. Desligue pelo botão da notificação ou da tela."
                }
            }

            fun levelButton(label: String, level: () -> Int): Button =
                pillButton(label, BtnStyle.SOFT) { runTest(level()) }.apply {
                    textSize = 14f
                    minHeight = dp(64)
                    minimumHeight = dp(64)
                    setPadding(dp(6), dp(6), dp(6), dp(6))
                }

            testReminderBtn = levelButton("🔋 ${thresholdSeekBar.progress.coerceIn(16, 50)}%\nLembrete") {
                prefs.getInt("threshold", 30).coerceIn(16, 50)
            }
            val btn15 = levelButton("🚨 15%\nForte") { 15 }
            val btn5 = levelButton("🚨🚨 5%\nCrítico") { 5 }
            val btn1 = levelButton("🚨🚨🚨 1%\nEmergência") { 1 }

            fun gridRow(a: Button, b: Button, top: Int): LinearLayout =
                LinearLayout(this@BatteryActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = top }
                    a.layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginEnd = dp(5) }
                    b.layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginStart = dp(5) }
                    addView(a)
                    addView(b)
                }

            val stopBtn = pillButton("Parar teste", BtnStyle.OUTLINE, topMargin = dp(12), icon = "⏹") {
                AlertTester.stopAll(this@BatteryActivity)
                statusTv.text = "⏹ Teste parado."
            }

            addView(titleTv)
            addView(descTv)
            addView(delayRow)
            addView(gridRow(testReminderBtn, btn15, 0))
            addView(gridRow(btn5, btn1, dp(10)))
            addView(stopBtn)
            addView(statusTv)
        }

        val checklistTitle = TextView(this).apply {
            text = "✓ Configurações importantes"
            textSize = 17f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(color(R.color.wine_text))
            setPadding(0, 0, 0, dp(12))
        }
        val (row1, icon1) = buildChecklistRow(
            "Notificações ativadas",
            "Para os alertas aparecerem na tela",
            false
        )
        checkItem1 = row1
        checkIcon1 = icon1
        row1.setOnClickListener {
            try {
                if (Build.VERSION.SDK_INT >= 33) {
                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    intent.putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                    startActivity(intent)
                }
            } catch (_: Exception) {}
        }
        val (row2, icon2) = buildChecklistRow(
            "Ignorar otimização de bateria",
            "Para o app rodar em segundo plano",
            false
        )
        checkItem2 = row2
        checkIcon2 = icon2
        row2.setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (_: Exception) {}
        }
        val (row3, icon3) = buildChecklistRow(
            "Volume do alarme ligado",
            "Para você ouvir o som do aviso",
            false
        )
        checkItem3 = row3
        checkIcon3 = icon3
        row3.setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_SOUND_SETTINGS))
            } catch (_: Exception) {}
        }
        val (row4, icon4) = buildChecklistRow(
            "Tela acende com o alerta",
            "Pro aviso aparecer mesmo com a tela desligada, em 5% e 1%",
            false
        )
        checkItem4 = row4
        checkIcon4 = icon4
        row4.setOnClickListener {
            try {
                if (Build.VERSION.SDK_INT >= 34) {
                    startActivity(
                        Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
                            .setData(Uri.parse("package:$packageName"))
                    )
                }
            } catch (_: Exception) {}
        }
        val checklistCard = card {
            addView(checklistTitle)
            addView(checkItem1)
            addView(checkItem2)
            addView(checkItem3)
            addView(checkItem4)
        }

        root.addView(heart)
        root.addView(title)
        root.addView(subtitle)
        root.addView(ringCard)
        root.addView(thresholdCard)
        root.addView(alertCard)
        root.addView(testCard)
        root.addView(checklistCard)

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            isVerticalScrollBarEnabled = false
            setBackgroundResource(R.drawable.bg_romantic)
            addView(root)
        }

        ViewCompat.setOnApplyWindowInsetsListener(scroll) { _, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or
                        WindowInsetsCompat.Type.displayCutout() or
                        WindowInsetsCompat.Type.ime()
            )
            root.setPadding(
                padH + bars.left, padTop + bars.top + dp(8),
                padH + bars.right, padBottom + bars.bottom
            )
            insets
        }

        setContentView(scroll)
        ViewCompat.requestApplyInsets(scroll)
    }

    private fun startService() {
        val intent = Intent(this, BatteryMonitorService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }
}
