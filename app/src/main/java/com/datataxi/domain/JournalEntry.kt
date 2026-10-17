package com.datataxi.domain

/** Une ligne d'événement du journal de Fraxion, avec la date de sa première lecture. */
data class JournalEntry(val date: String, val text: String) {
    val key: String get() = "$date|$text"

    /** Heure HH:mm:ss en début de ligne, utilisée pour trier. */
    val time: String get() = text.take(8)
}
