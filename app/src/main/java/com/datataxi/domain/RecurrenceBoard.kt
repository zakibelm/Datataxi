package com.datataxi.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/** Un appel : jour, heure (secondes depuis minuit), zone de départ, véhicule, type d'appel. */
data class Call(
    val date: LocalDate,
    val second: Int,
    val zone: String,
    val vehicle: String,
    val type: String = CallType.REGULIER,
) {
    val slot: Int get() = second / 60 / RecurrenceBoard.SLOT_MINUTES
    /** Même appel vu par l'historique du Google Sheet et par le journal de la tablette. */
    val key: String get() = "$date|$second|$zone|$vehicle"
}

/**
 * Poids d'une récurrence : nombre de journées (même jour de la semaine) où la zone a eu au moins
 * un appel dans la tranche (± 10 min). Seuils mesurés sur l'historique sept.–nov. 2026 :
 * la chance d'avoir un appel dans la zone ce jour-là monte avec le poids.
 */
enum class Weight(val minDays: Int, val label: String, val measured: String) {
    FAIBLE(2, "Faible", "≈ 4 fois sur 10"),
    MOYEN(3, "Moyen", "≈ 5 fois sur 10"),
    FORT(4, "Fort", "≈ 6 fois sur 10"),
    TRES_FORT(5, "Très fort", "6 fois sur 10 et plus");

    companion object {
        fun of(days: Int): Weight? = entries.lastOrNull { days >= it.minDays }
    }
}

/** Une zone qui revient dans la tranche : [days] journées sur [observedDays] observées. */
data class Recurrence(
    val zone: String,
    val days: Int,
    val observedDays: Int,
    /** Types d'appel observés dans la zone à cette heure-ci, du plus fréquent au moins fréquent (type, nombre d'appels). */
    val types: List<Pair<String, Int>> = emptyList(),
) {
    val weight: Weight get() = Weight.of(days)!!
}

data class SlotRecurrences(val dayOfWeek: DayOfWeek, val slot: Int, val zones: List<Recurrence>) {
    fun of(zone: String): Recurrence? = zones.firstOrNull { it.zone == zone }
}

/**
 * Tableau des récurrences : pour un jour de la semaine et une tranche de 10 minutes, les zones
 * qui ont eu des appels au moins [MIN_DAYS] journées différentes (même jour de la semaine),
 * avec une tolérance de ± 10 minutes (un appel à 08:19 et un à 08:21 sont la même habitude).
 *
 * Mesure (2 dernières semaines rejouées jour par jour, sans voir l'avenir) : une zone récurrente
 * a eu un appel le jour même 42 % (2 jours) à 60 % (4 jours et plus) du temps, contre 4 % sans
 * récurrence.
 */
object RecurrenceBoard {

    const val SLOT_MINUTES = 10
    const val MIN_DAYS = 2
    private const val TOLERANCE_SLOTS = 1

    fun slotOf(time: LocalTime): Int = (time.hour * 60 + time.minute) / SLOT_MINUTES

    fun label(slot: Int): String {
        val start = slot * SLOT_MINUTES
        val end = (start + SLOT_MINUTES) % (24 * 60)
        return "%02d:%02d–%02d:%02d".format(start / 60, start % 60, end / 60, end % 60)
    }

    /** Appels du journal de la tablette : véhicule qui a reçu ou pris un appel avec une zone de départ. */
    fun callsFromJournal(entries: List<JournalEntry>): List<Call> = entries.mapNotNull { e ->
        val date = runCatching { LocalDate.parse(e.date) }.getOrNull() ?: return@mapNotNull null
        val sec = EventDater.secondsOf(e.text) ?: return@mapNotNull null
        val d = EventDecoder.decode(e.text)
        val zone = d.departureZone ?: return@mapNotNull null
        val status = d.status ?: return@mapNotNull null
        if (!status.startsWith("recu") && !status.startsWith("pris")) return@mapNotNull null
        Call(date, sec, zone, d.vehicleId.orEmpty(), CallType.of(d.serviceType, status))
    }

    /** Historique complet + journal de la tablette, sans compter deux fois le même appel. */
    fun merge(history: List<Call>, local: List<Call>): List<Call> {
        val seen = HashSet<String>(history.size + local.size)
        val out = ArrayList<Call>(history.size + local.size)
        for (c in history) if (seen.add(c.key)) out.add(c)
        for (c in local) if (seen.add(c.key)) out.add(c)
        return out
    }

    /** Index par jour de la semaine, à construire une fois quand les données changent. */
    class Index(calls: List<Call>, observedFrom: List<Call> = calls) {
        internal val byDay: Map<DayOfWeek, List<Call>> = calls.groupBy { it.date.dayOfWeek }
        /** Journées observées : comptées sur tous les appels, même quand un filtre est actif. */
        internal val observedDays: Map<DayOfWeek, Int> =
            observedFrom.groupBy { it.date.dayOfWeek }.mapValues { (_, list) -> list.map { it.date }.distinct().size }
        val totalCalls: Int = calls.size
        val firstDate: LocalDate? = calls.minOfOrNull { it.date }
        val lastDate: LocalDate? = calls.maxOfOrNull { it.date }
        fun observedDays(day: DayOfWeek): Int = observedDays[day] ?: 0
    }

    fun recurrences(index: Index, day: DayOfWeek, slot: Int): SlotRecurrences {
        val calls = index.byDay[day].orEmpty()
        val datesByZone = HashMap<String, MutableSet<LocalDate>>()
        val typesByZone = HashMap<String, HashMap<String, Int>>()
        for (c in calls) {
            if (kotlin.math.abs(c.slot - slot) > TOLERANCE_SLOTS) continue
            datesByZone.getOrPut(c.zone) { HashSet() }.add(c.date)
            val t = typesByZone.getOrPut(c.zone) { HashMap() }
            t[c.type] = (t[c.type] ?: 0) + 1
        }
        val observed = index.observedDays(day)
        val zones = datesByZone
            .filter { it.value.size >= MIN_DAYS }
            .map { (zone, dates) ->
                val types = typesByZone[zone].orEmpty().entries
                    .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { CallType.ALL.indexOf(it.key) })
                    .map { it.key to it.value }
                Recurrence(zone, dates.size, observed, types)
            }
            .sortedWith(compareByDescending<Recurrence> { it.days }.thenBy { it.zone.toIntOrNull() ?: Int.MAX_VALUE })
        return SlotRecurrences(day, slot, zones)
    }
}
