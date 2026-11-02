package com.datataxi.domain

import java.time.LocalDate

/**
 * Reconnaît un événement déjà lu par son texte (heure, véhicule, zones...), sans tenir
 * compte de la date. Fraxion affiche plusieurs jours de journal : sans cette règle, chaque
 * relecture le lendemain créait une copie datée du lendemain.
 *
 * Le même texte exact (même seconde, même véhicule, mêmes zones) peut revenir un autre jour
 * pour un vrai nouvel événement : au-delà de [WINDOW_DAYS] jours d'écart, c'est un nouvel événement.
 */
class JournalDedup {

    private val datesByText = HashMap<String, MutableList<LocalDate>>()

    /** Date déjà enregistrée pour ce texte, proche de [around], ou null. */
    fun knownDate(text: String, around: LocalDate): LocalDate? =
        datesByText[text]
            ?.filter { within(it, around) }
            ?.maxOrNull()

    fun isSeen(text: String, date: LocalDate): Boolean = knownDate(text, date) != null

    fun record(text: String, date: LocalDate) {
        datesByText.getOrPut(text) { ArrayList(1) }.add(date)
    }

    fun clear() = datesByText.clear()

    companion object {
        const val WINDOW_DAYS = 7L

        private fun within(a: LocalDate, b: LocalDate): Boolean =
            !a.isBefore(b.minusDays(WINDOW_DAYS)) && !a.isAfter(b.plusDays(WINDOW_DAYS))

        /**
         * Nettoyage d'un journal enregistré par l'ancienne version : pour chaque événement
         * relu plusieurs jours de suite, on garde la copie la plus ancienne (sa date est la plus
         * proche du vrai jour) et on supprime les plus récentes.
         *
         * @return les lignes gardées (ordre d'origine) et, pour chacune, son index d'origine.
         */
        fun keepOldest(entries: List<JournalEntry>): Pair<List<JournalEntry>, List<Int>> {
            val dates = entries.map { runCatching { LocalDate.parse(it.date) }.getOrNull() }
            // Les plus anciennes d'abord ; à date égale, l'ordre d'origine.
            val order = entries.indices.sortedWith(
                compareBy<Int> { dates[it] ?: LocalDate.MAX }.thenBy { it }
            )
            val dedup = JournalDedup()
            val keep = BooleanArray(entries.size)
            for (i in order) {
                val d = dates[i]
                if (d == null) { keep[i] = true; continue } // date illisible : on ne touche à rien
                if (dedup.isSeen(entries[i].text, d)) continue
                dedup.record(entries[i].text, d)
                keep[i] = true
            }
            val idx = entries.indices.filter { keep[it] }
            return idx.map { entries[it] } to idx
        }
    }
}
