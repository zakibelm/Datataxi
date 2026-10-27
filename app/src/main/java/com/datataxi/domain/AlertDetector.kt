package com.datataxi.domain

/**
 * Cherche les mots déclencheurs (« go », « je pars »...) dans les textes d'un écran.
 * Le mot doit être entier : « go » ne se déclenche pas sur « gonfler » ou « Margot ».
 */
object AlertDetector {

    /** Retourne le premier mot déclencheur trouvé, tel que l'utilisateur l'a écrit, ou null. */
    fun firstMatch(texts: List<String>, phrases: List<String>): String? {
        for (phrase in phrases) {
            val clean = phrase.trim()
            if (clean.isEmpty()) continue
            val regex = Regex(
                "(?<![\\p{L}\\p{N}])" + Regex.escape(clean) + "(?![\\p{L}\\p{N}])",
                RegexOption.IGNORE_CASE,
            )
            if (texts.any { regex.containsMatchIn(it) }) return clean
        }
        return null
    }

    /** « go, je pars » ou une phrase par ligne -> liste de mots déclencheurs. */
    fun parsePhrases(raw: String): List<String> =
        raw.split(',', '\n', ';').map { it.trim() }.filter { it.isNotEmpty() }
}
