package com.datataxi.companion

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
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

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        startForeground(NOTIF_ID, notification())
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return }
        addBubble()
    }

    override fun onDestroy() {
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
        var sx = 0; var sy = 0; var tx = 0f; var ty = 0f; var moved = false
        b.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { sx = lp.x; sy = lp.y; tx = e.rawX; ty = e.rawY; moved = false }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - tx).toInt(); val dy = (e.rawY - ty).toInt()
                    if (Math.abs(dx) > dp(6) || Math.abs(dy) > dp(6)) moved = true
                    lp.x = sx + dx; lp.y = sy + dy; wm.updateViewLayout(b, lp)
                }
                MotionEvent.ACTION_UP -> if (!moved) togglePanel()
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
        val row = LinearLayout(this).apply { addView(send); addView(close) }
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

    private fun ask(question: String) {
        val q = question.trim()
        if (q.isEmpty()) return
        answerView?.text = "…"
        bubble?.mood = MascotView.Mood.THINKING
        input?.setText("")
        val snap = CaptureStore.snapshot.value
        val mask = CompanionSettings.maskData(this)
        val lines = (snap?.lines?.map { it.text } ?: emptyList()).take(200).map { if (mask) Masking.mask(it) else it }
        val provider = RelayProvider(CompanionSettings.relayUrl(this), CompanionSettings.token(this))
        thread {
            val r = provider.ask(if (mask) Masking.mask(q) else q, snap?.packageName ?: "", lines)
            ui.post {
                val text = r.getOrElse { "Erreur : ${it.message}" }
                answerView?.text = text
                bubble?.mood = MascotView.Mood.SPEAKING
                ui.postDelayed({ bubble?.mood = MascotView.Mood.IDLE }, (text.length * 60L).coerceIn(1500L, 15000L))
                if (r.isSuccess && CompanionSettings.speak(this)) AlertPlayer.say(this, text)
            }
        }
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
        private const val CHANNEL = "companion"
        private const val NOTIF_ID = 42
        fun start(c: Context) {
            val i = Intent(c, CompanionService::class.java)
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i) else c.startService(i)
        }
        fun stop(c: Context) { c.stopService(Intent(c, CompanionService::class.java)) }
    }
}
