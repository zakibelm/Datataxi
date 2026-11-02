package com.datataxi.domain

/**
 * Une ligne d'événement du journal de Fraxion, avec la date de l'événement.
 * Le doublon se reconnaît au texte seul (JournalDedup) ; [key] sert à l'empreinte trip_id.
 */
data class JournalEntry(val date: String, val text: String) {
    val key: String get() = "$date|$text"

    /** Heure HH:mm:ss en début de ligne, utilisée pour trier. */
    val time: String get() = text.take(8)
}
