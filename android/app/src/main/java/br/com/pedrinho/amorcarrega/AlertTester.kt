package br.com.pedrinho.amorcarrega

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.PowerManager

/**
 * Dispara alertas de teste pelo mesmo caminho dos alertas reais
 * (notificação, som em loop, vibração e tela cheia).
 *
 * Com atraso, dá tempo de bloquear o celular e ver o alerta chegar com a tela apagada.
 * Um WakeLock parcial mantém a CPU acordada durante a espera, senão o timer
 * poderia atrasar quando o aparelho "dorme".
 */
object AlertTester {
    private val handler = Handler(Looper.getMainLooper())
    private var pending: Runnable? = null
    private var wakeLock: PowerManager.WakeLock? = null

    fun run(context: Context, level: Int, delaySeconds: Int) {
        val app = context.applicationContext
        cancelPending()

        if (delaySeconds <= 0) {
            AlertEngine.fire(app, level, true)
            return
        }

        try {
            val pm = app.getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AmorCarrega:teste").apply {
                acquire((delaySeconds + 15) * 1000L)
            }
        } catch (_: Exception) {}

        val task = Runnable {
            pending = null
            try {
                AlertEngine.fire(app, level, true)
            } finally {
                releaseWakeLock()
            }
        }
        pending = task
        handler.postDelayed(task, delaySeconds * 1000L)
    }

    /** Para tudo: cancela teste agendado, som, vibração, notificações e fecha a tela do alerta. */
    fun stopAll(context: Context) {
        val app = context.applicationContext
        cancelPending()
        AlertEngine.cancelBatteryAlerts(app)
        app.sendBroadcast(Intent(BatteryMonitorService.ACTION_STOP_ALERT).setPackage(app.packageName))
    }

    private fun cancelPending() {
        pending?.let { handler.removeCallbacks(it) }
        pending = null
        releaseWakeLock()
    }

    private fun releaseWakeLock() {
        try { wakeLock?.let { if (it.isHeld) it.release() } } catch (_: Exception) {}
        wakeLock = null
    }
}
