package br.com.pedrinho.amorcarrega.care

import android.app.AlertDialog
import android.widget.Button
import android.widget.EditText
import android.widget.TimePicker
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import br.com.pedrinho.amorcarrega.MainActivity
import br.com.pedrinho.amorcarrega.MedicineEditorActivity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowDialog
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import java.io.File
import java.time.*

/** Renders the real Android View code with Robolectric/Skia, not a design mock. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w412dp-h915dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class CareScreensTest {
    private fun find(view: View, predicate: (View) -> Boolean): View? {
        if (predicate(view)) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) find(view.getChildAt(index), predicate)?.let { return it }
        return null
    }
    private fun waitFor(view: View, text: String) {
        val deadline = System.nanoTime() + 20_000_000_000
        while (System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if (find(view) { it is TextView && it.text.toString().contains(text) } != null) return
            Thread.sleep(20)
        }
        fail("Missing real application UI: $text")
    }
    private fun input(root: View, label: String): EditText = find(root) { view ->
        if (view !is EditText) false else {
            val parent = view.parent as? ViewGroup
            parent != null && (0 until parent.childCount).map(parent::getChildAt).filterIsInstance<TextView>()
                .any { it.labelFor == view.id && it.text.toString() == label }
        }
    } as? EditText ?: throw AssertionError("Input missing: $label")
    private fun click(root: View, label: String) {
        val button = find(root) { it is Button && it.text.toString() == label }
        assertNotNull("Button missing: $label", button); button!!.performClick()
        shadowOf(Looper.getMainLooper()).idle()
    }
    private fun awaitData(test: () -> Boolean) {
        val deadline = System.nanoTime() + 20_000_000_000
        while (System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if (test()) return
            Thread.sleep(20)
        }
        fail("Form did not persist the expected data")
    }
    private fun capture(view: View, name: String) {
        val width = 824; val height = 1830
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, width, height)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        assertTrue("Application background was not drawn", android.graphics.Color.alpha(bitmap.getPixel(2, height / 2)) > 0)
        val directory = File("build/screenshots").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    @Test fun rendersHomeCalendarHistoryAndEditorFromRealLocalData() {
        val context = RuntimeEnvironment.getApplication()
        shadowOf(context).grantPermissions("${context.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")
        val store = CareStore.get(context)
        val now = LocalDateTime.now().minusMinutes(5)
        runBlocking {
            val id = store.saveMedicine(Medicine(name = "Medicamento de teste", presentation = "Cadastro demonstrativo", concentration = "Informação da prescrição"),
                Plan(medicineId = 0, quantity = "Quantidade de teste", times = listOf(LocalTime.of(8, 0), LocalTime.of(18, 0)), from = LocalDate.now().minusDays(5)))
            val dose = store.snapshot().onDate(LocalDate.now().minusDays(1)).first { it.medicine.id == id }
            store.confirm(dose, "Quantidade informada", now.toLocalDate(), now.toLocalTime())
        }
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup().visible()
        val root = controller.get().window.decorView
        try {
            waitFor(root, "Medicamento de teste"); capture(root, "inicio-2-0")
            for ((label, title, file) in listOf(Triple("Remédios", "Seus medicamentos", "medicamentos-2-0"),
                    Triple("Calendário", "Calendário do cuidado", "calendario-2-0"), Triple("Histórico", "Cada cuidado fica aqui", "historico-2-0"))) {
                val item = find(root) { it.contentDescription?.toString() == label }
                assertNotNull("Navigation not found: $label", item); item!!.performClick()
                waitFor(root, title); capture(root, file)
            }
            val editor = Robolectric.buildActivity(MedicineEditorActivity::class.java).setup().visible()
            try {
                waitFor(editor.get().window.decorView, "Quantidade prescrita por administração")
                val editorRoot = editor.get().window.decorView
                capture(editorRoot, "cadastro-2-0")
                input(editorRoot, "Nome *").setText("Cadastro pela interface")
                input(editorRoot, "Quantidade prescrita por administração *").setText("Quantidade cadastrada na interface")
                click(editorRoot, "+  Adicionar horário")
                val timeDialog = ShadowDialog.getLatestDialog() as AlertDialog
                val picker = find(timeDialog.window!!.decorView) { it is TimePicker } as TimePicker
                picker.hour = 19; picker.minute = 0
                timeDialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
                click(editorRoot, "Salvar medicamento")
                awaitData { runBlocking { store.snapshot().medicines.any { it.name == "Cadastro pela interface" } } }
            } finally { editor.pause().stop().destroy() }
            controller.pause().resume().visible()
            find(root) { it.contentDescription?.toString() == "Início" }!!.performClick()
            waitFor(root, "Cadastro pela interface")
            click(root, "Já administrei")
            val confirmation = ShadowDialog.getLatestDialog() as AlertDialog
            input(confirmation.window!!.decorView, "Quantidade realmente administrada *").setText("Quantidade digitada")
            confirmation.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            awaitData { runBlocking { store.snapshot().records.any { it.actualQuantity == "Quantidade digitada" } } }
            waitFor(root, "Quantidade digitada")
            find(root) { it.contentDescription?.toString() == "Histórico" }!!.performClick()
            click(root, "Corrigir registro")
            val correction = ShadowDialog.getLatestDialog() as AlertDialog
            input(correction.window!!.decorView, "Quantidade realmente administrada *").setText("Correção pela interface")
            input(correction.window!!.decorView, "Motivo da correção *").setText("Correção de anotação em teste")
            correction.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            awaitData { runBlocking { store.snapshot().corrections.any { it.reason == "Correção de anotação em teste" } } }
            val updated = runBlocking { store.snapshot() }
            assertTrue(updated.records.any { it.actualQuantity == "Correção pela interface" })
            assertEquals("Quantidade digitada", updated.corrections.single { it.reason == "Correção de anotação em teste" }.oldQuantity)
        } finally { controller.pause().stop().destroy() }
    }
}
