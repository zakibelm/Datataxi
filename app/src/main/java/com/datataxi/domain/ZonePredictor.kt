package com.datataxi.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/** Un appel observé : jour, tranche de 10 minutes et zone de départ. */
data class Observation(val date: LocalDate, val slot: Int, val zone: String) {
    val dayOfWeek: DayOfWeek get() = date.dayOfWeek
}

/** Chance qu'un appel parte de [zone] dans la tranche, entre 0 et 1. */
data class ZoneChance(val zone: String, val probability: Double)

data class SlotPrediction(
    val dayOfWeek: DayOfWeek,
    val slot: Int,
    /** Zones de la plus probable à la moins probable. */
    val chances: List<ZoneChance>,
    /** true : calculé sur le même jour de la semaine ; false : sur tous les jours (historique trop court). */
    val sameWeekday: Boolean,
    /** Appels observés autour de cette tranche (± 20 min). */
    val samples: Int,
    /** Nombre de journées différentes dans l'historique utilisé. */
    val days: Int,
) {
    fun probabilityOf(zone: String): Double = chances.firstOrNull { it.zone == zone }?.probability ?: 0.0
}

/**
 * Prédiction simple et explicable, sans rien inventer : la part de chaque zone parmi les appels
 * observés le même jour de la semaine, à la même heure.
 *
 * - Tranches de 10 minutes (08:00–08:10, 08:10–08:20, ...).
 * - Lissage sur les tranches voisines (± 20 min, poids 3-2-1) pour ne pas dépendre d'un seul appel.
 * - Si le même jour de la semaine compte moins de [MIN_SAMPLES] appels autour de la tranche,
 *   on utilise tous les jours, et l'écran l'indique.
 */
object ZonePredictor {

    const val SLOT_MINUTES = 10
    const val SLOTS_PER_DAY = 24 * 60 / SLOT_MINUTES
    const val MIN_SAMPLES = 8
    private val WEIGHTS = intArrayOf(3, 2, 1) // distance 0, 1, 2 tranches

    fun slotOf(time: LocalTime): Int = (time.hour * 60 + time.minute) / SLOT_MINUTES

    fun label(slot: Int): String {
        val start = slot * SLOT_MINUTES
        val end = (start + SLOT_MINUTES) % (24 * 60)
        return "%02d:%02d–%02d:%02d".format(start / 60, start % 60, end / 60, end % 60)
    }

    /** Appels utilisables : véhicule qui a reçu ou pris un appel avec une zone de départ connue. */
    fun observations(entries: List<JournalEntry>): List<Observation> = entries.mapNotNull { e ->
        val date = runCatching { LocalDate.parse(e.date) }.getOrNull() ?: return@mapNotNull null
        val sec = EventDater.secondsOf(e.text) ?: return@mapNotNull null
        val decoded = EventDecoder.decode(e.text)
        val zone = decoded.departureZone ?: return@mapNotNull null
        val status = decoded.status ?: return@mapNotNull null
        if (!status.startsWith("recu") && !status.startsWith("pris")) return@mapNotNull null
        Observation(date, sec / 60 / SLOT_MINUTES, zone)
    }

    fun predict(observations: List<Observation>, day: DayOfWeek, slot: Int): SlotPrediction {
        val same = observations.filter { it.dayOfWeek == day }
        val sameResult = score(same, slot)
        if (sameResult.second >= MIN_SAMPLES) {
            return build(day, slot, sameResult, same, sameWeekday = true)
        }
        return build(day, slot, score(observations, slot), observations, sameWeekday = false)
    }

    /** Poids par zone et nombre d'appels dans la fenêtre. */
    private fun score(obs: List<Observation>, slot: Int): Pair<Map<String, Int>, Int> {
        val weights = HashMap<String, Int>()
        var samples = 0
        for (o in obs) {
            val dist = kotlin.math.abs(o.slot - slot)
            if (dist >= WEIGHTS.size) continue
            weights[o.zone] = (weights[o.zone] ?: 0) + WEIGHTS[dist]
            samples++
        }
        return weights to samples
    }

    private fun build(
        day: DayOfWeek,
        slot: Int,
        scored: Pair<Map<String, Int>, Int>,
        source: List<Observation>,
        sameWeekday: Boolean,
    ): SlotPrediction {
        val (weights, samples) = scored
        val total = weights.values.sum().toDouble()
        val chances = if (total == 0.0) emptyList() else weights.entries
            .map { ZoneChance(it.key, it.value / total) }
            .sortedWith(compareByDescending<ZoneChance> { it.probability }.thenBy { it.zone.toIntOrNull() ?: Int.MAX_VALUE })
        return SlotPrediction(
            dayOfWeek = day,
            slot = slot,
            chances = chances,
            sameWeekday = sameWeekday,
            samples = samples,
            days = source.map { it.date }.distinct().size,
        )
    }
}
