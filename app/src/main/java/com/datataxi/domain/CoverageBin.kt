package com.datataxi.domain

import java.time.LocalDateTime

/**
 * Preuve de couverture : une « case » de 5 minutes est marquée quand l'écran de Fraxion a réellement
 * été lisible (application au premier plan, écran allumé). Absence de case = la tablette ne lisait pas,
 * ce qui n'est PAS la même chose qu'une heure sans appel.
 */
object CoverageBin {
    const val MINUTES = 5

    /** Début de la case de 5 minutes, au format « 2026-11-24T18:35:00 ». */
    fun start(now: LocalDateTime): String {
        val m = now.minute / MINUTES * MINUTES
        return now.withMinute(m).withSecond(0).withNano(0).toString().let { if (it.length == 16) "$it:00" else it }
    }
}
