package br.com.pedrinho.amorcarrega

import android.app.*
import android.os.Bundle
import android.text.InputType
import android.view.*
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import br.com.pedrinho.amorcarrega.care.*
import kotlinx.coroutines.*
import java.time.*

class MedicineEditorActivity : ComponentActivity() {
    private lateinit var ui: CareUi
    private lateinit var nameField: EditText
    private lateinit var presentationField: EditText
    private lateinit var concentrationField: EditText
    private lateinit var quantityField: EditText
    private lateinit var notesField: EditText
    private lateinit var intervalField: EditText
    private lateinit var frequency: Spinner
    private lateinit var timeList: LinearLayout
    private lateinit var weekdaysView: GridLayout
    private lateinit var intervalView: LinearLayout
    private lateinit var fromButton: Button
    private lateinit var untilButton: Button
    private lateinit var errorView: TextView
    private lateinit var saveButton: Button
    private var medicine: Medicine? = null
    private var from = LocalDate.now()
    private var until: LocalDate? = null
    private var weekdays = 127
    private val times = sortedSetOf<LocalTime>()
    private var busy = false
    private var initialForm = ""
    private var built = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge(); ui = CareUi(this)
        setContentView(ui.column(24).apply { addView(ui.text("Preparando o cadastro…", 18f)) })
        lifecycleScope.launch {
            try {
                val snapshot = withContext(Dispatchers.IO) { CareStore.get(this@MedicineEditorActivity).snapshot() }
                val id = intent.getLongExtra("medicine_id", 0)
                medicine = snapshot.medicines.find { it.id == id }
                if (id != 0L && medicine == null) { finish(); return@launch }
                val plan = snapshot.plans.filter { it.medicineId == id && !it.superseded }.maxByOrNull { it.from }
                // An ended prescription must be deliberately entered again before resuming reminders.
                buildForm(plan?.takeIf { it.until == null || !it.until.isBefore(LocalDate.now()) }, savedInstanceState)
            } catch (_: Exception) {
                Toast.makeText(this@MedicineEditorActivity, "Não foi possível abrir o cadastro. Tente novamente.", Toast.LENGTH_LONG).show(); finish()
            }
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = leave()
        })
    }
    private fun leave() {
        if (busy) return
        if (built && formState().toString() != initialForm) {
            AlertDialog.Builder(this).setTitle("Descartar alterações?").setMessage("As alterações deste formulário ainda não foram salvas.")
                .setNegativeButton("Continuar editando", null).setPositiveButton("Descartar") { _, _ -> finish() }.show()
        } else finish()
    }
    private fun pickDate(value: LocalDate, action: (LocalDate) -> Unit) {
        DatePickerDialog(this, { _, year, month, day -> action(LocalDate.of(year, month + 1, day)) },
            value.year, value.monthValue - 1, value.dayOfMonth).show()
    }
    private fun buildForm(plan: Plan?, state: Bundle?) {
        from = state?.getString("from")?.let(LocalDate::parse) ?: plan?.from?.let { maxOf(it, LocalDate.now()) } ?: LocalDate.now()
        until = if (state != null) state.getString("until")?.let(LocalDate::parse) else plan?.until?.takeIf { !it.isBefore(from) }
        weekdays = state?.getInt("weekdays") ?: plan?.weekdays ?: 127
        times.clear(); times.addAll(state?.getStringArrayList("times")?.map(LocalTime::parse) ?: plan?.times ?: emptyList())
        val root = ui.column().apply { setBackgroundColor(ui.color(R.color.care_background)) }
        val header = ui.column(20).apply {
            addView(ui.button("‹  Voltar", false) { leave() })
            addView(ui.space(12)); addView(ui.text(if (medicine == null) "Novo medicamento" else "Editar medicamento", 27f, bold = true))
            addView(ui.text("Uma rotina de cuidado, do seu jeito.", 14f, ui.muted))
        }
        root.addView(header)
        val content = ui.column(20)
        val scroll = ScrollView(this).apply { isFillViewport = true; addView(content) }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        ui.section(content, "O medicamento", "Copie as informações da prescrição ou embalagem.")
        content.addView(ui.card().apply {
            nameField = ui.field(this, "Nome *", state?.getString("name") ?: medicine?.name.orEmpty())
            presentationField = ui.field(this, "Apresentação (ex.: cápsula, solução)", state?.getString("presentation") ?: medicine?.presentation.orEmpty())
            concentrationField = ui.field(this, "Concentração", state?.getString("concentration") ?: medicine?.concentration.orEmpty())
            quantityField = ui.field(this, "Quantidade prescrita por administração *", state?.getString("quantity") ?: plan?.quantity.orEmpty())
            addView(ui.text("Informe exatamente o que foi prescrito. O app não calcula nem altera doses.", 12f, ui.muted))
        })
        ui.section(content, "Quando lembrar", "Avisos locais, usando o horário e o fuso do celular.")
        content.addView(ui.card().apply {
            addView(ui.text("Horários *", 13f, bold = true))
            timeList = ui.column(); addView(timeList); renderTimes()
            addView(ui.button("+  Adicionar horário", false) {
                if (times.size >= 24) { Toast.makeText(this@MedicineEditorActivity, "Limite de 24 horários por esquema.", Toast.LENGTH_SHORT).show(); return@button }
                val now = LocalTime.now()
                TimePickerDialog(this@MedicineEditorActivity, { _, hour, minute ->
                    times.add(LocalTime.of(hour, minute)); renderTimes()
                }, now.hour, now.minute, true).show()
            })
            addView(ui.space(16)); addView(ui.text("Frequência", 13f, bold = true))
            frequency = Spinner(this@MedicineEditorActivity).apply {
                adapter = ArrayAdapter(this@MedicineEditorActivity, android.R.layout.simple_spinner_dropdown_item,
                    listOf("Todos os dias", "Dias da semana", "A cada X dias"))
                minimumHeight = ui.dp(52)
            }
            addView(frequency)
            weekdaysView = GridLayout(this@MedicineEditorActivity).apply { columnCount = 4 }
            listOf("Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom").forEachIndexed { index, label ->
                val mask = 1 shl index
                weekdaysView.addView(CheckBox(this@MedicineEditorActivity).apply {
                    text = label; setTextColor(ui.ink); isChecked = weekdays and mask != 0; minHeight = ui.dp(48)
                    setOnCheckedChangeListener { _, checked -> weekdays = if (checked) weekdays or mask else weekdays and mask.inv() }
                })
            }
            addView(weekdaysView)
            intervalView = ui.column()
            intervalField = ui.field(intervalView, "Intervalo em dias *", state?.getString("interval") ?: plan?.everyDays?.takeIf { it > 1 }?.toString().orEmpty(),
                type = InputType.TYPE_CLASS_NUMBER)
            addView(intervalView)
            val mode = state?.getInt("mode") ?: if ((plan?.everyDays ?: 1) > 1) 2 else if ((plan?.weekdays ?: 127) != 127) 1 else 0
            frequency.setSelection(mode)
            fun showFrequency(position: Int) {
                weekdaysView.visibility = if (position == 1) View.VISIBLE else View.GONE
                intervalView.visibility = if (position == 2) View.VISIBLE else View.GONE
            }
            showFrequency(mode)
            frequency.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) = showFrequency(position)
            }
            addView(ui.space(12))
            addView(ui.text(if (medicine == null) "Início do esquema" else "Esta alteração começa em", 13f, bold = true))
            fromButton = ui.button(from.friendly(), false) { pickDate(from) { from = it; fromButton.text = it.friendly() } }
            addView(fromButton)
            if (medicine != null) addView(ui.text("Escolha hoje ou uma data futura. Quantidade, horários e frequência anteriores ficam preservados no histórico. Cadastre mudanças somente conforme orientação profissional.", 12f, ui.muted))
            val toggle = CheckBox(this@MedicineEditorActivity).apply {
                text = "Definir término (opcional)"; setTextColor(ui.ink); isChecked = until != null; minHeight = ui.dp(48)
            }
            addView(toggle)
            untilButton = ui.button(until?.friendly() ?: "Selecionar término", false) {
                pickDate(until ?: from) { until = it; untilButton.text = it.friendly() }
            }
            addView(untilButton); untilButton.visibility = if (until != null) View.VISIBLE else View.GONE
            toggle.setOnCheckedChangeListener { _, checked ->
                until = if (checked) until ?: from else null
                untilButton.text = until?.friendly() ?: "Selecionar término"
                untilButton.visibility = if (checked) View.VISIBLE else View.GONE
            }
        })
        content.addView(ui.card().apply {
            notesField = ui.field(this, "Observações (opcional)", state?.getString("notes") ?: medicine?.notes.orEmpty(), true)
            addView(ui.text("Um lembrete não confirma uma administração. Você sempre informa o que realmente foi administrado.", 12f, ui.muted))
        })
        if (medicine != null) content.addView(ui.button("Arquivar medicamento", false) {
            if (!busy) AlertDialog.Builder(this@MedicineEditorActivity).setTitle("Arquivar medicamento?")
                .setMessage("Os próximos avisos serão cancelados. Cadastros e registros anteriores continuam disponíveis no calendário e no histórico.")
                .setNegativeButton("Voltar", null).setPositiveButton("Arquivar") { _, _ -> archive() }.show()
        })
        val footer = ui.column(20)
        errorView = ui.text("", 13f, ui.accent).apply { visibility = View.GONE; accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE }
        footer.addView(errorView)
        saveButton = ui.button("Salvar medicamento") { save() }; footer.addView(saveButton)
        root.addView(footer)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom); insets
        }
        setContentView(root); ViewCompat.requestApplyInsets(root)
        built = true; initialForm = state?.getString("initial") ?: formState().toString()
    }
    private fun renderTimes() {
        timeList.removeAllViews()
        if (times.isEmpty()) timeList.addView(ui.text("Nenhum horário cadastrado.", 13f, ui.muted))
        times.toList().chunked(3).forEach { group ->
            timeList.addView(ui.row().apply {
                group.forEach { time ->
                    addView(ui.button("$time  ×", false) { times.remove(time); renderTimes() }.apply {
                        contentDescription = "Remover horário $time"
                    }, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(ui.dp(2), ui.dp(4), ui.dp(2), 0) })
                }
            })
        }
    }
    private fun formState() = Bundle().apply {
        putString("name", nameField.text.toString()); putString("presentation", presentationField.text.toString())
        putString("concentration", concentrationField.text.toString()); putString("quantity", quantityField.text.toString())
        putString("notes", notesField.text.toString()); putString("interval", intervalField.text.toString())
        putInt("mode", frequency.selectedItemPosition); putInt("weekdays", weekdays)
        putString("from", from.toString()); putString("until", until?.toString())
        putStringArrayList("times", ArrayList(times.map { it.toString() }))
    }
    override fun onSaveInstanceState(outState: Bundle) {
        if (built) { outState.putAll(formState()); outState.putString("initial", initialForm) }
        super.onSaveInstanceState(outState)
    }
    private fun save() {
        if (busy) return
        val plan: Plan
        val med: Medicine
        try {
            val mode = frequency.selectedItemPosition
            med = Medicine(medicine?.id ?: 0, nameField.text.toString(), presentationField.text.toString(), concentrationField.text.toString(), notesField.text.toString())
            plan = Plan(medicineId = med.id, quantity = quantityField.text.toString(), times = times.toList(), from = from, until = until,
                everyDays = if (mode == 2) intervalField.text.toString().toIntOrNull() ?: 0 else 1,
                weekdays = if (mode == 1) weekdays else 127)
            require(med.name.isNotBlank()) { "Informe o nome do medicamento." }; plan.validate()
        } catch (error: IllegalArgumentException) { showError(error.message); return }
        busy = true; saveButton.isEnabled = false
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) { CareStore.get(this@MedicineEditorActivity).saveMedicine(med, plan) }
                val alarms = runCatching { CareAlarms.reschedule(this@MedicineEditorActivity) }
                Toast.makeText(this@MedicineEditorActivity, if (alarms.isSuccess) "Medicamento salvo." else "Cadastro salvo. Revise os avisos em Ajustes.", Toast.LENGTH_LONG).show()
                finish()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { showError(if (error is IllegalArgumentException) error.message else "Não foi possível salvar. Tente novamente."); busy = false; saveButton.isEnabled = true }
        }
    }
    private fun archive() {
        if (busy) return
        busy = true
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) { CareStore.get(this@MedicineEditorActivity).archive(medicine!!.id) }
                runCatching { CareAlarms.reschedule(this@MedicineEditorActivity) }
                finish()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { busy = false; showError("Não foi possível arquivar. Tente novamente.") }
        }
    }
    private fun showError(message: String?) { errorView.text = message ?: "Confira os campos."; errorView.visibility = View.VISIBLE }
}
