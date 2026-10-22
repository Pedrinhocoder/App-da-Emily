package br.com.pedrinho.amorcarrega.care

import android.content.Context
import androidx.room.*
import java.time.*

@Entity(tableName = "medicines")
data class MedicineRow(@PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String, val presentation: String, val concentration: String, val notes: String,
    val archivedAt: Long? = null) {
    fun domain() = Medicine(id, name, presentation, concentration, notes, archivedAt)
}
@Entity(tableName = "plans", foreignKeys = [ForeignKey(entity = MedicineRow::class,
    parentColumns = ["id"], childColumns = ["medicineId"])], indices = [Index("medicineId")])
data class PlanRow(@PrimaryKey(autoGenerate = true) val id: Long = 0, val medicineId: Long,
    val quantity: String, val times: String, val fromDate: String, val untilDate: String?,
    val everyDays: Int, val weekdays: Int, val superseded: Boolean = false) {
    fun domain() = Plan(id, medicineId, quantity, times.split(',').map(LocalTime::parse),
        LocalDate.parse(fromDate), untilDate?.let(LocalDate::parse), everyDays, weekdays, superseded)
}
@Entity(tableName = "administrations", foreignKeys = [
    ForeignKey(entity = MedicineRow::class, parentColumns = ["id"], childColumns = ["medicineId"]),
    ForeignKey(entity = PlanRow::class, parentColumns = ["id"], childColumns = ["planId"])],
    indices = [Index(value = ["occurrenceKey"], unique = true), Index("medicineId"), Index("planId")])
data class AdministrationRow(@PrimaryKey(autoGenerate = true) val id: Long = 0,
    val occurrenceKey: String, val medicineId: Long, val planId: Long,
    val medicineName: String, val presentation: String, val concentration: String,
    val prescribedQuantity: String, val plannedDate: String, val plannedTime: String,
    val actualQuantity: String, val administeredAt: Long, val actualDate: String,
    val actualTime: String, val zone: String, val recordedAt: Long, val updatedAt: Long)
@Entity(tableName = "corrections", foreignKeys = [ForeignKey(entity = AdministrationRow::class,
    parentColumns = ["id"], childColumns = ["administrationId"])], indices = [Index("administrationId")])
data class CorrectionRow(@PrimaryKey(autoGenerate = true) val id: Long = 0,
    val administrationId: Long, val oldQuantity: String, val oldDate: String, val oldTime: String,
    val newQuantity: String, val newDate: String, val newTime: String, val reason: String, val changedAt: Long)

@Dao
interface CareDao {
    @Query("SELECT * FROM medicines ORDER BY name COLLATE NOCASE") suspend fun medicines(): List<MedicineRow>
    @Query("SELECT * FROM plans ORDER BY fromDate, id") suspend fun plans(): List<PlanRow>
    @Query("SELECT * FROM administrations ORDER BY administeredAt DESC") suspend fun administrations(): List<AdministrationRow>
    @Query("SELECT * FROM corrections ORDER BY changedAt DESC") suspend fun corrections(): List<CorrectionRow>
    @Query("SELECT * FROM administrations WHERE occurrenceKey = :key") suspend fun administration(key: String): AdministrationRow?
    @Insert suspend fun insertMedicine(row: MedicineRow): Long
    @Update suspend fun updateMedicine(row: MedicineRow)
    @Insert suspend fun insertPlan(row: PlanRow): Long
    @Query("UPDATE plans SET untilDate = :until WHERE medicineId = :medicine AND fromDate < :from AND (untilDate IS NULL OR untilDate >= :from)")
    suspend fun closeEarlierPlans(medicine: Long, from: String, until: String)
    @Query("UPDATE plans SET superseded = 1 WHERE medicineId = :medicine AND fromDate >= :from")
    suspend fun supersedeFuturePlans(medicine: Long, from: String)
    @Insert suspend fun insertAdministration(row: AdministrationRow): Long
    @Update suspend fun updateAdministration(row: AdministrationRow)
    @Insert suspend fun insertCorrection(row: CorrectionRow)
}

@Database(entities = [MedicineRow::class, PlanRow::class, AdministrationRow::class, CorrectionRow::class],
    version = 1, exportSchema = true)
abstract class CareDatabase : RoomDatabase() {
    abstract fun dao(): CareDao
    companion object {
        @Volatile private var instance: CareDatabase? = null
        fun get(context: Context): CareDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, CareDatabase::class.java,
                "amor_cuida.db").build().also { instance = it }
        }
    }
}

data class CareSnapshot(val medicines: List<Medicine> = emptyList(), val plans: List<Plan> = emptyList(),
    val records: List<AdministrationRow> = emptyList(), val corrections: List<CorrectionRow> = emptyList()) {
    val confirmedKeys get() = records.map { it.occurrenceKey }.toSet()
    fun onDate(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()) = medicines
        .flatMap { Schedules.onDate(it, plans, date, zone) }.sortedBy { it.time }
    // Calendar marks the date actually entered by the user, never the scheduled date.
    fun recordedOn(date: LocalDate) = records.filter { it.actualDate == date.toString() }
}

