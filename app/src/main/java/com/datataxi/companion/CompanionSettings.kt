package com.datataxi.companion

import android.content.Context

/** Réglages de l'avatar flottant : adresse du relais (https), jeton, voix, masquage. */
object CompanionSettings {
    private const val PREFS = "datataxi_companion"
    /** OmniRoute de Zaki sur le VPS (accès privé Tailscale, voir zakibelm/zakomni). */
    private const val DEFAULT_URL = "https://srv679767.taila740dc.ts.net:8443"
    private const val DEFAULT_MODEL = "zaki-sensible"
    private fun p(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun relayUrl(c: Context): String = p(c).getString("relay_url", DEFAULT_URL) ?: DEFAULT_URL
    fun token(c: Context): String = SecretBox.decrypt(p(c).getString("token_enc", "") ?: "")
    fun speak(c: Context): Boolean = p(c).getBoolean("speak", true)
    fun maskData(c: Context): Boolean = p(c).getBoolean("mask", true)

    /** "relay" = Claude Code sur le VPS ; "omniroute" = serveur compatible OpenAI (OmniRoute). */
    fun provider(c: Context): String = p(c).getString("provider", "omniroute") ?: "omniroute"
    fun model(c: Context): String = p(c).getString("model", DEFAULT_MODEL) ?: DEFAULT_MODEL
    fun saveProvider(c: Context, provider: String, model: String) {
        p(c).edit().putString("provider", provider).putString("model", model.trim()).apply()
    }

    fun voiceMode(c: Context): Boolean = p(c).getBoolean("voice_mode", true)
    fun setVoiceMode(c: Context, on: Boolean) { p(c).edit().putBoolean("voice_mode", on).apply() }

    fun save(c: Context, url: String, token: String, speak: Boolean, mask: Boolean) {
        p(c).edit().putString("relay_url", url.trim()).putString("token_enc", SecretBox.encrypt(token.trim())).remove("token")
            .putBoolean("speak", speak).putBoolean("mask", mask).apply()
    }
}
