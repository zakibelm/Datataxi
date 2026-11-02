package com.datataxi.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/** Résultat d'une journée rejouée. */
data class DayReliability(
    val date: LocalDate,
    val judgedSlots: Int,
    val predicted: Int,
    val realized: Int,
    /** Couples tranche × zone sans récurrence, et combien ont quand même eu un appel. */
    val baselineCount: Int,
    val baselineRealized: Int,
    val byWeight: Map<Weight, Pair<Int, Int>>,
)

/** Réussite regroupée sur une période (jour, semaine ou mois). */
data class PeriodReliability(
    val start: LocalDate,
    val label: String,
    val predicted: Int,
    val realized: Int,
    val baselineCount: Int,
    val baselineRealized: Int,
) {
    val rate: Double get() = if (predicted == 0) 0.0 else realized.toDouble() / predicted
    val baseline: Double? get() = if (baselineCount == 0) null else baselineRealized.toDouble() / baselineCount
}

/**
 * Niveau de confiance de la solution : chaque journée est rejouée comme si on y était.
 * Les récurrences sont calculées avec les seules journées précédentes, puis comparées aux appels
 * réellement observés ce jour-là. Seules les tranches avec des données sont jugées (une tablette
 * éteinte ne compte pas comme une erreur). Même calcul que le script du Google Sheet.
 */
object Reliability {

    enum class Period(val label: String) { DAY("Jour"), WEEK("Semaine"), MONTH("Mois") }

    private const val SLOTS = 24 * 60 / RecurrenceBoard.SLOT_MINUTES

    fun daily(calls: List<Call>): List<DayReliability> {
        val byDate = calls.groupBy { it.date }.toSortedMap()
        val index = HashMap<DayOfWeek, HashMap<Long, MutableSet<LocalDate>>>()
        val knownZones = LinkedHashSet<String>()
        val zoneIds = HashMap<String, Int>()
        fun key(slot: Int, zone: String): Long = slot.toLong() * 100_000 + zoneIds.getOrPut(zone) { zoneIds.size }
        val out = ArrayList<DayReliability>()

        for ((date, dayCalls) in byDate) {
            val w = date.dayOfWeek
            val realized = HashSet<Long>()
            val judged = HashSet<Int>()
            for (c in dayCalls) for (s in c.slot - 1..c.slot + 1) {
                if (s !in 0 until SLOTS) continue
                realized.add(key(s, c.zone)); judged.add(s)
            }

            val idx = index[w].orEmpty()
            var predicted = 0
            var ok = 0
            val byWeight = HashMap<Weight, Pair<Int, Int>>()
            val predictedKeys = HashSet<Long>()
            for ((k, dates) in idx) {
                val s = (k / 100_000).toInt()
                if (s !in judged) continue
                val weight = Weight.of(dates.size) ?: continue
                predictedKeys.add(k)
                val hit = if (k in realized) 1 else 0
                predicted++; ok += hit
                val (n, h) = byWeight[weight] ?: (0 to 0)
                byWeight[weight] = (n + 1) to (h + hit)
            }

            var base = 0
            var baseOk = 0
            for (s in judged) for (z in knownZones) {
                val k = key(s, z)
                if (k in predictedKeys) continue
                base++
                if (k in realized) baseOk++
            }

            if (predicted > 0) out.add(DayReliability(date, judged.size, predicted, ok, base, baseOk, byWeight))

            val target = index.getOrPut(w) { HashMap() }
            for (c in dayCalls) {
                knownZones.add(c.zone)
                for (s in c.slot - 1..c.slot + 1) {
                    if (s !in 0 until SLOTS) continue
                    target.getOrPut(key(s, c.zone)) { HashSet() }.add(date)
                }
            }
        }
        return out
    }

    fun group(days: List<DayReliability>, period: Period): List<PeriodReliability> =
        days.groupBy { startOf(it.date, period) }.toSortedMap().map { (start, list) ->
            PeriodReliability(
                start = start,
                label = label(start, period),
                predicted = list.sumOf { it.predicted },
                realized = list.sumOf { it.realized },
                baselineCount = list.sumOf { it.baselineCount },
                baselineRealized = list.sumOf { it.baselineRealized },
            )
        }

    /** Réussite par poids sur toutes les journées jugées : prédictions, réalisées. */
    fun byWeight(days: List<DayReliability>): Map<Weight, Pair<Int, Int>> =
        Weight.entries.associateWith { w ->
            days.fold(0 to 0) { acc, d -> d.byWeight[w]?.let { (acc.first + it.first) to (acc.second + it.second) } ?: acc }
        }

    fun startOf(date: LocalDate, period: Period): LocalDate = when (period) {
        Period.DAY -> date
        Period.WEEK -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        Period.MONTH -> date.withDayOfMonth(1)
    }

    private val FR = Locale.CANADA_FRENCH

    fun label(start: LocalDate, period: Period): String = when (period) {
        Period.DAY -> "%02d/%02d".format(start.dayOfMonth, start.monthValue)
        Period.WEEK -> "sem. %02d/%02d".format(start.dayOfMonth, start.monthValue)
        Period.MONTH -> start.month.getDisplayName(TextStyle.SHORT, FR) + " " + start.year
    }
}
