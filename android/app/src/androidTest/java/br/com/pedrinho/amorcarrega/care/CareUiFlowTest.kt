package br.com.pedrinho.amorcarrega.care

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.room.withTransaction
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.com.pedrinho.amorcarrega.MainActivity
import br.com.pedrinho.amorcarrega.friendly
import kotlinx.coroutines.runBlocking
import org.hamcrest.*
import org.hamcrest.Matchers.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class CareUiFlowTest {
    private fun field(label: String): Matcher<View> = object : TypeSafeMatcher<View>() {
        override fun describeTo(description: Description) { description.appendText("field labelled $label") }
        override fun matchesSafely(view: View): Boolean {
            if (view !is EditText) return false
            val parent = view.parent as? ViewGroup ?: return false
            return (0 until parent.childCount).map(parent::getChildAt).filterIsInstance<TextView>()
                .any { it.labelFor == view.id && it.text.toString() == label }
        }
    }
    private fun waitFor(matcher: Matcher<View>) {
        val deadline = android.os.SystemClock.elapsedRealtime() + 30000
        var last: Throwable? = null
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            try { onView(matcher).check(matches(withEffectiveVisibility(androidx.test.espresso.matcher.ViewMatchers.Visibility.VISIBLE))); return }
            catch (failure: Throwable) { last = failure; android.os.SystemClock.sleep(250) }
        }
        throw AssertionError("View did not appear: $matcher", last)
    }
    private fun setDate(date: LocalDate) = object : ViewAction {
        override fun getConstraints(): Matcher<View> = isAssignableFrom(DatePicker::class.java)
        override fun getDescription() = "Choose explicit date"
        override fun perform(controller: UiController, view: View) { (view as DatePicker).updateDate(date.year, date.monthValue - 1, date.dayOfMonth); controller.loopMainThreadUntilIdle() }
    }
    private fun setTime() = object : ViewAction {
        override fun getConstraints(): Matcher<View> = isAssignableFrom(TimePicker::class.java)
        override fun getDescription() = "Choose explicit test time"
        override fun perform(controller: UiController, view: View) { (view as TimePicker).hour = 18; view.minute = 0; controller.loopMainThreadUntilIdle() }
    }
    @Test fun createMedicineConfirmActualDateAndCorrectWithoutLosingAudit() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = CareDatabase.get(context); val store = CareStore(db)
        val name = "Teste UI ${System.nanoTime()}"
        val yesterday = LocalDate.now().minusDays(1)
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            waitFor(withContentDescription("Remédios")); onView(withContentDescription("Remédios")).perform(click())
            waitFor(withText("+  Novo medicamento")); onView(withText("+  Novo medicamento")).perform(click())
            waitFor(field("Nome *")); onView(field("Nome *")).perform(scrollTo(), replaceText(name), closeSoftKeyboard())
            onView(field("Apresentação (ex.: cápsula, solução)")).perform(scrollTo(), replaceText("apresentação de teste"), closeSoftKeyboard())
            onView(field("Concentração")).perform(scrollTo(), replaceText("concentração informada"), closeSoftKeyboard())
            onView(field("Quantidade prescrita por administração *")).perform(scrollTo(), replaceText("quantidade prescrita"), closeSoftKeyboard())
            onView(withText("+  Adicionar horário")).perform(scrollTo(), click())
            onView(isAssignableFrom(TimePicker::class.java)).perform(setTime())
            onView(withId(android.R.id.button1)).perform(click())
            onView(withText(LocalDate.now().friendly())).perform(scrollTo(), click())
            onView(isAssignableFrom(DatePicker::class.java)).perform(setDate(yesterday))
            onView(withId(android.R.id.button1)).perform(click())
            onView(withText("Salvar medicamento")).perform(click())
            waitFor(withContentDescription("Início")); onView(withContentDescription("Início")).perform(click())
            waitFor(withText("Já administrei")); onView(withText("Já administrei")).perform(scrollTo(), click())
            waitFor(field("Quantidade realmente administrada *"))
            onView(field("Quantidade realmente administrada *")).perform(replaceText("quantidade real"), closeSoftKeyboard())
            onView(withText("Data real: ${LocalDate.now().friendly()}")).perform(scrollTo(), click())
            onView(isAssignableFrom(DatePicker::class.java)).perform(setDate(yesterday))
            onView(withId(android.R.id.button1)).perform(click())
            onView(withText("Confirmar registro")).perform(click())
            waitFor(withContentDescription("Calendário")); onView(withContentDescription("Calendário")).perform(click())
            if (java.time.YearMonth.from(yesterday) != java.time.YearMonth.now()) onView(withText("‹")).perform(click())
            waitFor(withContentDescription(startsWith(yesterday.friendly())))
            onView(withContentDescription(startsWith(yesterday.friendly()))).perform(scrollTo(), click())
            waitFor(withText("Corrigir registro")); onView(withText("Corrigir registro")).perform(scrollTo(), click())
            waitFor(field("Motivo da correção *"))
            onView(field("Quantidade realmente administrada *")).perform(scrollTo(), replaceText("quantidade corrigida"), closeSoftKeyboard())
            onView(field("Motivo da correção *")).perform(scrollTo(), replaceText("Ajuste da anotação de teste"), closeSoftKeyboard())
            onView(withText("Salvar correção")).perform(click())
            val deadline = android.os.SystemClock.elapsedRealtime() + 30000
            while (store.snapshot().corrections.none { it.reason == "Ajuste da anotação de teste" } && android.os.SystemClock.elapsedRealtime() < deadline) android.os.SystemClock.sleep(250)
            val snapshot = store.snapshot(); val med = snapshot.medicines.first { it.name == name }
            if (android.os.Build.VERSION.SDK_INT >= 31 && InstrumentationRegistry.getArguments().getString("alarm_mode") == "inexact") assertFalse("This UI run also checks approximate alarms without exact permission", CareAlarms.exactAllowed(context))
            val record = snapshot.records.single { it.medicineId == med.id }
            assertEquals(yesterday.toString(), record.actualDate)
            assertEquals("quantidade corrigida", record.actualQuantity)
            assertEquals("quantidade real", snapshot.corrections.single { it.administrationId == record.id }.oldQuantity)
        } finally {
            scenario.close()
            val med = store.snapshot().medicines.find { it.name == name }
            if (med != null) {
                val pending = PendingIntent.getBroadcast(context, 0, Intent(context, CareAlarmReceiver::class.java).setData(Uri.parse("amorcuida://alarm/${med.id}")), PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
                if (pending != null) { context.getSystemService(AlarmManager::class.java).cancel(pending); pending.cancel() }
                db.withTransaction {
                    val sql = db.openHelper.writableDatabase
                    sql.execSQL("DELETE FROM corrections WHERE administrationId IN (SELECT id FROM administrations WHERE medicineId = ?)", arrayOf(med.id))
                    sql.execSQL("DELETE FROM administrations WHERE medicineId = ?", arrayOf(med.id))
                    sql.execSQL("DELETE FROM plans WHERE medicineId = ?", arrayOf(med.id))
                    sql.execSQL("DELETE FROM medicines WHERE id = ?", arrayOf(med.id))
                }
            }
        }
    }
}
