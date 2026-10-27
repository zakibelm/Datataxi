package com.datataxi.companion

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.datataxi.data.CaptureStore
import com.datataxi.domain.Masking
import com.datataxi.service.AlertPlayer
import kotlin.concurrent.thread

/** Avatar flottant : une bulle déplaçable ; un toucher ouvre un mini-chat qui voit le texte de l'écran cible. */
class CompanionService : Service() {

    private lateinit var wm: WindowManager
    private val ui = Handler(Looper.getMainLooper())
    private var bubble: MascotView? = null
    private var panel: LinearLayout? = null
    private var answerView: TextView? = null
    private var input: EditText? = null
    private var tapCount = 0
    private var lastTap = 0L
    private var emoteIndex = 0

    /** Touchers rapides : humeur joueuse + petit son, comme les émotes de Coucou. */
    private fun emote() {
        val moods = listOf(
            MascotView.Mood.LOVE to android.media.ToneGenerator.TONE_PROP_ACK,
            MascotView.Mood.EXCITED to android.media.ToneGenerator.TONE_PROP_BEEP2,
            MascotView.Mood.JOY to android.media.ToneGenerator.TONE_PROP_PROMPT,
            MascotView.Mood.SURPRISED to android.media.ToneGenerator.TONE_PROP_NACK,
        )
        val (mood, tone) = moods[emoteIndex++ % moods.size]
        bubble?.setMood(mood, 2500L)
        runCatching {
            val tg = android.media.ToneGenerator(android.media.AudioManager.STREAM_MUSIC, 60)
            tg.startTone(tone, 250)
            ui.postDelayed({ tg.release() }, 600L)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        if (Build.VERSION.SDK_INT >= 34) {
            var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
                type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            startForeground(NOTIF_ID, notification(), type)
        } else startForeground(NOTIF_ID, notification())
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return }
        addBubble()
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        stopListening()
        bubble?.let { runCatching { wm.removeView(it) } }
        panel?.let { runCatching { wm.removeView(it) } }
        super.onDestroy()
    }

