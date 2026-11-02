package com.datataxi.domain

import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Donne à chaque ligne du journal Fraxion la date réelle de l'événement.
 *
 * Fraxion n'affiche que l'heure (HH:mm:ss). L'ancienne version datait chaque ligne du jour
 * de sa lecture : un message de 23:58 lu le lendemain matin recevait la date du lendemain.
 *
 * Règles, dans l'ordre de l'écran (du plus récent au plus ancien) :
 * - une ligne déjà connue garde sa date et sert de repère pour les suivantes ;
 * - la première ligne inconnue sans repère : aujourd'hui, ou hier si son heure est plus tard
 *   que l'heure actuelle (elle ne peut pas être dans le futur) ;
 * - ensuite, quand l'heure remonte de plus de 12 h d'une ligne à la suivante, on a passé
 *   minuit en reculant : on recule d'un jour ;
 * - aucune date n'est jamais dans le futur.
 */
object EventDater {

    private const val HALF_DAY = 12 * 3600

    /** Marge pour un léger décalage d'horloge entre Fraxion et la tablette. */
    private const val CLOCK_TOLERANCE = 10 * 60

    /** Secondes depuis minuit de l'heure HH:mm:ss en début de ligne, ou null. */
    fun secondsOf(text: String): Int? {
        if (text.length < 8 || text[2] != ':' || text[5] != ':') return null
        val h = text.substring(0, 2).toIntOrNull() ?: return null
        val m = text.substring(3, 5).toIntOrNull() ?: return null
        val s = text.substring(6, 8).toIntOrNull() ?: return null
        if (h !in 0..23 || m !in 0..59 || s !in 0..59) return null
        return h * 3600 + m * 60 + s
    }

    /**
     * @param lines lignes d'événement dans l'ordre de l'écran (plus récente en premier).
     * @param now moment de la lecture.
     * @param known date déjà enregistrée pour une ligne, ou null si elle est nouvelle.
     * @return une date par ligne, dans le même ordre.
     */
    fun date(
        lines: List<String>,
        now: LocalDateTime,
        known: (String) -> LocalDate?,
    ): List<LocalDate> {
        val today = now.toLocalDate()
        val nowSec = now.toLocalTime().toSecondOfDay()
        var prevDate: LocalDate? = null
        var prevSec: Int? = null
        val out = ArrayList<LocalDate>(lines.size)

        for (line in lines) {
            val sec = secondsOf(line)
            val pd = prevDate
            val ps = prevSec
            val d: LocalDate = known(line) ?: run {
                var guess: LocalDate =
                    if (pd == null || ps == null || sec == null) today
                    else if (sec - ps > HALF_DAY) pd.minusDays(1)
                    else pd
                // Jamais dans le futur.
                if (guess.isAfter(today)) guess = today
                if (guess == today && sec != null && sec > nowSec + CLOCK_TOLERANCE) guess = today.minusDays(1)
                guess
            }
            out.add(d)
            prevDate = d
            if (sec != null) prevSec = sec
        }
        return out
    }
}
