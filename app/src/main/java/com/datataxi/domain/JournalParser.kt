package com.datataxi.domain

object JournalParser {

    /** Une ligne d'événement commence par une heure, par exemple « 14:47:16 Véhicule 121 a reçu ... ». */
    private val EVENT = Regex("\\d{2}:\\d{2}:\\d{2}\\s+\\S.*")

    /**
     * Extrait les lignes d'événement. Chaque texte lu est découpé par ligne, au cas où
     * Fraxion afficherait tout le journal dans un seul élément.
     */
    fun eventLines(lines: List<CapturedLine>): List<String> =
        lines
            .flatMap { it.text.lines() }
            .map { it.trim().replace('\t', ' ') }
            .filter { EVENT.matches(it) }
}