class CareStore(private val db: CareDatabase) {
    private val dao get() = db.dao()
    suspend fun snapshot(): CareSnapshot = db.withTransaction {
        CareSnapshot(dao.medicines().map { it.domain() }, dao.plans().map { it.domain() },
            dao.administrations(), dao.corrections())
    }
    suspend fun saveMedicine(medicine: Medicine, plan: Plan, today: LocalDate = LocalDate.now()): Long {
        require(medicine.name.isNotBlank() && medicine.name.length <= 100) { "Informe o nome do medicamento (até 100 caracteres)." }
        require(medicine.presentation.length <= 120 && medicine.concentration.length <= 120 && medicine.notes.length <= 1000) { "Revise o tamanho dos campos." }
        plan.validate()
        return db.withTransaction {
            val old = dao.medicines().find { it.id == medicine.id }
            require(medicine.id == 0L || old != null) { "Medicamento não encontrado." }
            require(old?.archivedAt == null) { "Este medicamento está arquivado; o histórico foi preservado." }
            if (old != null) require(!plan.from.isBefore(today)) { "Alterações devem começar hoje ou em uma data futura. O histórico anterior é preservado." }
            val row = MedicineRow(medicine.id, medicine.name.trim(), medicine.presentation.trim(),
                medicine.concentration.trim(), medicine.notes.trim())
            val id = if (old == null) dao.insertMedicine(row) else { dao.updateMedicine(row); medicine.id }
            if (old != null) {
                dao.closeEarlierPlans(id, plan.from.toString(), plan.from.minusDays(1).toString())
                dao.supersedeFuturePlans(id, plan.from.toString())
            }
            dao.insertPlan(PlanRow(medicineId = id, quantity = plan.quantity.trim(),
                times = plan.times.sorted().joinToString(","), fromDate = plan.from.toString(),
                untilDate = plan.until?.toString(), everyDays = plan.everyDays, weekdays = plan.weekdays))
            id
        }
    }
    suspend fun archive(id: Long, now: Instant = Instant.now()) = db.withTransaction {
        val medicine = dao.medicines().first { it.id == id }
        dao.updateMedicine(medicine.copy(archivedAt = now.toEpochMilli()))
    }
    suspend fun confirm(occurrence: Occurrence, quantity: String, date: LocalDate, time: LocalTime,
                        editing: AdministrationRow? = null, reason: String = "",
                        now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): Long {
        require(quantity.isNotBlank() && quantity.length <= 120) { "Informe a quantidade realmente administrada." }
        val actualZone = editing?.zone?.let(ZoneId::of) ?: zone
        val local = date.atTime(time.withSecond(0).withNano(0))
        val actual = local.atZone(actualZone)
        require(actual.toLocalDateTime() == local) { "Esse horário não existiu neste fuso. Confira a data e a hora." }
        require(!actual.toInstant().isAfter(now)) { "A administração não pode ser registrada no futuro." }
        if (editing != null) require(reason.trim().isNotEmpty() && reason.length <= 500) { "Informe o motivo da correção (até 500 caracteres)." }
        return db.withTransaction {
            val old = dao.administration(occurrence.key)
            if (editing == null) {
                require(old == null) { "Este lembrete já foi confirmado. Use Corrigir no histórico." }
                val medicine = dao.medicines().firstOrNull { it.id == occurrence.medicine.id }?.domain()
                val plans = dao.plans().map { it.domain() }
                require(medicine != null && Schedules.onDate(medicine, plans, occurrence.date, zone).any { it.key == occurrence.key }) {
                    "Este lembrete foi alterado. Abra novamente o cadastro atual."
                }
                dao.insertAdministration(AdministrationRow(occurrenceKey = occurrence.key,
                    medicineId = occurrence.medicine.id, planId = occurrence.plan.id,
                    medicineName = occurrence.medicine.name, presentation = occurrence.medicine.presentation,
                    concentration = occurrence.medicine.concentration, prescribedQuantity = occurrence.plan.quantity,
                    plannedDate = occurrence.date.toString(), plannedTime = occurrence.time.toString(),
                    actualQuantity = quantity.trim(), administeredAt = actual.toInstant().toEpochMilli(),
                    actualDate = date.toString(), actualTime = local.toLocalTime().toString(), zone = actualZone.id,
                    recordedAt = now.toEpochMilli(), updatedAt = now.toEpochMilli()))
            } else {
                require(old != null && old.id == editing.id && old.updatedAt == editing.updatedAt) {
                    "O registro mudou. Reabra o histórico antes de corrigir."
                }
                val changed = maxOf(now.toEpochMilli(), old.updatedAt + 1)
                dao.insertCorrection(CorrectionRow(administrationId = old.id, oldQuantity = old.actualQuantity,
                    oldDate = old.actualDate, oldTime = old.actualTime, newQuantity = quantity.trim(),
                    newDate = date.toString(), newTime = local.toLocalTime().toString(), reason = reason.trim(), changedAt = changed))
                dao.updateAdministration(old.copy(actualQuantity = quantity.trim(), administeredAt = actual.toInstant().toEpochMilli(),
                    actualDate = date.toString(), actualTime = local.toLocalTime().toString(), updatedAt = changed))
                old.id
            }
        }
    }
    companion object { fun get(context: Context) = CareStore(CareDatabase.get(context)) }
}
