package br.com.pedrinho.amorcarrega.care

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import java.time.LocalDate
import java.time.LocalTime

/** Verifies the sound requested from Android, not audible speaker output on a real device. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35])
class CareReminderAudioTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val manager get() = context.getSystemService(NotificationManager::class.java)
    private val alarm get() = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
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

    @Test fun defaultChannelUsesDeviceAlarmWithAlarmAudioAttributes() {
        val channel = channel()
        assertEquals(alarm, channel.sound)
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
        assertEquals(alarm, channel.sound)
        assertEquals(notification, manager.getNotificationChannel("care_short_s1_v1").sound)
        assertFalse("Preserve the old channel's vibration preference", channel.shouldVibrate())
    }

    @Test fun appSoundDisabledKeepsNewProfileSilent() {
        context.getSharedPreferences("care_preferences", 0).edit().putBoolean("sound", false).commit()
        val channel = channel()
        assertNull(channel.sound)
        assertTrue(channel.id.contains("_s0_"))
    }

    @Test fun explicitlyMutedLegacyChannelStaysMuted() {
        legacy(null, userSound = true)
        assertNull(channel().sound)
    }

    @Test fun customUserSoundSurvivesMigration() {
        val custom = Uri.parse("content://media/internal/audio/media/123")
        legacy(custom, userSound = true)
        assertEquals(custom, channel().sound)
    }

    @Test fun blockedLegacyChannelIsNotReenabled() {
        legacy(null, NotificationManager.IMPORTANCE_NONE, userSound = true)
        assertEquals(NotificationManager.IMPORTANCE_NONE, channel().importance)
        assertNull(channel().sound)
    }

    @Test fun quietLegacyChannelKeepsItsImportance() {
        legacy(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), NotificationManager.IMPORTANCE_LOW)
        assertEquals(NotificationManager.IMPORTANCE_LOW, channel().importance)
    }

    @Test @Config(sdk = [35]) fun silentAppDefaultIsReplacedWhenUserDidNotChooseSilence() {
        val previous = legacy(null)
        assertFalse(previous.hasUserSetSound())
        assertEquals(alarm, channel().sound)
    }

    @Test fun existingNewChannelIsNeverReset() {
        val existing = NotificationChannel(CareAlarms.channelId(context), "Preferência do usuário", NotificationManager.IMPORTANCE_LOW)
        existing.setSound(null, null)
        manager.createNotificationChannel(existing)
        val channel = channel()
        assertNull(channel.sound)
        assertEquals(NotificationManager.IMPORTANCE_LOW, channel.importance)
    }

    @Test fun deliveredReminderUsesOnlyTheAlarmChannelWithoutPersistentLoopOrAutomaticRecord() = runBlocking {
        val store = CareStore.get(context)
        val today = LocalDate.now()
        // Midnight is already due regardless of the test machine's wall clock.
        val id = store.saveMedicine(Medicine(name = "Teste de áudio"),
            Plan(medicineId = 0, quantity = "quantidade cadastrada", times = listOf(LocalTime.MIDNIGHT), from = today))
        val dose = store.snapshot().onDate(today).single { it.medicine.id == id }
        CareAlarms.deliver(context, dose.key)
        val notice = manager.activeNotifications.single { it.tag == "care:${dose.key}" }.notification
        assertEquals(channel().id, notice.channelId)
        assertEquals(alarm, manager.getNotificationChannel(notice.channelId).sound)
        assertEquals(0, notice.flags and Notification.FLAG_INSISTENT)
        assertNotEquals(0, notice.flags and Notification.FLAG_ONLY_ALERT_ONCE)
        // No second, per-notification sound source or repeated administration.
        @Suppress("DEPRECATION")
        assertNull(notice.sound)
        assertTrue(store.snapshot().records.isEmpty())
        CareAlarms.deliver(context, dose.key)
        assertEquals(1, manager.activeNotifications.count { it.tag == "care:${dose.key}" })
    }
}
