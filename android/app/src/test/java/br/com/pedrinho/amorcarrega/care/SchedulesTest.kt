package br.com.pedrinho.amorcarrega.care

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class SchedulesTest {
    private val med = Medicine(1, "Medicamento de teste")
    private val utc = ZoneId.of("UTC")
    private fun plan(from: String = "2026-10-20", times: List<String> = listOf("08:00", "18:00")) =
        Plan(1, med.id, "quantidade informada", times.map(LocalTime::parse), LocalDate.parse(from))
    @Test fun nextUsesAllTimesAndMovesToTomorrow() {
        val p = plan()
        assertEquals(LocalTime.of(18, 0), Schedules.next(med, listOf(p), Instant.parse("2026-10-22T08:01:00Z"), utc)!!.time)
        assertEquals(LocalDate.parse("2026-10-23"), Schedules.next(med, listOf(p), Instant.parse("2026-10-22T18:00:00Z"), utc)!!.date)
    }
    @Test fun confirmedOccurrenceDoesNotScheduleAgain() {
        val p = plan(); val dose = Occurrence(med, p, LocalDate.parse("2026-10-22"), LocalTime.of(18, 0))
        val next = Schedules.next(med, listOf(p), Instant.parse("2026-10-22T17:59:00Z"), utc, setOf(dose.key))!!
        assertEquals(LocalDate.parse("2026-10-23"), next.date)
    }
    @Test fun intervalIsAnchoredToStartAndCrossesLeapDay() {
        val p = plan("2028-02-28", listOf("18:00")).copy(everyDays = 2)
        assertFalse(p.includes(LocalDate.parse("2028-02-29")))
        assertTrue(p.includes(LocalDate.parse("2028-03-01")))
    }
    @Test fun selectedWeekdaysSkipWeekend() {
        val p = plan().copy(weekdays = 31)
        val next = Schedules.next(med, listOf(p), Instant.parse("2026-10-23T19:00:00Z"), utc)!!
        assertEquals(LocalDate.parse("2026-10-26"), next.date)
    }
    @Test fun endDateIsInclusiveAndThenStops() {
        val p = plan().copy(until = LocalDate.parse("2026-10-22"))
        assertNotNull(Schedules.next(med, listOf(p), Instant.parse("2026-10-22T17:00:00Z"), utc))
        assertNull(Schedules.next(med, listOf(p), Instant.parse("2026-10-22T19:00:00Z"), utc))
    }
    @Test fun futureRevisionKeepsOldQuantityUntilEffectiveDate() {
        val old = plan().copy(until = LocalDate.parse("2026-10-24"))
        val changed = plan("2026-10-25").copy(id = 2, quantity = "nova quantidade explicitamente cadastrada")
        assertEquals(old.quantity, Schedules.onDate(med, listOf(old, changed), LocalDate.parse("2026-10-24"), utc).first().plan.quantity)
        assertEquals(changed.quantity, Schedules.onDate(med, listOf(old, changed), LocalDate.parse("2026-10-25"), utc).first().plan.quantity)
    }
    @Test fun springGapShiftsMissingLocalTimeAndDoesNotDuplicate() {
        val zone = ZoneId.of("America/New_York"); val p = plan("2026-03-08", listOf("02:30"))
        val next = Schedules.next(med, listOf(p), Instant.parse("2026-03-08T06:00:00Z"), zone)!!
        assertEquals(Instant.parse("2026-03-08T07:30:00Z"), next.instant(zone))
    }
    @Test fun autumnOverlapHasOneReminderPerLocalTime() {
        val zone = ZoneId.of("America/New_York"); val p = plan("2026-11-01", listOf("01:30"))
        val next = Schedules.next(med, listOf(p), Instant.parse("2026-11-01T05:31:00Z"), zone)!!
        assertEquals(LocalDate.parse("2026-11-02"), next.date)
    }
    @Test fun timezoneChangeRecomputesWallClockInstant() {
        val p = plan(); val date = LocalDate.parse("2026-10-22")
        val dose = Occurrence(med, p, date, LocalTime.of(18, 0))
        assertEquals(Instant.parse("2026-10-22T21:00:00Z"), dose.instant(ZoneId.of("America/Sao_Paulo")))
        assertEquals(Instant.parse("2026-10-22T18:00:00Z"), dose.instant(utc))
    }
    @Test fun missingConfirmationIsNotAnAdministration() {
        val dose = Occurrence(med, plan(), LocalDate.parse("2026-10-22"), LocalTime.of(18, 0))
        assertEquals(DoseStatus.UNCONFIRMED, Schedules.status(dose, emptySet(), Instant.parse("2026-10-22T19:00:00Z"), utc))
        assertEquals(DoseStatus.PENDING, Schedules.status(dose, emptySet(), Instant.parse("2026-10-22T17:00:00Z"), utc))
        assertEquals(DoseStatus.CONFIRMED, Schedules.status(dose, setOf(dose.key), Instant.parse("2026-10-22T17:00:00Z"), utc))
    }
    @Test fun archiveStopsFutureSchedulingAndPreservesEarlierOccurrences() {
        val archived = med.copy(archivedAt = Instant.parse("2026-10-22T12:00:00Z").toEpochMilli())
        assertNull(Schedules.next(archived, listOf(plan()), Instant.parse("2026-10-22T07:00:00Z"), utc))
        assertEquals(listOf(LocalTime.of(8, 0)), Schedules.onDate(archived, listOf(plan()), LocalDate.parse("2026-10-22"), utc).map { it.time })
    }
    @Test fun endedOrSupersededPlansDoNotReturnNextDose() {
        assertNull(Schedules.next(med, listOf(plan().copy(superseded = true)), Instant.parse("2026-10-22T07:00:00Z"), utc))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsEmptyQuantity() { plan().copy(quantity = " ").validate() }
    @Test(expected = IllegalArgumentException::class) fun rejectsDuplicateTimes() { plan(times = listOf("08:00", "08:00")).validate() }
    @Test(expected = IllegalArgumentException::class) fun rejectsNoWeekday() { plan().copy(weekdays = 0).validate() }
    @Test(expected = IllegalArgumentException::class) fun rejectsEndBeforeStart() { plan().copy(until = LocalDate.parse("2026-10-19")).validate() }
}
