package com.datataxi.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Joue le signal sonore puis dit la phrase à voix haute. Tout passe par le volume « alarme »
 * de la tablette, pour que l'alerte s'entende même si le volume média est baissé.
 */
object AlertPlayer {

    private const val SPEAK_DELAY_MS = 750L

    private val handler = Handler(Looper.getMainLooper())

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var pendingMessage: String? = null

    private val _status = MutableStateFlow("")
    val status: StateFlow<String> = _status.asStateFlow()

    /** À appeler depuis le fil principal (service d'accessibilité ou bouton de test). */
    fun play(context: Context, message: String) {
        beep()
        speak(context.applicationContext, message)
    }

    private fun beep() {
        try {
            val tone = ToneGenerator(AudioManager.STREAM_ALARM, ToneGenerator.MAX_VOLUME)
            tone.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 700)
            handler.postDelayed({ tone.release() }, 1200L)
        } catch (_: RuntimeException) {
            _status.value = "Le signal sonore n'a pas pu démarrer."
        }
    }

    private fun speak(app: Context, message: String) {
        if (ttsReady) {
            speakNow(message)
            return
        }
        pendingMessage = message
        if (tts != null) return // initialisation déjà en cours : la phrase sera dite à la fin
        tts = TextToSpeech(app) { status ->
            val engine = tts
            if (status != TextToSpeech.SUCCESS || engine == null) {
                _status.value = "Voix indisponible : le signal sonore seul est joué."
                tts = null
                return@TextToSpeech
            }
            engine.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            var language = engine.setLanguage(Locale("fr", "CA"))
            if (language == TextToSpeech.LANG_MISSING_DATA || language == TextToSpeech.LANG_NOT_SUPPORTED) {
                language = engine.setLanguage(Locale.FRENCH)
            }
            if (language == TextToSpeech.LANG_MISSING_DATA || language == TextToSpeech.LANG_NOT_SUPPORTED) {
                _status.value = "Voix française absente : installe-la dans les réglages de synthèse vocale."
            } else {
                _status.value = ""
            }
            ttsReady = true
            pendingMessage?.let { speakNow(it) }
            pendingMessage = null
        }
    }

    private fun speakNow(message: String) {
        val engine = tts ?: return
        handler.postDelayed({
            val params = Bundle().apply {
                putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_ALARM)
            }
            engine.speak(message, TextToSpeech.QUEUE_FLUSH, params, "datataxi_alert")
        }, SPEAK_DELAY_MS)
    }
}
