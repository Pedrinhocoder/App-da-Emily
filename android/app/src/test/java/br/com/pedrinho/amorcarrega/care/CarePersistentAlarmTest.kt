package br.com.pedrinho.amorcarrega.care

import android.app.Notification
import android.content.Intent
import android.media.MediaPlayer
import android.os.Looper
import android.widget.Button
import android.view.View
import android.view.ViewGroup
import br.com.pedrinho.amorcarrega.MedicationAlarmActivity
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowMediaPlayer
import java.time.*
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35])
@LooperMode(LooperMode.Mode.PAUSED)
class CarePersistentAlarmTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val store get() = CareStore.get(context)
    private val players = CopyOnWriteArrayList<MediaPlayer>()
    private var service: ServiceController<CareMedicationAlarmService>? = null
    @Before fun setup() {
        runBlocking(kotlinx.coroutines.Dispatchers.IO) { CareDatabase.get(context).clearAllTables() }
        CareAlarms.preferences(context).edit().remove(CareMedicationAlarmService.ACTIVE_KEYS).commit()
        shadowOf(context).grantPermissions("${context.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")
        ShadowMediaPlayer.setMediaInfoProvider { ShadowMediaPlayer.MediaInfo(60000, 0) }
        ShadowMediaPlayer.setCreateListener { player, _ -> players.add(player) }
    }
    @After fun cleanup() {
        service?.destroy(); CareReminderSound.stop()
        await { players.all { shadowOf(it).state == ShadowMediaPlayer.State.END } }
    }
    private fun await(test: () -> Boolean) {
        val deadline = System.nanoTime() + 10_000_000_000
        while (System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if (test()) return
            Thread.sleep(10)
        }
        fail("Persistent alarm did not reach expected state")
    }
    private fun advance(ms: Long) { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms)) }
    private fun sounding() = players.lastOrNull()?.let { shadowOf(it).state == ShadowMediaPlayer.State.STARTED } == true
    private fun active() = CareAlarms.preferences(context).getStringSet(CareMedicationAlarmService.ACTIVE_KEYS, emptySet()).orEmpty()
    private fun dose(name: String = "Medicamento de teste"): Occurrence = runBlocking {
        val date = LocalDate.now()
        val id = store.saveMedicine(Medicine(name = name), Plan(medicineId = 0,
            quantity = "quantidade prescrita", times = listOf(LocalTime.MIDNIGHT), from = date))
        store.snapshot().onDate(date).single { it.medicine.id == id }
    }
    private fun start(dose: Occurrence) {
        service = Robolectric.buildService(CareMedicationAlarmService::class.java,
            Intent(context, CareMedicationAlarmService::class.java).putExtra(CareAlarms.KEY, dose.key))
            .create().startCommand(0, 1)
        await { sounding() }
    }
    private fun confirm(dose: Occurrence) = runBlocking {
        store.confirm(dose, "quantidade real", LocalDate.now(), LocalTime.now().withSecond(0).withNano(0))
        CareAlarms.cancelNotification(context, dose.key)
    }
    @Test fun repeatsAfterSilentIntervalUntilActualAdministrationIsSaved() {
        val dose = dose(); start(dose)
        advance(CareReminderSound.PLAY_MS)
        await { shadowOf(players.single()).state == ShadowMediaPlayer.State.END }
        // Run the continuation which schedules the silent interval.
        shadowOf(Looper.getMainLooper()).idle()
        advance(CareMedicationAlarmService.PAUSE_MS)
        await { players.size == 2 && sounding() }
        assertTrue(runBlocking { store.snapshot().records.isEmpty() })
        confirm(dose)
        await { active().isEmpty() && !sounding() }
        val count = players.size
        advance(20000)
        assertEquals(count, players.size)
    }
    @Test fun confirmingOneOfTwoDosesKeepsTheOtherAlarmActive() {
        val first = dose("Primeiro"); val second = dose("Segundo"); start(first)
        service!!.get().onStartCommand(Intent(context, CareMedicationAlarmService::class.java)
            .putExtra(CareAlarms.KEY, second.key), 0, 2)
        await { second.key in active() }
        confirm(first)
        await { active() == setOf(second.key) }
        assertTrue(sounding())
        confirm(second)
        await { active().isEmpty() && !sounding() }
        assertEquals(2, runBlocking { store.snapshot().records.size })
    }
    @Test fun stickyRestartDropsAlreadyConfirmedKeys() {
        val dose = dose(); confirm(dose)
        CareAlarms.preferences(context).edit().putStringSet(CareMedicationAlarmService.ACTIVE_KEYS, setOf(dose.key)).commit()
        service = Robolectric.buildService(CareMedicationAlarmService::class.java).create().startCommand(0, 1)
        await { active().isEmpty() }
        assertTrue(players.isEmpty())
    }
    @Test fun archiveReconcilesTheAlarmWithoutCreatingAdministration() = runBlocking {
        val dose = dose(); start(dose)
        store.archive(dose.medicine.id)
        CareMedicationAlarmService.changed(context)
        await { active().isEmpty() && !sounding() }
        assertTrue(store.snapshot().records.isEmpty())
    }
    @Test fun lockScreenOpensFormWithoutConfirmingOrStoppingAlarm() {
        val dose = dose(); start(dose)
        val controller = Robolectric.buildActivity(MedicationAlarmActivity::class.java,
            Intent(context, MedicationAlarmActivity::class.java).putExtra(CareAlarms.KEY, dose.key)).setup()
        try {
            val activity = controller.get()
            fun button(view: View): Button? {
                if (view is Button && view.text == "Informar administração") return view
                if (view is ViewGroup) for (index in 0 until view.childCount) button(view.getChildAt(index))?.let { return it }
                return null
            }
            await { button(activity.window.decorView) != null }
            button(activity.window.decorView)!!.performClick()
            val open = shadowOf(activity).nextStartedActivity
            assertEquals(dose.key, open.getStringExtra(CareAlarms.KEY))
            assertTrue(runBlocking { store.snapshot().records.isEmpty() })
            assertTrue(sounding()); assertTrue(dose.key in active())
        } finally { controller.pause().stop().destroy() }
    }
    @Test fun deliveredNotificationHasFullScreenAlarmAndNoAutomaticDismiss() = runBlocking {
        val dose = dose()
        CareAlarms.deliver(context, dose.key)
        val notice = context.getSystemService(android.app.NotificationManager::class.java).activeNotifications
            .single { it.tag == "care:${dose.key}" }.notification
        assertEquals(Notification.CATEGORY_ALARM, notice.category)
        assertNotNull(notice.fullScreenIntent)
        assertEquals(0, notice.flags and Notification.FLAG_AUTO_CANCEL)
        assertNotEquals(0, notice.flags and Notification.FLAG_ONGOING_EVENT)
        assertTrue(store.snapshot().records.isEmpty())
    }
}
