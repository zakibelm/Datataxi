package com.datataxi.domain

import java.time.LocalDateTime

/**
 * Filtres du tableau de bord.
 * @param zones zones gardées (vide = toutes)
 * @param types types d'appel gardés (vide = tous) : un appel est gardé s'il a au moins un de ces types
 * @param startMinute heure de début en minutes depuis minuit (null = maintenant)
 * @param endMinute heure de fin en minutes depuis minuit (null = début + 2 h)
 */
data class Filters(
    val zones: Set<String> = emptySet(),
    val types: Set<String> = emptySet(),
    val startMinute: Int? = null,
    val endMinute: Int? = null,
) {
    fun keep(c: Call): Boolean = (zones.isEmpty() || c.zone in zones) && (types.isEmpty() || c.types.any { it in types })

    val activeCount: Int
        get() = (if (zones.isNotEmpty()) 1 else 0) + (if (types.isNotEmpty()) 1 else 0) +
            (if (startMinute != null || endMinute != null) 1 else 0)

    /** Début de la plage, arrondi à la tranche de 10 minutes. */
    fun start(now: LocalDateTime): LocalDateTime {
        val base = now.withSecond(0).withNano(0)
        val m = startMinute ?: (now.hour * 60 + now.minute)
        val floored = m / 10 * 10
        return base.withHour(floored / 60).withMinute(floored % 60)
    }

    /** Nombre de tranches de 10 minutes de la plage (1 à [MAX_SLOTS]). */
    fun slotCount(now: LocalDateTime): Int {
        val s = start(now)
        val startM = s.hour * 60 + s.minute
        val e = endMinute ?: return DEFAULT_SLOTS
        var length = e - startM
        if (length <= 0) length += 24 * 60
        return ((length + 9) / 10).coerceIn(1, MAX_SLOTS)
    }

    companion object {
        const val DEFAULT_SLOTS = 12   // 2 heures
        const val MAX_SLOTS = 36       // 6 heures
    }
}
