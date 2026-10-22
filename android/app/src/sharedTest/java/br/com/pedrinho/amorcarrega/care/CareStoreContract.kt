package br.com.pedrinho.amorcarrega.care

import androidx.room.Room
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.time.*

abstract class CareStoreContract {
    protected abstract val context: android.content.Context
    private lateinit var db: CareDatabase
    private lateinit var store: CareStore
    private val today = LocalDate.parse("2026-10-22")
    private val now = Instant.parse("2026-10-22T20:00:00Z")
    private val zone = ZoneId.of("UTC")
    @Before fun setup() { db = Room.inMemoryDatabaseBuilder(context, CareDatabase::class.java).build(); store = CareStore(db) }
    @After fun close() { db.close() }
    private suspend fun add(name: String = "Medicamento de teste"): Occurrence {
        val id = store.saveMedicine(Medicine(name = name, presentation = "cápsula", concentration = "concentração informada"),
            Plan(medicineId = 0, quantity = "quantidade prescrita", times = listOf(LocalTime.of(18, 0)), from = today.minusDays(3)))
        return store.snapshot().onDate(today, zone).first { it.medicine.id == id }
    }
    private suspend fun record(dose: Occurrence) = store.confirm(dose, "quantidade real", today, LocalTime.of(18, 5), now = now, zone = zone)
    @Test fun persistsIndependentMedicinesAndTimes() = runBlocking {
        add("Um"); add("Dois"); val s = store.snapshot()
        assertEquals(2, s.medicines.size); assertEquals(2, s.onDate(today, zone).size); assertTrue(s.records.isEmpty())
    }
    @Test fun calendarUsesActualAdministrationDateNotScheduledDate() = runBlocking {
        val dose = add(); store.confirm(dose, "quantidade real", today.minusDays(1), LocalTime.of(23, 50), now = now, zone = zone)
        val s = store.snapshot()
        assertEquals(1, s.recordedOn(today.minusDays(1)).size); assertTrue(s.recordedOn(today).isEmpty())
        assertTrue(dose.key in s.confirmedKeys)
    }
    @Test fun duplicateConfirmationIsRejectedWithoutReplacingOriginal() = runBlocking {
        val dose = add(); record(dose)
        try { record(dose); fail("Duplicated administration accepted") } catch (_: IllegalArgumentException) {}
        assertEquals(1, store.snapshot().records.size)
    }
    @Test fun correctionKeepsPreviousValuesAndReason() = runBlocking {
        val dose = add(); record(dose); val old = store.snapshot().records.single()
        store.confirm(dose, "quantidade corrigida", today, LocalTime.of(18, 10), old, "Correção do registro", now, zone)
        val s = store.snapshot(); assertEquals(1, s.records.size); assertEquals(1, s.corrections.size)
        assertEquals("quantidade real", s.corrections.single().oldQuantity)
        assertEquals("Correção do registro", s.corrections.single().reason)
        assertEquals("quantidade corrigida", s.records.single().actualQuantity)
        assertEquals(old.recordedAt, s.records.single().recordedAt)
    }
    @Test fun correctingActualDateMovesCalendarMarkAndKeepsOldDateInAudit() = runBlocking {
        val dose = add(); record(dose); val old = store.snapshot().records.single()
        store.confirm(dose, old.actualQuantity, today.minusDays(1), LocalTime.of(18, 10), old, "Data real corrigida", now, zone)
        val s = store.snapshot()
        assertTrue(s.recordedOn(today).isEmpty())
        assertEquals(1, s.recordedOn(today.minusDays(1)).size)
        assertEquals(today.toString(), s.corrections.single().oldDate)
    }
    @Test fun staleCorrectionCannotOverwriteNewerCorrection() = runBlocking {
        val dose = add(); record(dose); val old = store.snapshot().records.single()
        store.confirm(dose, "primeira correção", today, LocalTime.of(18, 10), old, "Motivo", now, zone)
        try { store.confirm(dose, "valor antigo", today, LocalTime.of(18, 10), old, "Motivo", now, zone); fail("Stale write accepted") }
        catch (_: IllegalArgumentException) {}
        assertEquals(1, store.snapshot().corrections.size)
    }
    @Test fun correctionWithoutReasonRollsBack() = runBlocking {
        val dose = add(); record(dose); val old = store.snapshot().records.single()
        try { store.confirm(dose, "outra quantidade", today, LocalTime.of(18, 10), old, "", now, zone); fail("Empty reason accepted") }
        catch (_: IllegalArgumentException) {}
        assertEquals(old, store.snapshot().records.single()); assertTrue(store.snapshot().corrections.isEmpty())
    }
    @Test fun futureAdministrationDoesNotWriteRecord() = runBlocking {
        val dose = add()
        try { store.confirm(dose, "quantidade", today.plusDays(1), LocalTime.of(18, 0), now = now, zone = zone); fail("Future record accepted") }
        catch (_: IllegalArgumentException) {}
        try { store.confirm(dose, "quantidade", today, LocalTime.of(20, 1), now = now.plusSeconds(45), zone = zone); fail("Next minute in the future accepted") }
        catch (_: IllegalArgumentException) {}
        assertTrue(store.snapshot().records.isEmpty())
    }
    @Test fun futurePlanChangePreservesPreviousPrescriptionAndHistory() = runBlocking {
        val dose = add(); record(dose)
        store.saveMedicine(dose.medicine, dose.plan.copy(from = today.plusDays(2), quantity = "nova prescrição"), today)
        val s = store.snapshot()
        assertEquals("quantidade prescrita", s.onDate(today.plusDays(1), zone).single().plan.quantity)
        assertEquals("nova prescrição", s.onDate(today.plusDays(2), zone).single().plan.quantity)
        assertEquals("quantidade prescrita", s.records.single().prescribedQuantity)
    }
    @Test fun replacingFutureChangeDoesNotCreateOverlappingPlans() = runBlocking {
        val dose = add()
        store.saveMedicine(dose.medicine, dose.plan.copy(from = today.plusDays(5), quantity = "alteração futura"), today)
        store.saveMedicine(dose.medicine, dose.plan.copy(from = today.plusDays(2), quantity = "alteração revisada"), today)
        val s = store.snapshot()
        assertEquals(1, s.onDate(today.plusDays(6), zone).size)
        assertEquals("alteração revisada", s.onDate(today.plusDays(6), zone).single().plan.quantity)
    }
    @Test fun archivedMedicineKeepsHistoryButSchedulesNothing() = runBlocking {
        val dose = add(); record(dose); store.archive(dose.medicine.id, now)
        val s = store.snapshot(); assertEquals(1, s.records.size)
        assertNull(Schedules.next(s.medicines.single(), s.plans, now, zone))
        assertEquals(1, s.onDate(today.minusDays(1), zone).size)
    }
    @Test fun batteryPreferencesAreNotTouchedByCareDatabase() = runBlocking {
        val prefs = context.getSharedPreferences("config", 0); val before = prefs.all.toMap()
        val dose = add(); record(dose)
        assertEquals(before, prefs.all)
    }
    @Test fun dataSurvivesDatabaseCloseAndReopen() = runBlocking {
        val name = "care-test-${System.nanoTime()}.db"
        var disk = Room.databaseBuilder(context, CareDatabase::class.java, name).build()
        try {
            CareStore(disk).saveMedicine(Medicine(name = "Persistência"), Plan(medicineId = 0, quantity = "prescrita", times = listOf(LocalTime.NOON), from = today))
            disk.close(); disk = Room.databaseBuilder(context, CareDatabase::class.java, name).build()
            assertEquals("Persistência", CareStore(disk).snapshot().medicines.single().name)
        } finally { disk.close(); context.deleteDatabase(name) }
    }
}
