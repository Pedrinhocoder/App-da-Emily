package br.com.pedrinho.amorcarrega.care

import android.app.*
import android.content.*
import android.net.Uri
import android.os.*
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.FileInputStream
import java.time.*

/** Exercises actual AlarmManager delivery and NotificationManager without writing any automatic administration. */
@RunWith(AndroidJUnit4::class)
class CareNativeReminderTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private fun shell(command: String): String = instrumentation.uiAutomation.executeShellCommand(command).use {
        FileInputStream(it.fileDescriptor).bufferedReader().use { reader -> reader.readText() }
    }
    @Test(timeout = 300000) fun offlineAlarmDeliversBriefReminderAndRequiresManualConfirmation() = runBlocking {
        check(shell("getprop ro.kernel.qemu").trim() == "1") { "Use um emulador dedicado para este teste que alterna rede e permissões." }
        val db = CareDatabase.get(context); val store = CareStore(db)
        val manager = context.getSystemService(NotificationManager::class.java)
        val wifi = shell("settings get global wifi_on").trim()
        val data = shell("settings get global mobile_data").trim()
        var id = 0L
        var dose: Occurrence? = null
        try {
            if (Build.VERSION.SDK_INT >= 33) shell("pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS")
            shell("svc wifi disable"); shell("svc data disable")
            val target = ZonedDateTime.now().plusMinutes(2).withSecond(0).withNano(0)
            id = store.saveMedicine(Medicine(name = "Teste de alarme offline ${System.nanoTime()}"),
                Plan(medicineId = 0, quantity = "quantidade de teste", times = listOf(target.toLocalTime()), from = target.toLocalDate()))
            val snapshot = store.snapshot()
            dose = snapshot.onDate(target.toLocalDate()).first { it.medicine.id == id }
            if (Build.VERSION.SDK_INT >= 31) {
                shell("appops set ${context.packageName} SCHEDULE_EXACT_ALARM allow")
                assertTrue(CareAlarms.exactAllowed(context))
            }
            CareAlarms.reschedule(context)
            val deadline = SystemClock.elapsedRealtime() + Duration.between(Instant.now(), target.toInstant()).toMillis().coerceAtLeast(0) + 90000
            var notification = manager.activeNotifications.firstOrNull { it.tag == "care:${dose.key}" }
            while (notification == null && SystemClock.elapsedRealtime() < deadline) {
                SystemClock.sleep(500)
                notification = manager.activeNotifications.firstOrNull { it.tag == "care:${dose.key}" }
            }
            assertNotNull("Native offline reminder was not delivered", notification)
            assertEquals(Notification.CATEGORY_REMINDER, notification!!.notification.category)
            assertEquals(0, notification.notification.flags and Notification.FLAG_INSISTENT)
            assertTrue("A reminder must not confirm administration", store.snapshot().records.none { it.medicineId == id })
            store.confirm(dose, "quantidade realmente informada", LocalDate.now(), LocalTime.now().withSecond(0).withNano(0))
            CareAlarms.cancelNotification(context, dose.key); CareAlarms.reschedule(context)
            assertTrue(manager.activeNotifications.none { it.tag == "care:${dose.key}" })
            assertEquals(1, store.snapshot().recordedOn(LocalDate.now()).count { it.medicineId == id })
        } finally {
            dose?.let { CareAlarms.cancelNotification(context, it.key) }
            if (id != 0L) {
                val pending = PendingIntent.getBroadcast(context, 0, Intent(context, CareAlarmReceiver::class.java)
                    .setData(Uri.parse("amorcuida://alarm/$id")), PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
                if (pending != null) { context.getSystemService(AlarmManager::class.java).cancel(pending); pending.cancel() }
                db.withTransaction {
                    val sql = db.openHelper.writableDatabase
                    sql.execSQL("DELETE FROM corrections WHERE administrationId IN (SELECT id FROM administrations WHERE medicineId = ?)", arrayOf(id))
                    sql.execSQL("DELETE FROM administrations WHERE medicineId = ?", arrayOf(id))
                    sql.execSQL("DELETE FROM plans WHERE medicineId = ?", arrayOf(id))
                    sql.execSQL("DELETE FROM medicines WHERE id = ?", arrayOf(id))
                }
            }
            if (wifi == "1") shell("svc wifi enable")
            if (data == "1") shell("svc data enable")
        }
    }
}