    private fun overlayType() =
        if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun addBubble() {
        val b = MascotView(this)
        val lp = WindowManager.LayoutParams(
            dp(84), dp(84), overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START; x = dp(8); y = dp(160) }
        var sx = 0; var sy = 0; var tx = 0f; var ty = 0f; var moved = false; var longPressed = false
        val longPress = Runnable { longPressed = true; togglePanel() }
        b.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { sx = lp.x; sy = lp.y; tx = e.rawX; ty = e.rawY; moved = false; longPressed = false; ui.postDelayed(longPress, 650L) }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - tx).toInt(); val dy = (e.rawY - ty).toInt()
                    if (Math.abs(dx) > dp(6) || Math.abs(dy) > dp(6)) { moved = true; ui.removeCallbacks(longPress) }
                    lp.x = sx + dx; lp.y = sy + dy; wm.updateViewLayout(b, lp)
                }
                MotionEvent.ACTION_UP -> { ui.removeCallbacks(longPress); if (!moved && !longPressed) {
                    val now = System.currentTimeMillis()
                    tapCount = if (now - lastTap < 700) tapCount + 1 else 1
                    lastTap = now
                    if (tapCount >= 3) emote() else onBubbleTap()
                } }
            }
            true
        }
        wm.addView(b, lp); bubble = b
    }

    private fun togglePanel() {
        if (panel != null) { closePanel(); return }
        val answer = TextView(this).apply { setTextColor(Color.WHITE); textSize = 16f; text = "Pose-moi une question sur ce que tu vois à l'écran." }
        val scroll = ScrollView(this).apply { addView(answer) }
        val field = EditText(this).apply {
            hint = "Ta question…"; setTextColor(Color.WHITE); setHintTextColor(Color.LTGRAY); maxLines = 3
        }
        val send = Button(this).apply { text = "Envoyer"; setOnClickListener { ask(field.text.toString()) } }
        val close = Button(this).apply { text = "Fermer"; setOnClickListener { closePanel() } }
        val mic = Button(this).apply { text = "🎤 Parler"; setOnClickListener { if (listening) stopListening() else startListening() } }
        val row = LinearLayout(this).apply { addView(mic); addView(send); addView(close) }
        val p = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(12), dp(12), dp(12))
            background = GradientDrawable().apply { cornerRadius = dp(16).toFloat(); setColor(Color.parseColor("#F2263238")) }
            addView(scroll, LinearLayout.LayoutParams(-1, dp(180)))
            addView(field); addView(row)
        }
        val lp = WindowManager.LayoutParams(
            dp(380), WindowManager.LayoutParams.WRAP_CONTENT, overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.START; x = dp(8); y = dp(8)
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
        wm.addView(p, lp); panel = p; answerView = answer; input = field
    }

    private fun closePanel() {
        panel?.let { runCatching { wm.removeView(it) } }
        panel = null; answerView = null; input = null
    }

    private fun ask(question: String, spoken: Boolean = false) {
        val q = question.trim()
        if (q.isEmpty()) return
        answerView?.text = "…"
        bubble?.setMood(MascotView.Mood.THINKING)
        input?.setText("")
        val snap = CaptureStore.snapshot.value
        val mask = CompanionSettings.maskData(this)
        val lines = (snap?.lines?.map { it.text } ?: emptyList()).take(200).map { if (mask) Masking.mask(it) else it }
        val provider: ChatProvider = if (CompanionSettings.provider(this) == "relay")
            RelayProvider(CompanionSettings.relayUrl(this), CompanionSettings.token(this))
        else OpenAiCompatProvider(CompanionSettings.relayUrl(this), CompanionSettings.token(this), CompanionSettings.model(this))
        thread {
            val r = provider.ask(if (mask) Masking.mask(q) else q, snap?.packageName ?: "", lines)
            ui.post {
                val text = r.getOrElse { "Erreur : ${it.message}" }
                answerView?.text = text
                if (!r.isSuccess) { bubble?.setMood(MascotView.Mood.SAD, 6000L); return@post }
                val voiceMode = CompanionSettings.voiceMode(this)
                if (spoken || CompanionSettings.speak(this)) {
                    bubble?.setMood(MascotView.Mood.SPEAKING, 30_000L)
                    AlertPlayer.say(this, text) {
                        bubble?.setMood(MascotView.Mood.JOY, 1500L)
                        // Mains libres : après la réponse, il se remet à l'écoute pour la suite de la conversation.
                        if (spoken && voiceMode) ui.postDelayed({ startListening() }, 600L)
                    }
                } else {
                    bubble?.setMood(MascotView.Mood.SPEAKING, (text.length * 60L).coerceIn(1500L, 15000L))
                }
            }
        }
    }

    // ---------- Voix ----------
    private var recognizer: SpeechRecognizer? = null
    private var listening = false

    private fun onBubbleTap() {
        if (CompanionSettings.voiceMode(this)) {
            if (listening) stopListening() else startListening()
        } else {
            bubble?.setMood(MascotView.Mood.SURPRISED, 600L); togglePanel()
        }
    }

    private fun say(msg: String) { answerView?.text = msg }

    private fun startListening() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            say("Micro non autorisé : ouvre DataTaxi, carte « Avatar flottant », et autorise le micro.")
            bubble?.setMood(MascotView.Mood.SAD, 4000L); return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            say("Reconnaissance vocale absente sur cette tablette (installe/active l'application Google).")
            bubble?.setMood(MascotView.Mood.SAD, 4000L); return
        }
        stopListening()
        val rec = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer = rec
        rec.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { runCatching { android.media.ToneGenerator(android.media.AudioManager.STREAM_MUSIC, 60).startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 120) } }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
            override fun onPartialResults(partialResults: Bundle?) {
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { say(it) }
            }
            override fun onResults(results: Bundle?) {
                listening = false
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (text.isNullOrBlank()) bubble?.setMood(MascotView.Mood.IDLE) else ask(text, spoken = true)
            }
            override fun onError(error: Int) {
                listening = false
                // Silence ou rien compris : fin de la conversation, sans bruit.
                if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                    bubble?.setMood(MascotView.Mood.IDLE)
                } else {
                    say("Erreur micro ($error).")
                    bubble?.setMood(MascotView.Mood.SAD, 3000L)
                }
            }
        })
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fr-CA")
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        listening = true
        bubble?.setMood(MascotView.Mood.LISTENING, 0L)
        rec.startListening(i)
    }

    private fun stopListening() {
        listening = false
        recognizer?.let { runCatching { it.cancel(); it.destroy() } }
        recognizer = null
        if (bubble?.mood == MascotView.Mood.LISTENING) bubble?.setMood(MascotView.Mood.IDLE)
    }

    private fun notification(): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Avatar DataTaxi", NotificationManager.IMPORTANCE_LOW)
        )
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL) else @Suppress("DEPRECATION") Notification.Builder(this)
        return b.setContentTitle("Avatar DataTaxi actif").setSmallIcon(android.R.drawable.ic_dialog_info).build()
    }

    companion object {
        private var instance: CompanionService? = null

        /** Fait réagir la mascotte depuis ailleurs dans l'app (ex. alerte « c'est l'heure »). */
        fun react(mood: MascotView.Mood, forMs: Long) {
            val svc = instance ?: return
            svc.ui.post { svc.bubble?.setMood(mood, forMs) }
        }

        private const val CHANNEL = "companion"
        private const val NOTIF_ID = 42
        fun start(c: Context) {
            val i = Intent(c, CompanionService::class.java)
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i) else c.startService(i)
        }
        fun stop(c: Context) { c.stopService(Intent(c, CompanionService::class.java)) }
    }
}
