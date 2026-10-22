package br.com.pedrinho.amorcarrega

import android.content.*
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.ScrollView
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import br.com.pedrinho.amorcarrega.care.*
import kotlinx.coroutines.*

/** Lock-screen alarm; opening the form does not confirm or stop audio. */
class MedicationAlarmActivity : ComponentActivity() {
    private var key: String? = null
    private var generation = 0
    private val events = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) { reload() }
    }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        if (Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(true); setTurnScreenOn(true) }
        else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        key = intent.getStringExtra(CareAlarms.KEY)
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent); setIntent(intent); key = intent.getStringExtra(CareAlarms.KEY); reload()
    }
    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(this, events, IntentFilter(CareMedicationAlarmService.CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
    }
    override fun onStop() { unregisterReceiver(events); super.onStop() }
    override fun onResume() { super.onResume(); reload() }
    private fun reload() {
        val ticket = ++generation
        lifecycleScope.launch {
            val snapshot = try { withContext(Dispatchers.IO) { CareStore.get(this@MedicationAlarmActivity).snapshot() } }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { return@launch }
            if (ticket != generation) return@launch
            val pending = CareMedicationAlarmService.pending(snapshot, setOfNotNull(key))
            val dose = pending.firstOrNull() ?: run { finish(); return@launch }
            CareMedicationAlarmService.start(this@MedicationAlarmActivity, dose.key)
            val ui = CareUi(this@MedicationAlarmActivity)
            val body = ui.column(24).apply {
                setBackgroundColor(ui.color(R.color.care_background))
                addView(ui.space(28)); addView(ui.text("♥ Amor, Cuida", 22f, ui.accent, true))
                addView(ui.space(24)); addView(ui.text("Hora do medicamento", 30f, bold = true))
                addView(ui.text("${dose.time}", 52f, ui.accent, true))
                addView(ui.text(dose.date.friendly(), 17f, ui.muted)); addView(ui.space(24))
                addView(ui.card().apply {
                    addView(ui.text(dose.medicine.name, 26f, bold = true))
                    addView(ui.text("${dose.medicine.presentation} ${dose.medicine.concentration}".trim(), 16f, ui.muted))
                    addView(ui.space()); addView(ui.text(dose.plan.quantity, 21f, ui.accent, true))
                    addView(ui.text("Conforme a prescrição cadastrada.", 13f, ui.muted))
                })
                addView(ui.text("O alarme continua até você salvar a confirmação da administração. Abrir o formulário não confirma a dose.", 15f))
                addView(ui.button("Informar administração") {
                    startActivity(Intent(this@MedicationAlarmActivity, MainActivity::class.java)
                        .putExtra(CareAlarms.KEY, dose.key)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                })
            }
            val root = ScrollView(this@MedicationAlarmActivity).apply { isFillViewport = true; addView(body) }
            ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
                val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom); insets
            }
            setContentView(root); ViewCompat.requestApplyInsets(root)
        }
    }
}
