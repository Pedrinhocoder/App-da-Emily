package br.com.pedrinho.amorcarrega.care

import java.time.*

/** Dates/times describe the user's prescription, never a calculated dose. */
data class Medicine(
    val id: Long = 0, val name: String, val presentation: String = "",
    val concentration: String = "", val notes: String = "", val archivedAt: Long? = null
)
data class Plan(
    val id: Long = 0, val medicineId: Long, val quantity: String,
    val times: List<LocalTime>, val from: LocalDate, val until: LocalDate? = null,
    val everyDays: Int = 1, val weekdays: Int = 127, val superseded: Boolean = false
) {
    fun validate() {
        require(quantity.isNotBlank() && quantity.length <= 120) { "Informe a quantidade prescrita." }
        require(times.isNotEmpty() && times.size <= 24 && times.distinct().size == times.size) { "Adicione horários diferentes (até 24)." }
        require(times.all { it.second == 0 && it.nano == 0 }) { "Use horários em horas e minutos." }
        require(until == null || !until.isBefore(from)) { "O término deve ser igual ou posterior ao início." }
        require(everyDays in 1..365) { "O intervalo deve estar entre 1 e 365 dias." }
        require(weekdays in 1..127 && (everyDays == 1 || weekdays == 127)) { "Selecione dias da semana ou um intervalo." }
    }
    fun includes(date: LocalDate): Boolean = !superseded && !date.isBefore(from) &&
        (until == null || !date.isAfter(until)) &&
        java.time.temporal.ChronoUnit.DAYS.between(from, date) % everyDays == 0L &&
        weekdays and (1 shl (date.dayOfWeek.value - 1)) != 0
}
data class Occurrence(val medicine: Medicine, val plan: Plan, val date: LocalDate, val time: LocalTime) {
    val key: String get() = "${medicine.id}:${plan.id}:${date}T$time"
    fun instant(zone: ZoneId): Instant = date.atTime(time).atZone(zone).toInstant()
}
enum class DoseStatus(val label: String) {
    PENDING("Pendente"), CONFIRMED("Confirmado"), UNCONFIRMED("Sem confirmação")
}
object Schedules {
    fun onDate(medicine: Medicine, plans: List<Plan>, date: LocalDate, zone: ZoneId): List<Occurrence> =
        plans.filter { it.medicineId == medicine.id && it.includes(date) }
            .flatMap { plan -> plan.times.sorted().map { Occurrence(medicine, plan, date, it) } }
            .filter { medicine.archivedAt == null || it.instant(zone).toEpochMilli() < medicine.archivedAt }
            .sortedBy { it.time }

    /** One wall-clock occurrence on a DST overlap; Java shifts a missing local time forward. */
    fun next(medicine: Medicine, plans: List<Plan>, after: Instant, zone: ZoneId,
             confirmed: Set<String> = emptySet()): Occurrence? {
        if (medicine.archivedAt != null) return null
        return plans.filter { it.medicineId == medicine.id && !it.superseded }.mapNotNull { plan ->
            var day = maxOf(after.atZone(zone).toLocalDate(), plan.from)
            val horizon = day.plusDays(maxOf(370, plan.everyDays * 7 + 7).toLong())
            var result: Occurrence? = null
            while (!day.isAfter(horizon) && (plan.until == null || !day.isAfter(plan.until))) {
                result = onDate(medicine, listOf(plan), day, zone)
                    .firstOrNull { it.instant(zone).isAfter(after) && it.key !in confirmed }
                if (result != null) break
                day = day.plusDays(1)
            }
            result
        }.minByOrNull { it.instant(zone) }
    }
    fun status(occurrence: Occurrence, confirmed: Set<String>, now: Instant, zone: ZoneId): DoseStatus =
        if (occurrence.key in confirmed) DoseStatus.CONFIRMED
        else if (occurrence.instant(zone).isBefore(now)) DoseStatus.UNCONFIRMED else DoseStatus.PENDING
}
