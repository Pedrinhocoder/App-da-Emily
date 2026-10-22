package br.com.pedrinho.amorcarrega

import android.Manifest
import android.app.*
import android.content.*
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.view.*
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import br.com.pedrinho.amorcarrega.care.*
import kotlinx.coroutines.*
import java.time.*
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    private lateinit var ui: CareUi
    private lateinit var content: LinearLayout
    private lateinit var navigation: LinearLayout
    private lateinit var scroll: ScrollView
    private var snapshot = CareSnapshot()
    private var selectedTab = 0
    private var selectedDate = LocalDate.now()
    private var month = YearMonth.now()
    private var battery = 100
    private var charging = false
    private var loading = true
    private var loadError = false
    private var generation = 0
    private var pendingKey: String? = null
    private var confirmationState: Bundle? = null
    private var saveConfirmation: (() -> Bundle)? = null
    private var registered = false
    private var batteryServiceFailed = false
    private val events = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_BATTERY_CHANGED) {
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0)
                battery = if (scale > 0) (100 * level / scale).coerceIn(0, 100) else 0
                charging = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
                if (selectedTab == 0 && !loading) render()
            } else if (intent.action == Intent.ACTION_TIME_TICK) render()
            else reload()
        }
    }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state); enableEdgeToEdge(); ui = CareUi(this)
        selectedTab = state?.getInt("tab") ?: 0
        selectedDate = state?.getString("selected_date")?.let(LocalDate::parse) ?: LocalDate.now()
        month = state?.getString("month")?.let(YearMonth::parse) ?: YearMonth.from(selectedDate)
        confirmationState = state?.getBundle("confirmation")
        pendingKey = confirmationState?.getString("key") ?: intent.getStringExtra(CareAlarms.KEY)
        buildShell()
        try { ContextCompat.startForegroundService(this, Intent(this, BatteryMonitorService::class.java)) }
        catch (_: RuntimeException) { batteryServiceFailed = true }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent); setIntent(intent)
        pendingKey = intent.getStringExtra(CareAlarms.KEY); reload()
    }
    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(this, events, IntentFilter(Intent.ACTION_BATTERY_CHANGED).apply {
            addAction(Intent.ACTION_DATE_CHANGED); addAction(Intent.ACTION_TIME_CHANGED); addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_TIME_TICK)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
        registered = true
    }
    override fun onStop() { if (registered) { unregisterReceiver(events); registered = false }; super.onStop() }
    override fun onResume() { super.onResume(); reload(); CareAlarms.enqueue(this) }
    override fun onSaveInstanceState(state: Bundle) {
        state.putInt("tab", selectedTab); state.putString("selected_date", selectedDate.toString()); state.putString("month", month.toString())
        saveConfirmation?.invoke()?.let { state.putBundle("confirmation", it) }
        super.onSaveInstanceState(state)
    }
    private fun buildShell() {
        val root = ui.column().apply { setBackgroundColor(ui.color(R.color.care_background)) }
        val header = ui.row().apply { setPadding(ui.dp(20), ui.dp(12), ui.dp(20), ui.dp(10)) }
        header.addView(ui.text("♥", 23f, ui.accent, true).apply {
            gravity = Gravity.CENTER; background = ui.shape(ui.soft, 14)
        }, LinearLayout.LayoutParams(ui.dp(44), ui.dp(44)))
        header.addView(ui.column().apply {
            setPadding(ui.dp(12), 0, 0, 0)
            addView(ui.text("Amor, Cuida", 22f, bold = true)); addView(ui.text("Cuidando de quem eu amo, todos os dias.", 10f, ui.muted))
        }, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(header)
        content = ui.column(20)
        scroll = ScrollView(this).apply { isFillViewport = true; isVerticalScrollBarEnabled = false; addView(content) }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        navigation = ui.row().apply {
            setPadding(ui.dp(5), ui.dp(6), ui.dp(5), ui.dp(6))
            setBackgroundColor(ui.color(R.color.care_surface))
        }
        root.addView(navigation)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom); insets
        }
        setContentView(root); ViewCompat.requestApplyInsets(root); render()
    }
    private fun reload() {
        val ticket = ++generation
        lifecycleScope.launch {
            try {
                val data = withContext(Dispatchers.IO) { CareStore.get(this@MainActivity).snapshot() }
                if (ticket != generation) return@launch
                snapshot = data; loading = false; loadError = false; render()
                val key = pendingKey
                pendingKey = null
                intent.removeExtra(CareAlarms.KEY)
                if (key != null && saveConfirmation == null) {
                    val existing = snapshot.records.find { it.occurrenceKey == key }
                    val occurrence = existing?.let(::recordOccurrence) ?: CareAlarms.occurrence(snapshot, key)
                    if (occurrence != null) showConfirmation(occurrence, existing)
                    else Toast.makeText(this@MainActivity, "Esse lembrete mudou. Confira o cadastro atual.", Toast.LENGTH_LONG).show()
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (ticket == generation) { loading = false; loadError = true; render() } }
        }
    }
    private fun tab(index: Int) { selectedTab = index; render(); scroll.scrollTo(0, 0) }
    private fun editor(id: Long = 0) = startActivity(Intent(this, MedicineEditorActivity::class.java).putExtra("medicine_id", id))
    private fun batteryPage() = startActivity(Intent(this, BatteryActivity::class.java))
    private fun render() {
        if (!::content.isInitialized) return
        content.removeAllViews(); renderNavigation()
        if (loading) { content.addView(ui.text("Preparando seu cuidado…", 18f)); return }
        if (loadError) { ui.empty(content, "Não foi possível abrir seus dados", "Seus registros não foram apagados. Tente novamente.", "Tentar novamente") { reload() }; return }
        when (selectedTab) { 0 -> home(); 1 -> medicines(); 2 -> calendar(); 3 -> history(); else -> settings() }
    }
    private fun renderNavigation() {
        navigation.removeAllViews()
        val icons = listOf(R.drawable.ic_care_home, R.drawable.ic_care_medicine, R.drawable.ic_care_calendar, R.drawable.ic_care_history, R.drawable.ic_care_settings)
        listOf("Início", "Remédios", "Calendário", "Histórico", "Ajustes").forEachIndexed { index, label ->
            navigation.addView(ui.column(5).apply {
                gravity = Gravity.CENTER; minimumHeight = ui.dp(60); isClickable = true; isFocusable = true
                isSelected = index == selectedTab; contentDescription = label
                if (isSelected) background = ui.shape(ui.soft, 16)
                addView(ImageView(this@MainActivity).apply {
                    setImageResource(icons[index]); imageTintList = ColorStateList.valueOf(if (index == selectedTab) ui.accent else ui.muted)
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }, LinearLayout.LayoutParams(ui.dp(23), ui.dp(23)))
                addView(ui.text(label, 10f, if (isSelected) ui.accent else ui.muted, isSelected).apply {
                    gravity = Gravity.CENTER; importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                })
                setOnClickListener { tab(index) }
            }, LinearLayout.LayoutParams(0, -2, 1f))
        }
    }
    private fun permissionNotice() {
        val notifications = NotificationManagerCompat.from(this).areNotificationsEnabled()
        val exact = CareAlarms.exactAllowed(this)
        val channelMuted = getSystemService(NotificationManager::class.java).getNotificationChannel(CareAlarms.channelId(this))?.importance == NotificationManager.IMPORTANCE_NONE
        if (snapshot.medicines.none { it.archivedAt == null } || (notifications && exact && !channelMuted)) return
        content.addView(ui.card().apply {
            setBackgroundColor(ui.soft)
            addView(ui.text("Vamos preparar os avisos?", 16f, bold = true))
            addView(ui.text(if (!notifications) "Permita notificações para receber os lembretes cadastrados." else if (channelMuted) "O canal de medicamentos foi silenciado no Android. Revise-o em Ajustes." else "Sem a permissão de alarmes exatos, o Android pode atrasar os avisos.", 13f, ui.muted))
            addView(ui.button("Revisar permissões", false) { tab(4) })
        })
    }
    private fun home() {
        val today = LocalDate.now(); val now = Instant.now(); val zone = ZoneId.systemDefault()
        val doses = snapshot.onDate(today); val confirmed = snapshot.confirmedKeys
        val upcoming = doses.filter { Schedules.status(it, confirmed, now, zone) == DoseStatus.PENDING }
        val unconfirmed = doses.count { Schedules.status(it, confirmed, now, zone) == DoseStatus.UNCONFIRMED }
        content.addView(ui.badge("SEU CUIDADO, AQUI · OFFLINE")); content.addView(ui.space(14))
        content.addView(ui.text("Um dia mais leve.\nUm cuidado de cada vez.", 29f, bold = true))
        content.addView(ui.space(8)); content.addView(ui.text(today.format(DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", BRAZIL)), 14f, ui.muted))
        content.addView(ui.space(20))
        val next = snapshot.medicines.mapNotNull { Schedules.next(it, snapshot.plans, now, zone, confirmed) }.minByOrNull { it.instant(zone) }
        content.addView(ui.card().apply {
            background = ui.shape(ui.action, 24)
            addView(ui.text("PRÓXIMO LEMBRETE", 11f, Color.WHITE, true)); addView(ui.space(12))
            if (next != null) {
                addView(ui.text(next.time.toString(), 40f, Color.WHITE, true))
                addView(ui.text(next.medicine.name, 22f, Color.WHITE, true))
                addView(ui.text("${next.plan.quantity} · ${if (next.date == today) "Hoje" else next.date.friendly()}", 14f, Color.WHITE))
            } else {
                addView(ui.text("Tudo no seu tempo ♥", 24f, Color.WHITE, true))
                addView(ui.text("Cadastre seus medicamentos conforme a prescrição para organizar os próximos avisos.", 14f, Color.WHITE))
                addView(ui.button("+  Cadastrar medicamento", false) { editor() })
            }
        })
        content.addView(ui.row().apply {
            listOf(upcoming.size.toString() to "Próximos hoje", snapshot.recordedOn(today).size.toString() to "Registros hoje", unconfirmed.toString() to "Sem confirmação").forEach { (count, label) ->
                addView(ui.column(8).apply {
                    addView(ui.text(count, 25f, bold = true)); addView(ui.text(label, 10f, ui.muted))
                }, LinearLayout.LayoutParams(0, -2, 1f))
            }
        })
        content.addView(ui.space(14)); permissionNotice()
        content.addView(ui.card().apply {
            addView(ui.badge(if (charging) "CARREGANDO" else "BATERIA", ui.good, ui.color(R.color.care_good_bg)))
            addView(ui.text("$battery%  ·  Seu celular também merece cuidado", 17f, bold = true))
            addView(ui.text(if (batteryServiceFailed) "Abra o módulo de bateria para revisar o monitoramento." else "Os alertas de bateria continuam disponíveis, com seus níveis e testes.", 13f, ui.muted))
            addView(ui.button("Abrir bateria e testes  ›", false) { batteryPage() })
        })
        ui.section(content, "Sua rotina de hoje", "Confirmar é um gesto seu. O aviso não faz isso sozinho.")
        if (doses.isEmpty()) ui.empty(content, "Nenhum lembrete para hoje", "Seus horários aparecerão aqui depois do cadastro.", "+  Novo medicamento") { editor() }
        else doses.forEach { doseCard(content, it) }
    }
    private fun medicines() {
        ui.section(content, "Seus medicamentos", "Prescrição cadastrada, rotina organizada.")
        content.addView(ui.button("+  Novo medicamento") { editor() }); content.addView(ui.space(16)); permissionNotice()
        val active = snapshot.medicines.filter { it.archivedAt == null }
        if (active.isEmpty()) ui.empty(content, "Vamos começar com calma?", "Cadastre um medicamento por vez, copiando os dados da prescrição.")
        active.forEach { medicine ->
            content.addView(ui.card().apply {
                addView(ui.badge("MEDICAMENTO")); addView(ui.space(8)); addView(ui.text(medicine.name, 23f, bold = true))
                addView(ui.text(listOf(medicine.presentation, medicine.concentration).filter { it.isNotBlank() }.joinToString(" · "), 14f, ui.muted))
                val plans = snapshot.plans.filter { it.medicineId == medicine.id && !it.superseded && (it.until == null || !it.until.isBefore(LocalDate.now())) }
                if (plans.isEmpty()) addView(ui.text("Esquema encerrado. Histórico preservado.", 13f, ui.muted))
                plans.forEach { plan ->
                    addView(ui.space(10)); addView(ui.text(plan.quantity, 17f, bold = true))
                    addView(ui.text(plan.times.joinToString(" · "), 16f, ui.accent, true))
                    addView(ui.text(frequencyLabel(plan), 12f, ui.muted))
                    addView(ui.text("Desde ${plan.from.friendly()}${plan.until?.let { " até ${it.friendly()}" }.orEmpty()}", 12f, ui.muted))
                    if (plan.from.isAfter(LocalDate.now())) addView(ui.badge("Alteração futura cadastrada"))
                }
                if (medicine.notes.isNotBlank()) { addView(ui.space(10)); addView(ui.text(medicine.notes, 13f, ui.muted)) }
                addView(ui.button("Editar cadastro e horários", false) { editor(medicine.id) })
            })
        }
        val archived = snapshot.medicines.filter { it.archivedAt != null }
        if (archived.isNotEmpty()) {
            ui.section(content, "Arquivados", "Sem novos avisos. Os registros continuam no histórico.")
            archived.forEach { medicine -> content.addView(ui.card().apply { addView(ui.text(medicine.name, 17f, bold = true)); addView(ui.text("Arquivado · histórico preservado", 12f, ui.muted)) }) }
        }
    }
    private fun frequencyLabel(plan: Plan): String = if (plan.everyDays > 1) "A cada ${plan.everyDays} dias" else if (plan.weekdays == 127) "Todos os dias" else
        listOf("seg", "ter", "qua", "qui", "sex", "sáb", "dom").filterIndexed { i, _ -> plan.weekdays and (1 shl i) != 0 }.joinToString(" · ")

    private fun doseCard(parent: LinearLayout, dose: Occurrence) {
        val record = snapshot.records.find { it.occurrenceKey == dose.key }
        val status = Schedules.status(dose, snapshot.confirmedKeys, Instant.now(), ZoneId.systemDefault())
        parent.addView(ui.card().apply {
            val tone = if (status == DoseStatus.CONFIRMED) ui.good else if (status == DoseStatus.UNCONFIRMED) ui.accent else ui.muted
            addView(ui.row().apply {
                addView(ui.text(dose.time.toString(), 22f, bold = true), LinearLayout.LayoutParams(0, -2, 1f))
                addView(ui.badge(status.label, tone, if (status == DoseStatus.CONFIRMED) ui.color(R.color.care_good_bg) else ui.soft))
            })
            addView(ui.space(7)); addView(ui.text(dose.medicine.name, 19f, bold = true))
            addView(ui.text("${dose.plan.quantity} · ${dose.medicine.concentration}", 14f, ui.muted))
            if (record != null) addView(ui.text("Registro: ${LocalDate.parse(record.actualDate).friendly()} às ${record.actualTime} · ${record.actualQuantity}", 12f, ui.good))
            else if (status == DoseStatus.UNCONFIRMED) addView(ui.text("Sem registro não significa que não foi administrado.", 12f, ui.muted))
            addView(ui.button(if (record == null) "Já administrei" else "Consultar / corrigir", record == null) { showConfirmation(dose, record) })
        })
    }
    private fun calendar() {
        ui.section(content, "Calendário do cuidado", "As marcas verdes seguem a data real da administração.")
        val heading = ui.row()
        heading.addView(ui.button("‹", false) { month = month.minusMonths(1); selectedDate = month.atDay(1); render() }, LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)))
        heading.addView(ui.text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy", BRAZIL)).replaceFirstChar { it.uppercaseChar() }, 19f, bold = true).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(0, -2, 1f))
        heading.addView(ui.button("›", false) { month = month.plusMonths(1); selectedDate = month.atDay(1); render() }, LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)))
        content.addView(heading)
        content.addView(ui.button("Hoje", false) { selectedDate = LocalDate.now(); month = YearMonth.from(selectedDate); render() })
        content.addView(ui.space(12))
        val grid = GridLayout(this).apply { columnCount = 7; rowCount = 7; layoutParams = LinearLayout.LayoutParams(-1, -2) }
        fun addCell(view: View, row: Int, col: Int, height: Int) {
            grid.addView(view, GridLayout.LayoutParams(GridLayout.spec(row), GridLayout.spec(col, 1f)).apply { width = 0; this.height = ui.dp(height); setMargins(ui.dp(1), ui.dp(2), ui.dp(1), ui.dp(2)) })
        }
        listOf("S", "T", "Q", "Q", "S", "S", "D").forEachIndexed { col, day -> addCell(ui.text(day, 12f, ui.muted, true).apply { gravity = Gravity.CENTER }, 0, col, 28) }
        val offset = month.atDay(1).dayOfWeek.value - 1
        (1..month.lengthOfMonth()).forEach { day ->
            val date = month.atDay(day); val selected = date == selectedDate
            val records = snapshot.recordedOn(date).size
            val missing = snapshot.onDate(date).count { Schedules.status(it, snapshot.confirmedKeys, Instant.now(), ZoneId.systemDefault()) == DoseStatus.UNCONFIRMED }
            val index = offset + day - 1
            val cell = ui.column().apply {
                gravity = Gravity.CENTER; isClickable = true; isFocusable = true; isSelected = selected
                background = ui.shape(if (selected) ui.action else Color.TRANSPARENT, 14, if (!selected && date == LocalDate.now()) ui.accent else null)
                contentDescription = "${date.friendly()}, $records administrações registradas, $missing lembretes sem confirmação"
                addView(ui.text(day.toString(), 15f, if (selected) Color.WHITE else ui.ink, selected).apply { gravity = Gravity.CENTER; importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO })
                addView(ui.row().apply {
                    gravity = Gravity.CENTER
                    if (records > 0) addView(View(this@MainActivity).apply { background = ui.shape(if (selected) Color.WHITE else ui.good, 10) }, LinearLayout.LayoutParams(ui.dp(6), ui.dp(6)).apply { marginEnd = ui.dp(3) })
                    if (missing > 0) addView(View(this@MainActivity).apply { background = ui.shape(if (selected) Color.parseColor("#F1B6CE") else ui.accent, 10) }, LinearLayout.LayoutParams(ui.dp(6), ui.dp(6)))
                    minimumHeight = ui.dp(10)
                })
                setOnClickListener { selectedDate = date; render() }
            }
            addCell(cell, index / 7 + 1, index % 7, 52)
        }
        content.addView(grid); content.addView(ui.space(12))
        content.addView(ui.row().apply {
            addView(ui.badge("● Registrado", ui.good, ui.color(R.color.care_good_bg)))
            addView(ui.badge("● Sem confirmação"))
        })
        content.addView(ui.text("Sem confirmação não é prova de uma dose esquecida.", 12f, ui.muted))
        ui.section(content, selectedDate.friendly(), "Administrações realmente registradas nesta data")
        val records = snapshot.recordedOn(selectedDate)
        if (records.isEmpty()) ui.empty(content, "Nenhuma administração registrada", "Confirme um lembrete abaixo e informe a data e a hora reais para marcar o calendário.")
        else records.forEach { recordCard(content, it) }
        val planned = snapshot.onDate(selectedDate)
        if (planned.isNotEmpty()) { ui.section(content, "Lembretes desta data"); planned.forEach { doseCard(content, it) } }
    }
    private fun history() {
        ui.section(content, "Cada cuidado fica aqui", "Registros e correções salvos somente neste aparelho.")
        content.addView(ui.button("Escolher data no calendário", false) { tab(2) })
        content.addView(ui.space(16))
        if (snapshot.records.isEmpty()) ui.empty(content, "Sua história começa com uma confirmação", "Quando você informar uma administração, o medicamento, a quantidade e a data real aparecerão aqui.")
        else {
            content.addView(ui.text("ÚLTIMOS ${minOf(100, snapshot.records.size)} REGISTROS", 11f, ui.muted, true)); content.addView(ui.space(12))
            snapshot.records.take(100).forEach { recordCard(content, it) }
            if (snapshot.records.size > 100) content.addView(ui.text("Consulte os registros anteriores pelo calendário.", 13f, ui.muted))
        }
        val missing = (0L..6L).flatMap { snapshot.onDate(LocalDate.now().minusDays(it)) }
            .filter { Schedules.status(it, snapshot.confirmedKeys, Instant.now(), ZoneId.systemDefault()) == DoseStatus.UNCONFIRMED }
        if (missing.isNotEmpty()) {
            ui.section(content, "Sem confirmação", "Últimos 7 dias. Ausência de registro não prova que não foi administrado.")
            missing.take(20).forEach { content.addView(ui.text(it.date.friendly(), 12f, ui.muted)); doseCard(content, it) }
            if (missing.size > 20) content.addView(ui.text("Veja as demais ocorrências no calendário.", 13f, ui.muted))
        }
    }
    private fun recordCard(parent: LinearLayout, record: AdministrationRow) {
        parent.addView(ui.card().apply {
            addView(ui.badge("ADMINISTRAÇÃO REGISTRADA", ui.good, ui.color(R.color.care_good_bg)))
            addView(ui.space(8)); addView(ui.text(record.medicineName, 20f, bold = true))
            addView(ui.text("${record.actualQuantity} · ${LocalDate.parse(record.actualDate).friendly()} às ${record.actualTime}", 15f, ui.good, true))
            addView(ui.text("${record.presentation} ${record.concentration}\nFuso do registro: ${record.zone}", 12f, ui.muted))
            val changes = snapshot.corrections.filter { it.administrationId == record.id }
            addView(ui.button("Corrigir registro", false) { showConfirmation(recordOccurrence(record), record) })
            if (changes.isNotEmpty()) addView(ui.button("Ver ${changes.size} correção(ões)", false) {
                AlertDialog.Builder(this@MainActivity).setTitle("Alterações preservadas")
                    .setMessage(changes.joinToString("\n\n") { "${it.oldQuantity} · ${LocalDate.parse(it.oldDate).friendly()} ${it.oldTime}\n→ ${it.newQuantity} · ${LocalDate.parse(it.newDate).friendly()} ${it.newTime}\nMotivo: ${it.reason}\nAlterado em: ${Instant.ofEpochMilli(it.changedAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", BRAZIL))}" })
                    .setPositiveButton("Fechar", null).show()
            })
        })
    }
    private fun recordOccurrence(record: AdministrationRow) = Occurrence(
        Medicine(record.medicineId, record.medicineName, record.presentation, record.concentration),
        Plan(record.planId, record.medicineId, record.prescribedQuantity, listOf(LocalTime.parse(record.plannedTime)), LocalDate.parse(record.plannedDate)),
        LocalDate.parse(record.plannedDate), LocalTime.parse(record.plannedTime))

    private fun settings() {
        ui.section(content, "Do seu jeito", "Pequenos ajustes para uma rotina mais tranquila.")
        val prefs = CareAlarms.preferences(this)
        content.addView(ui.card().apply {
            addView(ui.text("Avisos de medicamentos", 19f, bold = true))
            listOf("sound" to "Som breve", "vibration" to "Vibração").forEach { (key, label) ->
                addView(Switch(this@MainActivity).apply {
                    text = label; setTextColor(ui.ink); isChecked = prefs.getBoolean(key, true); minHeight = ui.dp(56)
                    setOnCheckedChangeListener { _, value -> prefs.edit().putBoolean(key, value).apply() }
                })
            }
            addView(ui.text("O som de medicamento é curto. Os alarmes de bateria têm seus próprios controles.", 12f, ui.muted))
            addView(ui.button("Canal de medicamentos no Android", false) {
                val channelId = CareAlarms.ensureChannel(this@MainActivity)
                openSettings(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, packageName).putExtra(Settings.EXTRA_CHANNEL_ID, channelId))
            })
        })
        val notifications = NotificationManagerCompat.from(this).areNotificationsEnabled()
        val exact = CareAlarms.exactAllowed(this)
        content.addView(ui.card().apply {
            addView(ui.text("Permissões e segundo plano", 19f, bold = true))
            addView(ui.button(if (notifications) "✓ Notificações permitidas" else "Permitir notificações", false) {
                if (Build.VERSION.SDK_INT >= 33 && !notifications) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 200)
                else openSettings(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
            })
            addView(ui.button(if (exact) "✓ Alarmes exatos disponíveis" else "Permitir alarmes exatos", false) {
                if (Build.VERSION.SDK_INT >= 31) openSettings(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).setData(Uri.parse("package:$packageName")))
                else Toast.makeText(this@MainActivity, "Disponível nesta versão do Android.", Toast.LENGTH_SHORT).show()
            })
            if (!exact) addView(ui.text("O Android usará avisos aproximados até você permitir os alarmes exatos.", 12f, ui.accent))
            addView(ui.button("Revisar uso de bateria / segundo plano", false) {
                openSettings(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(Uri.parse("package:$packageName")))
            })
            addView(ui.text("No Motorola, confira se o uso em segundo plano está permitido e se o app não está restrito. Não Perturbe, canais silenciados e políticas do Android podem limitar os avisos.", 12f, ui.muted))
            addView(ui.button("Reprogramar próximos avisos", false) {
                lifecycleScope.launch {
                    val result = runCatching { CareAlarms.reschedule(this@MainActivity) }
                    Toast.makeText(this@MainActivity, if (result.isSuccess) "Próximos avisos reprogramados." else "Não foi possível reprogramar. Tente novamente.", Toast.LENGTH_LONG).show()
                }
            })
        })
        content.addView(ui.card().apply {
            addView(ui.text("Bateria", 19f, bold = true)); addView(ui.text("Seus níveis, sons, testes e monitoramento continuam aqui.", 13f, ui.muted))
            addView(ui.button("Abrir configurações de bateria", false) { batteryPage() })
        })
        content.addView(ui.card().apply {
            addView(ui.text("Seu cuidado é local", 19f, bold = true))
            addView(ui.text("Sem conta, sem calendário externo e sem conexão para funcionar. Os dados ficam neste aparelho. Desinstalar ou limpar os dados apaga os registros locais.\n\nO app organiza o que foi prescrito. Não calcula doses, não orienta compensações e não substitui o profissional de saúde.", 13f, ui.muted))
            addView(ui.space(12)); addView(ui.badge("AMOR, CUIDA · 2.0.0"))
        })
    }
    private fun openSettings(intent: Intent) {
        try { startActivity(intent) } catch (_: ActivityNotFoundException) { Toast.makeText(this, "Esta opção não está disponível neste aparelho.", Toast.LENGTH_LONG).show() }
    }
    private fun showConfirmation(dose: Occurrence, record: AdministrationRow?) {
        if (saveConfirmation != null) return
        val restored = confirmationState?.takeIf { it.getString("key") == dose.key }; confirmationState = null
        var date = restored?.getString("date")?.let(LocalDate::parse) ?: record?.actualDate?.let(LocalDate::parse)
            ?: minOf(dose.date, LocalDate.now())
        var time = restored?.getString("time")?.let(LocalTime::parse) ?: record?.actualTime?.let(LocalTime::parse) ?: LocalTime.now().withSecond(0).withNano(0)
        val body = ui.column(20)
        body.addView(ui.text(dose.medicine.name, 21f, bold = true))
        body.addView(ui.text("Prescrição cadastrada: ${dose.plan.quantity}\nLembrete: ${dose.date.friendly()} às ${dose.time}", 13f, ui.muted))
        body.addView(ui.space(14))
        val amount = ui.field(body, "Quantidade realmente administrada *", restored?.getString("quantity") ?: record?.actualQuantity ?: dose.plan.quantity)
        lateinit var dateButton: Button
        dateButton = ui.button("Data real: ${date.friendly()}", false) {
            DatePickerDialog(this, { _, year, month, day -> date = LocalDate.of(year, month + 1, day); dateButton.text = "Data real: ${date.friendly()}" }, date.year, date.monthValue - 1, date.dayOfMonth).show()
        }
        body.addView(dateButton)
        lateinit var timeButton: Button
        timeButton = ui.button("Hora real: $time", false) {
            TimePickerDialog(this, { _, hour, minute -> time = LocalTime.of(hour, minute); timeButton.text = "Hora real: $time" }, time.hour, time.minute, true).show()
        }
        body.addView(timeButton)
        body.addView(ui.text("Fuso: ${record?.zone ?: ZoneId.systemDefault().id}", 11f, ui.muted))
        val reason = if (record != null) ui.field(body, "Motivo da correção *", restored?.getString("reason").orEmpty(), true) else null
        body.addView(ui.space(10)); body.addView(ui.text("Confirme somente o que realmente foi administrado. O aviso sozinho não cria um registro.", 12f, ui.muted))
        val error = ui.text("", 13f, ui.accent).apply { accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE }; body.addView(error)
        val dialog = AlertDialog.Builder(this).setTitle(if (record == null) "Registrar administração" else "Corrigir administração")
            .setView(ScrollView(this).apply { addView(body) }).setNegativeButton("Voltar", null)
            .setPositiveButton(if (record == null) "Confirmar registro" else "Salvar correção", null).create()
        saveConfirmation = { Bundle().apply {
            putString("key", dose.key); putString("quantity", amount.text.toString()); putString("date", date.toString())
            putString("time", time.toString()); putString("reason", reason?.text?.toString())
        } }
        dialog.setOnDismissListener { saveConfirmation = null }
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val save = dialog.getButton(AlertDialog.BUTTON_POSITIVE); save.isEnabled = false
                dialog.setCancelable(false); dialog.getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled = false
                val actualAmount = amount.text.toString()
                val actualDate = date
                val actualTime = time
                val correctionReason = reason?.text?.toString().orEmpty()
                lifecycleScope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            CareStore.get(this@MainActivity).confirm(dose, actualAmount, actualDate, actualTime, record, correctionReason)
                        }
                        CareAlarms.cancelNotification(this@MainActivity, dose.key)
                        CareAlarms.enqueue(this@MainActivity)
                        dialog.dismiss(); reload()
                        Toast.makeText(this@MainActivity, "Registro salvo no histórico e no calendário.", Toast.LENGTH_SHORT).show()
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (failure: Exception) {
                        error.text = if (failure is IllegalArgumentException) failure.message ?: "Confira os campos." else "Não foi possível salvar. Tente novamente."
                        save.isEnabled = true; dialog.setCancelable(true); dialog.getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled = true
                    }
                }
            }
        }
        dialog.show()
    }
}
