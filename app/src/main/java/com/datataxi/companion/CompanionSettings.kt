package com.datataxi.companion

import android.content.Context

/** Réglages de l'avatar flottant : adresse du relais (https), jeton, voix, masquage. */
object CompanionSettings {
    private const val PREFS = "datataxi_companion"
    private fun p(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun relayUrl(c: Context): String = p(c).getString("relay_url", "") ?: ""
    fun token(c: Context): String = SecretBox.decrypt(p(c).getString("token_enc", "") ?: "")
    fun speak(c: Context): Boolean = p(c).getBoolean("speak", true)
    fun maskData(c: Context): Boolean = p(c).getBoolean("mask", true)

    fun save(c: Context, url: String, token: String, speak: Boolean, mask: Boolean) {
        p(c).edit().putString("relay_url", url.trim()).putString("token_enc", SecretBox.encrypt(token.trim())).remove("token")
            .putBoolean("speak", speak).putBoolean("mask", mask).apply()
    }
}
