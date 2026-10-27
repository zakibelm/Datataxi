package com.datataxi.data

import android.content.Context
import com.datataxi.domain.AlertDetector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Réglages de l'alerte « c'est l'heure » : activée, mots déclencheurs, phrase prononcée. */
object AlertSettings {

    private const val PREFS = "datataxi_alert"
    const val DEFAULT_PHRASES = "go, je pars"
    const val DEFAULT_MESSAGE = "C'est l'heure"
    private const val NO_ALERT = "Aucune alerte pour l'instant."

    private val _lastAlert = MutableStateFlow(NO_ALERT)
    val lastAlert: StateFlow<String> = _lastAlert.asStateFlow()

    private fun prefs(c: Context) =
        c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun init(c: Context) {
        _lastAlert.value = prefs(c).getString("last_label", null) ?: NO_ALERT
    }

    fun enabled(c: Context): Boolean = prefs(c).getBoolean("enabled", true)

    fun setEnabled(c: Context, value: Boolean) {
        prefs(c).edit().putBoolean("enabled", value).apply()
    }

    fun phrasesRaw(c: Context): String = prefs(c).getString("phrases", DEFAULT_PHRASES).orEmpty()

    fun phrases(c: Context): List<String> = AlertDetector.parsePhrases(phrasesRaw(c))

    fun message(c: Context): String =
        prefs(c).getString("message", DEFAULT_MESSAGE).orEmpty().ifBlank { DEFAULT_MESSAGE }

    fun save(c: Context, phrases: String, message: String) {
        prefs(c).edit()
            .putString("phrases", phrases.ifBlank { DEFAULT_PHRASES })
            .putString("message", message.ifBlank { DEFAULT_MESSAGE })
            .apply()
    }

    /** Note l'heure et la cause de la dernière alerte, pour l'afficher dans l'écran de réglage. */
    fun recordAlert(c: Context, app: String, phrase: String) {
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CANADA).format(Date())
        val label = "Dernière alerte : $stamp (« $phrase » dans $app)"
        prefs(c).edit().putString("last_label", label).apply()
        _lastAlert.value = label
    }
}
