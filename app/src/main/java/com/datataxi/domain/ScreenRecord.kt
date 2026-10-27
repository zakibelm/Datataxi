package com.datataxi.domain

import java.security.MessageDigest

/**
 * Photo textuelle d'un écran de l'application cible, à un instant donné.
 * [screenId] identifie l'écran (classe de la fenêtre, ou « inconnu » si Android ne la donne pas).
 */
data class ScreenRecord(
    val capturedAtMillis: Long,
    val screenId: String,
    val lines: List<String>,
) {
    /**
     * Empreinte du contenu : sert à ne pas enregistrer deux fois le même écran.
     * Les heures (« 14:47:16 ») sont retirées pour qu'une horloge qui défile ne crée pas
     * un nouvel enregistrement à chaque seconde. Le texte conservé, lui, reste intact.
     */
    val fingerprint: String
        get() {
            val normalized = screenId + "\n" + lines.joinToString("\n") { TIME.replace(it, "") }
            return MessageDigest.getInstance("SHA-1")
                .digest(normalized.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
                .take(16)
        }

    private companion object {
        val TIME = Regex("\\d{1,2}:\\d{2}(:\\d{2})?")
    }
}
