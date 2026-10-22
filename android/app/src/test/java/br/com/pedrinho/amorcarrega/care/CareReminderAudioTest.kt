package br.com.pedrinho.amorcarrega.care

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.media.MediaPlayer
import android.os.Looper
import android.os.PowerManager
import android.net.Uri
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowMediaPlayer
import org.robolectric.shadows.ShadowPowerManager
import org.robolectric.shadows.util.DataSource
import java.util.concurrent.CopyOnWriteArrayList
import java.time.Duration
import java.io.IOException
import org.robolectric.util.ReflectionHelpers
import java.time.LocalDate
import java.time.LocalTime

/** Verifies the sound requested from Android, not audible speaker output on a real device. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35])
@LooperMode(LooperMode.Mode.PAUSED)
class CareReminderAudioTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val manager get() = context.getSystemService(NotificationManager::class.java)
    private val alarm get() = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
    private val players = CopyOnWriteArrayList<MediaPlayer>()
    @Before fun setupAudio() {
        ShadowMediaPlayer.setMediaInfoProvider { ShadowMediaPlayer.MediaInfo(60000, 0) }
        ShadowMediaPlayer.setCreateListener { player, _ -> players.add(player) }
    }
    private fun await(timeoutMillis: Long = 5000, test: () -> Boolean) {
        val deadline = System.nanoTime() + timeoutMillis * 1_000_000
        while (System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if (test()) return
            Thread.sleep(10)
        }
        fail("Audio operation did not complete")
    }
    private fun running() = players.lastOrNull()?.let { shadowOf(it).state == ShadowMediaPlayer.State.STARTED } == true
    private fun finish(job: Deferred<Unit>, millis: Long = CareReminderSound.PLAY_MS) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis))
        await { job.isCompleted }
        runBlocking { job.await() }
        assertEquals(ShadowMediaPlayer.State.END, shadowOf(players.last()).state)
        assertFalse(ShadowPowerManager.getLatestWakeLock().isHeld)
    }
    private fun play() = CoroutineScope(Dispatchers.IO).async(start = CoroutineStart.UNDISPATCHED) {
        CareReminderSound.play(context, CareAlarms.ensureChannel(context))
    }
    private fun channel() = manager.getNotificationChannel(CareAlarms.ensureChannel(context))!!
    private fun legacy(sound: Uri?, importance: Int = NotificationManager.IMPORTANCE_HIGH,
                       userSound: Boolean = false): NotificationChannel {
        val old = NotificationChannel("care_short_s1_v1", "Canal anterior", importance)
        old.setSound(sound, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT).build())
        old.enableVibration(false)
        // Simulate NotificationManager's persisted USER_LOCKED_SOUND flag, not an app default.
        if (userSound) ReflectionHelpers.setField(old, "mUserLockedFields", 0x20)
        manager.createNotificationChannel(old)
        return old
    }

    @Test fun defaultChannelIsSilentWhileDirectPlayerUsesDeviceAlarm() {
        val channel = channel()
        assertNull(channel.sound)
        assertEquals(alarm, CareReminderSound.allowedUri(context, channel.id))
        assertEquals(AudioAttributes.USAGE_ALARM, channel.audioAttributes.usage)
        assertEquals(AudioAttributes.CONTENT_TYPE_SONIFICATION, channel.audioAttributes.contentType)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, channel.importance)
        assertFalse(channel.canBypassDnd())
    }

    @Test fun migratesOldNotificationSoundWithoutModifyingOldChannel() {
        val notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        legacy(notification)
        val channel = channel()
        assertNotEquals("care_short_s1_v1", channel.id)
        assertNull(channel.sound)
        assertEquals(alarm, CareReminderSound.allowedUri(context, channel.id))
        assertEquals(notification, manager.getNotificationChannel("care_short_s1_v1").sound)
        assertFalse("Preserve the old channel's vibration preference", channel.shouldVibrate())
    }

    @Test fun appSoundDisabledKeepsNewProfileSilent() {
        context.getSharedPreferences("care_preferences", 0).edit().putBoolean("sound", false).commit()
        val channel = channel()
        assertNull(channel.sound)
        assertTrue(channel.id.contains("_s0_"))
        assertNull(CareReminderSound.allowedUri(context, channel.id))
    }

    @Test fun explicitlyMutedLegacyChannelStaysMuted() {
        legacy(null, userSound = true)
        assertNull(CareReminderSound.allowedUri(context, channel().id))
        runBlocking { CareReminderSound.play(context, channel().id) }
        assertTrue(players.isEmpty())
    }

    @Test fun customUserSoundSurvivesMigration() {
        val custom = Uri.parse("content://media/internal/audio/media/123")
        legacy(custom, userSound = true)
        assertNull(channel().sound)
        assertEquals(custom, CareReminderSound.allowedUri(context, channel().id))
    }

    @Test fun blockedLegacyChannelIsNotReenabled() {
        legacy(null, NotificationManager.IMPORTANCE_NONE, userSound = true)
        assertEquals(NotificationManager.IMPORTANCE_NONE, channel().importance)
        assertNull(channel().sound)
    }

    @Test fun quietLegacyChannelKeepsItsImportance() {
        legacy(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), NotificationManager.IMPORTANCE_LOW)
        assertEquals(NotificationManager.IMPORTANCE_LOW, channel().importance)
        assertNull(CareReminderSound.allowedUri(context, channel().id))
    }

    @Test @Config(sdk = [35]) fun silentAppDefaultIsReplacedWhenUserDidNotChooseSilence() {
        val previous = legacy(null)
        assertFalse(previous.hasUserSetSound())
        assertNull(channel().sound)
        assertEquals(alarm, CareReminderSound.allowedUri(context, channel().id))
    }

    @Test fun existingNewChannelIsNeverReset() {
        val existing = NotificationChannel(CareAlarms.channelId(context), "Preferência do usuário", NotificationManager.IMPORTANCE_LOW)
        existing.setSound(null, null)
        manager.createNotificationChannel(existing)
        val channel = channel()
        assertNull(channel.sound)
        assertEquals(NotificationManager.IMPORTANCE_LOW, channel.importance)
    }

    @Test fun deliveredReminderStartsRealPlayerAndStopsWithinFiveSecondsWithoutDuplicateOrAutomaticRecord() = runBlocking {
        val store = CareStore.get(context)
        val today = LocalDate.now()
        // Midnight is already due regardless of the test machine's wall clock.
        val id = store.saveMedicine(Medicine(name = "Teste de áudio"),
            Plan(medicineId = 0, quantity = "quantidade cadastrada", times = listOf(LocalTime.MIDNIGHT), from = today))
        val dose = store.snapshot().onDate(today).single { it.medicine.id == id }
        val delivery = CoroutineScope(Dispatchers.IO).async { CareAlarms.deliver(context, dose.key) }
        await { running() }
        val player = players.single()
        assertEquals(DataSource.toDataSource(context, alarm), shadowOf(player).dataSource)
        assertEquals(AudioAttributes.USAGE_ALARM, shadowOf(player).audioAttributes.usage)
        assertEquals(AudioAttributes.CONTENT_TYPE_SONIFICATION, shadowOf(player).audioAttributes.contentType)
        assertFalse(player.isLooping)
        assertTrue(ShadowPowerManager.getLatestWakeLock().isHeld)
        val notice = manager.activeNotifications.single { it.tag == "care:${dose.key}" }.notification
        assertEquals(channel().id, notice.channelId)
        assertNull(manager.getNotificationChannel(notice.channelId).sound)
        assertEquals(0, notice.flags and Notification.FLAG_INSISTENT)
        assertNotEquals(0, notice.flags and Notification.FLAG_ONLY_ALERT_ONCE)
        // No second, per-notification sound source or repeated administration.
        @Suppress("DEPRECATION")
        assertNull(notice.sound)
        assertTrue(store.snapshot().records.isEmpty())
        CareAlarms.deliver(context, dose.key)
        assertEquals(1, manager.activeNotifications.count { it.tag == "care:${dose.key}" })
        assertEquals(1, players.size)
        finish(delivery)
    }

    @Test fun shortToneCompletionReleasesPlayerBeforeTimeLimit() {
        ShadowMediaPlayer.setMediaInfoProvider { ShadowMediaPlayer.MediaInfo(500, 0) }
        val job = play()
        await { running() }
        finish(job, 500)
    }

    @Test fun simultaneousRemindersShareOneClip() {
        val first = play()
        await { running() }
        val second = play()
        assertEquals(1, players.size)
        finish(first)
        await { second.isCompleted }
        runBlocking { second.await() }
        assertEquals(1, players.size)
    }

    @Test fun playerPreparationFailureReleasesWakeLock() {
        ShadowMediaPlayer.setMediaInfoProvider { null }
        ShadowMediaPlayer.addException(DataSource.toDataSource(context, alarm), IOException("Unreadable alarm"))
        val job = play()
        await { job.isCompleted }
        runBlocking { job.await() }
        assertEquals(ShadowMediaPlayer.State.END, shadowOf(players.single()).state)
        assertFalse(ShadowPowerManager.getLatestWakeLock().isHeld)
    }

    @Test fun alarmWorksWithScreenOffAndNeverOverridesTotalSilence() {
        shadowOf(context.getSystemService(PowerManager::class.java)).setIsInteractive(false)
        val job = play()
        await { running() }
        finish(job)
        manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
        assertNull(CareReminderSound.allowedUri(context, channel().id))
    }

    @Test fun userEnabledChannelSoundNeverGetsASecondPlayer() {
        val configured = NotificationChannel(CareAlarms.channelId(context), "Som escolhido", NotificationManager.IMPORTANCE_HIGH)
        configured.setSound(alarm, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
        manager.createNotificationChannel(configured)
        runBlocking { CareReminderSound.play(context, channel().id) }
        assertTrue(players.isEmpty())
    }

    @Test fun playbackErrorCallbackReleasesPlayer() {
        ShadowMediaPlayer.setMediaInfoProvider {
            ShadowMediaPlayer.MediaInfo(60000, 0).apply { scheduleErrorAtOffset(500, MediaPlayer.MEDIA_ERROR_UNKNOWN, 0) }
        }
        val job = play()
        await { running() }
        finish(job, 500)
    }

    @Test @Config(sdk = [35]) fun stalledPreparationHasBoundedLifetimeAndReleasesWakeLock() {
        ShadowMediaPlayer.setMediaInfoProvider { ShadowMediaPlayer.MediaInfo(60000, -1) }
        val job = play()
        await(10000) { job.isCompleted }
        runBlocking { job.await() }
        assertEquals(ShadowMediaPlayer.State.END, shadowOf(players.single()).state)
        assertFalse(ShadowPowerManager.getLatestWakeLock().isHeld)
    }
}
