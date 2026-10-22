package br.com.pedrinho.amorcarrega.care

import android.app.*
import android.content.*
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import br.com.pedrinho.amorcarrega.MainActivity
import br.com.pedrinho.amorcarrega.R
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.*

object CareAlarms {
    const val KEY = "care_occurrence"
    const val NOTIFICATION_ID = 20000
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    fun exactAllowed(context: Context) = Build.VERSION.SDK_INT < 31 ||
        context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    fun preferences(context: Context) = context.getSharedPreferences("care_preferences", Context.MODE_PRIVATE)
    fun enqueue(context: Context) {
        val app = context.applicationContext
        scope.launch { runCatching { reschedule(app) }.onFailure { Log.e("CareAlarms", "Falha ao programar lembretes", it) } }
    }
    private fun alarmIntent(context: Context, medicineId: Long, key: String? = null) = PendingIntent.getBroadcast(
        context, 0, Intent(context, CareAlarmReceiver::class.java)
            .setData(Uri.parse("amorcuida://alarm/$medicineId")).putExtra(KEY, key),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    suspend fun reschedule(context: Context) = withContext(Dispatchers.IO) {
        mutex.withLock { scheduleSnapshot(context, CareStore.get(context).snapshot()) }
    }
    private fun scheduleSnapshot(context: Context, snapshot: CareSnapshot) {
        val manager = context.getSystemService(AlarmManager::class.java)
        val zone = ZoneId.systemDefault()
        val now = Instant.now()
        val confirmed = snapshot.confirmedKeys
        snapshot.medicines.forEach { medicine ->
            val next = Schedules.next(medicine, snapshot.plans, now, zone, confirmed)
            val pending = alarmIntent(context, medicine.id, next?.key)
            manager.cancel(pending)
            if (next != null) {
                val at = next.instant(zone).toEpochMilli()
                try {
                    if (exactAllowed(context)) manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
                    else manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
                } catch (_: SecurityException) {
                    // Permission can be revoked between the check and the call.
                    manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
                }
            }
        }
        val notifications = context.getSystemService(NotificationManager::class.java)
        notifications.activeNotifications.filter { it.tag?.startsWith("care:") == true }.forEach {
            val key = it.tag.removePrefix("care:")
            val dose = occurrence(snapshot, key)
            if (key in confirmed || dose == null || dose.medicine.archivedAt != null) notifications.cancel(it.tag, it.id)
        }
        CareMedicationAlarmService.changed(context)
    }
    fun occurrence(snapshot: CareSnapshot, key: String): Occurrence? = runCatching {
        val parts = key.split(':', limit = 3)
        val medicine = snapshot.medicines.first { it.id == parts[0].toLong() }
        val dateTime = LocalDateTime.parse(parts[2])
        Schedules.onDate(medicine, snapshot.plans.filter { it.id == parts[1].toLong() }, dateTime.toLocalDate(), ZoneId.systemDefault())
            .firstOrNull { it.key == key }
    }.getOrNull()

    suspend fun deliver(context: Context, key: String) = withContext(Dispatchers.IO) {
        val soundChannel = mutex.withLock {
            val snapshot = CareStore.get(context).snapshot()
            val dose = occurrence(snapshot, key)
            try {
                if (dose != null && dose.medicine.archivedAt == null && key !in snapshot.confirmedKeys &&
                    !dose.instant(ZoneId.systemDefault()).isAfter(Instant.now().plusSeconds(1))) {
                    val alreadyVisible = context.getSystemService(NotificationManager::class.java)
                        .activeNotifications.any { it.tag == "care:$key" }
                    show(context, dose)
                    if (CareMedicationAlarmService.start(context, key) || alreadyVisible) null else channelId(context)
                } else null
            } finally {
                // A notification permission/channel error must not stop the recurring schedule.
                scheduleSnapshot(context, snapshot)
            }
        }
        // If Android denies background service startup (e.g. an approximate alarm), keep
        // the brief audible fallback; opening the alarm screen starts persistent playback.
        if (soundChannel != null) CareReminderSound.play(context, soundChannel)
    }
    fun cancelNotification(context: Context, key: String) {
        context.getSystemService(NotificationManager::class.java).cancel("care:$key", NOTIFICATION_ID)
        CareMedicationAlarmService.changed(context)
    }

    fun channelId(context: Context): String {
        val prefs = preferences(context)
        return "care_direct_v3_s${if (prefs.getBoolean("sound", true)) 1 else 0}_v${if (prefs.getBoolean("vibration", true)) 1 else 0}"
    }
    fun ensureChannel(context: Context): String {
        val prefs = preferences(context)
        val sound = prefs.getBoolean("sound", true)
        val vibration = prefs.getBoolean("vibration", true)
        val channelId = channelId(context)
        val manager = context.getSystemService(NotificationManager::class.java)
        // Never recreate/reset a channel the user has already configured.
        if (manager.getNotificationChannel(channelId) != null) return channelId
        val suffix = "s${if (sound) 1 else 0}_v${if (vibration) 1 else 0}"
        val sources = listOfNotNull(manager.getNotificationChannel("care_alarm_v2_$suffix"),
            manager.getNotificationChannel("care_short_$suffix"))
        val previous = sources.firstOrNull()
        val defaultAlarm = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        val defaultNotification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        // API 30+ distinguishes a deliberately selected sound (including silence) from
        // the app's old default. On older Android, preserve silence/custom URIs conservatively.
        val explicitSound = sources.firstOrNull { old -> if (Build.VERSION.SDK_INT >= 30)
            old.hasUserSetSound()
        else old.sound != defaultNotification && old.sound != defaultAlarm }
        preferences(context).edit().putString(CareReminderSound.URI_PREFIX + channelId,
            if (!sound) "" else if (explicitSound != null) explicitSound.sound?.toString().orEmpty()
            else defaultAlarm.toString()).apply()
        val channel = NotificationChannel(channelId, "Medicamentos · som ${if (sound) "ligado" else "desligado"} · vibração ${if (vibration) "ligada" else "desligada"}", previous?.importance ?: NotificationManager.IMPORTANCE_HIGH)
        channel.description = "Um aviso breve no horário cadastrado. Não confirma administração."
        channel.vibrationPattern = previous?.vibrationPattern ?: if (vibration) longArrayOf(0, 180, 120, 180) else longArrayOf(0)
        channel.enableVibration(vibration && (previous?.shouldVibrate() ?: true))
        if (previous != null) {
            channel.group = previous.group
            channel.setShowBadge(previous.canShowBadge())
        }
        // The notification transports the popup/vibration only. Audio has exactly one owner:
        // the short-lived MediaPlayer, independent of the notification volume stream.
        channel.setSound(null,
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
        manager.createNotificationChannel(channel)
        return channelId
    }
    private fun show(context: Context, dose: Occurrence) {
        val channelId = ensureChannel(context)
        val manager = context.getSystemService(NotificationManager::class.java)
        val open = Intent(context, MainActivity::class.java)
            .setData(Uri.parse("amorcuida://dose/${Uri.encode(dose.key)}")).putExtra(KEY, dose.key)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(context, 0, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val alarm = PendingIntent.getActivity(context, 0,
            Intent(context, br.com.pedrinho.amorcarrega.MedicationAlarmActivity::class.java)
                .setData(Uri.parse("amorcuida://alarm-screen/${Uri.encode(dose.key)}"))
                .putExtra(KEY, dose.key).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val publicVersion = NotificationCompat.Builder(context, channelId).setSmallIcon(R.drawable.ic_care_notification)
            .setContentTitle("Amor, Cuida ♥").setContentText("Você tem um lembrete de medicamento.").build()
        val notification = NotificationCompat.Builder(context, channelId).setSmallIcon(R.drawable.ic_care_notification)
            .setContentTitle("${dose.medicine.name} · ${dose.time}")
            .setContentText("${dose.plan.quantity} · Conforme a prescrição cadastrada")
            .setStyle(NotificationCompat.BigTextStyle().bigText("${dose.plan.quantity}\n${dose.medicine.presentation} ${dose.medicine.concentration}\nToque para informar se foi administrado."))
            .setPriority(NotificationCompat.PRIORITY_MAX).setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setPublicVersion(publicVersion)
            .setContentIntent(alarm).setFullScreenIntent(alarm, true)
            .addAction(0, "Informar administração", pending).setAutoCancel(false).setOngoing(true)
            .setOnlyAlertOnce(true).setWhen(dose.instant(ZoneId.systemDefault()).toEpochMilli()).build()
        // No notification audio or persistent loop: CareReminderSound owns the only player.
        notification.flags = notification.flags and Notification.FLAG_INSISTENT.inv()
        // A notification is a reminder only. Administration is written exclusively by the confirmation form.
        manager.notify("care:${dose.key}", NOTIFICATION_ID, notification)
    }
    fun receive(context: Context, pending: BroadcastReceiver.PendingResult, key: String? = null) {
        val app = context.applicationContext
        scope.launch {
            try { if (key == null) reschedule(app) else deliver(app, key) }
            catch (error: Exception) { Log.e("CareAlarms", "Falha ao restaurar lembretes", error) }
            finally { pending.finish() }
        }
    }
}
class CareAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val key = intent.getStringExtra(CareAlarms.KEY) ?: return
        CareAlarms.receive(context, goAsync(), key)
    }
}
class CareRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
                Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_DATE_CHANGED,
                AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED)) {
            CareAlarms.receive(context, goAsync())
        }
    }
}
