package br.com.pedrinho.amorcarrega.care

import android.app.*
import android.content.*
import android.net.Uri
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import br.com.pedrinho.amorcarrega.MedicationAlarmActivity
import br.com.pedrinho.amorcarrega.R
import kotlinx.coroutines.*

/** Owns intermittent medication audio until all delivered, still-valid doses are recorded. */
class CareMedicationAlarmService : Service() {
    companion object {
        const val ACTIVE_KEYS = "active_medication_alarm_keys"
        const val CHANGED = "br.com.pedrinho.amorcarrega.MEDICATION_ALARM_CHANGED"
        const val STATUS_ID = 21000
        const val PAUSE_MS = 3000L
        fun start(context: Context, key: String): Boolean = try {
            ContextCompat.startForegroundService(context, Intent(context, CareMedicationAlarmService::class.java)
                .putExtra(CareAlarms.KEY, key)); true
        } catch (error: RuntimeException) {
            Log.e("CareMedicationAlarm", "Android não permitiu iniciar alarme persistente; confira alarmes exatos", error)
            false
        }
        fun changed(context: Context) { context.sendBroadcast(Intent(CHANGED).setPackage(context.packageName)) }
        fun pending(snapshot: CareSnapshot, keys: Set<String>): List<Occurrence> = keys.mapNotNull { key ->
            CareAlarms.occurrence(snapshot, key)?.takeIf {
                key !in snapshot.confirmedKeys && it.medicine.archivedAt == null &&
                    !it.instant(java.time.ZoneId.systemDefault()).isAfter(java.time.Instant.now())
            }
        }.sortedBy { it.instant(java.time.ZoneId.systemDefault()) }
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var loop: Job? = null
    private var refresh: Job? = null
    private val keys = linkedSetOf<String>()
    private lateinit var wake: PowerManager.WakeLock
    private val events = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) { reconcile() }
    }
    override fun onCreate() {
        super.onCreate()
        wake = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AmorCuida:activeMedicationAlarm")
            .apply { setReferenceCounted(false) }
        keys.addAll(CareAlarms.preferences(this).getStringSet(ACTIVE_KEYS, emptySet()).orEmpty())
        ContextCompat.registerReceiver(this, events, IntentFilter(CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
    }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.getStringExtra(CareAlarms.KEY)?.let(keys::add)
        persist()
        // Enter foreground before asynchronous Room work, satisfying Android's deadline.
        startForeground(STATUS_ID, status(null))
        reconcile()
        return START_STICKY
    }
    private fun persist() { CareAlarms.preferences(this).edit().putStringSet(ACTIVE_KEYS, keys.toSet()).apply() }
    private fun status(dose: Occurrence?): Notification {
        val key = dose?.key ?: keys.firstOrNull()
        val intent = Intent(this, MedicationAlarmActivity::class.java).putExtra(CareAlarms.KEY, key)
            .setData(Uri.parse("amorcuida://active-medication/${Uri.encode(key.orEmpty())}"))
        val open = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CareAlarms.ensureChannel(this))
            .setSmallIcon(R.drawable.ic_care_notification).setContentTitle("Alarme de medicamento ativo")
            .setContentText(dose?.let { "${it.medicine.name} · ${it.time} · informe a administração" }
                ?: "Verificando lembretes sem confirmação…")
            .setContentIntent(open).setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setSilent(true).setOngoing(true).setOnlyAlertOnce(true).build()
    }
    private fun reconcile() {
        refresh?.cancel(); loop?.cancel()
        refresh = scope.launch {
            try {
                val checked = keys.toSet()
                val snapshot = withContext(Dispatchers.IO) { CareStore.get(this@CareMedicationAlarmService).snapshot() }
                val pending = pending(snapshot, checked)
                val valid = pending.map { it.key }.toSet()
                keys.removeAll(checked - valid); persist()
                if (keys.isEmpty()) {
                    if (wake.isHeld) wake.release()
                    CareReminderSound.stop(); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
                    return@launch
                }
                getSystemService(NotificationManager::class.java).notify(STATUS_ID, status(pending.firstOrNull()))
                loop = scope.launch {
                    while (isActive) {
                        // Keep the three-second silent interval alive with the screen off.
                        // Renewal is bounded; process death never leaves an indefinite lock.
                        val channel = CareAlarms.ensureChannel(this@CareMedicationAlarmService)
                        val audible = CareReminderSound.allowedUri(this@CareMedicationAlarmService, channel) != null
                        if (audible) {
                            wake.acquire(60000)
                            CareReminderSound.play(this@CareMedicationAlarmService, channel)
                        } else if (wake.isHeld) wake.release()
                        delay(if (audible) PAUSE_MS else 30000L)
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                // Keep persisted keys for a sticky restart; never mark a dose administered.
                Log.e("CareMedicationAlarm", "Não foi possível verificar os alarmes ativos", error)
            }
        }
    }
    override fun onDestroy() {
        unregisterReceiver(events); scope.cancel(); CareReminderSound.stop()
        if (wake.isHeld) wake.release()
        super.onDestroy()
    }
}
