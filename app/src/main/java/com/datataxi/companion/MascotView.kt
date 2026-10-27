package com.datataxi.companion

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Mascotte originale de DataTaxi : un vrai personnage animé qui exprime des émotions.
 * Chaque humeur change les yeux, la bouche, les accessoires et la façon de bouger.
 */
class MascotView(context: Context) : View(context) {

    enum class Mood { IDLE, THINKING, SPEAKING, JOY, EXCITED, SURPRISED, SAD, SLEEPY, WORRIED, LOVE, LISTENING }

    var mood: Mood = Mood.IDLE
        private set

    private val handler = Handler(Looper.getMainLooper())
    private val toIdle = Runnable { mood = Mood.IDLE; scheduleSleep() }
    private val toSleep = Runnable { mood = Mood.SLEEPY }

    /** Prend une humeur ; si [forMs] est donné, revient au repos ensuite. */
    fun setMood(m: Mood, forMs: Long = 0L) {
        handler.removeCallbacks(toIdle); handler.removeCallbacks(toSleep)
        mood = m
        if (forMs > 0) handler.postDelayed(toIdle, forMs) else if (m == Mood.IDLE) scheduleSleep()
    }

    /** Sans activité pendant 90 s, la mascotte s'endort. */
    private fun scheduleSleep() {
        handler.removeCallbacks(toSleep); handler.postDelayed(toSleep, 90_000L)
    }

    private var t = 0f
    private val yellow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FFC107") }
    private val shade = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#33000000") }
    private val dark = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#263238") }
    private val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val blush = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FF8A80"); alpha = 150 }
    private val heart = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F06292") }
    private val star = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FFF59D") }
    private val tear = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#81D4FA") }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#263238"); style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#90A4AE"); isFakeBoldText = true }
    private val oval = RectF()
    private val path = Path()

    private val anim = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1000; repeatCount = ValueAnimator.INFINITE; interpolator = LinearInterpolator()
        addUpdateListener { t += 1f / 60f; invalidate() }
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); anim.start(); scheduleSleep() }
    override fun onDetachedFromWindow() {
        anim.cancel(); handler.removeCallbacksAndMessages(null); super.onDetachedFromWindow()
    }

    private fun heartAt(c: Canvas, x: Float, y: Float, s: Float, p: Paint) {
        path.reset()
        path.moveTo(x, y + s * 0.9f)
        path.cubicTo(x - s * 1.6f, y - s * 0.2f, x - s * 0.6f, y - s * 1.3f, x, y - s * 0.4f)
        path.cubicTo(x + s * 0.6f, y - s * 1.3f, x + s * 1.6f, y - s * 0.2f, x, y + s * 0.9f)
        c.drawPath(path, p)
    }

    private fun sparkle(c: Canvas, x: Float, y: Float, s: Float) {
        path.reset()
        path.moveTo(x, y - s); path.lineTo(x + s * 0.25f, y - s * 0.25f); path.lineTo(x + s, y)
        path.lineTo(x + s * 0.25f, y + s * 0.25f); path.lineTo(x, y + s); path.lineTo(x - s * 0.25f, y + s * 0.25f)
        path.lineTo(x - s, y); path.lineTo(x - s * 0.25f, y - s * 0.25f); path.close()
        c.drawPath(path, star)
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val r = minOf(w, h) * 0.38f
        val two = 2f * PI.toFloat()

        // --- mouvement du corps selon l'humeur
        var dx = 0f; var dy = 0f; var squash = 1f
        val breathe = sin(t * two * 0.6f)
        when (mood) {
            Mood.EXCITED -> { dy = -abs(sin(t * two * 1.6f)) * h * 0.14f; squash = 1f + cos(t * two * 1.6f) * 0.05f }
            Mood.JOY -> { dy = -abs(sin(t * two * 1.0f)) * h * 0.09f; dx = sin(t * two * 0.5f) * w * 0.02f }
            Mood.SURPRISED -> { dy = -h * 0.06f; squash = 0.95f }
            Mood.SAD -> { dy = h * 0.03f; squash = 1.04f + breathe * 0.01f }
            Mood.SLEEPY -> { squash = 1f + breathe * 0.04f; dy = h * 0.02f }
            Mood.WORRIED -> { dx = sin(t * two * 4f) * w * 0.025f }
            Mood.LOVE -> { dx = sin(t * two * 0.8f) * w * 0.03f; dy = breathe * h * 0.02f }
            else -> {
                val hopP = (t % 4f) / 0.6f
                dy = (if (hopP < 1f) -abs(sin(hopP * PI.toFloat())) * h * 0.10f else 0f) + breathe * h * 0.03f
                squash = 1f + breathe * 0.03f
            }
        }
        val cx = w / 2f + dx; val cy = h / 2f + dy

        // ombre + corps + enseigne de taxi
        c.drawOval(w / 2f - r * 0.7f, h * 0.9f, w / 2f + r * 0.7f, h * 0.96f, shade)
        oval.set(cx - r * squash, cy - r / squash, cx + r * squash, cy + r / squash)
        c.drawOval(oval, yellow)
        c.drawRoundRect(cx - r * 0.35f, cy - r * 1.15f, cx + r * 0.35f, cy - r * 0.85f, 6f, 6f, dark)
        c.drawRect(cx - r * 0.25f, cy - r * 1.08f, cx + r * 0.25f, cy - r * 0.92f, white)

        val eyeY = cy - r * 0.05f
        val eyeDx = r * 0.38f
        val blink = if ((t % 3.5f) > 3.35f) 0.1f else 1f
        line.strokeWidth = r * 0.07f
        var er = r * 0.24f

        // --- yeux
        when (mood) {
            Mood.JOY, Mood.EXCITED -> {
                // yeux fermés en « ^ ^ » (joie), avec étincelles si enthousiaste
                for (s in floatArrayOf(-1f, 1f)) {
                    oval.set(cx + s * eyeDx - er, eyeY - er * 0.6f, cx + s * eyeDx + er, eyeY + er * 0.9f)
                    c.drawArc(oval, 200f, 140f, false, line)
                }
            }
            Mood.LOVE -> for (s in floatArrayOf(-1f, 1f)) {
                val beat = 1f + sin(t * two * 1.5f) * 0.12f
                heartAt(c, cx + s * eyeDx, eyeY, er * 0.8f * beat, heart)
            }
            Mood.SLEEPY -> for (s in floatArrayOf(-1f, 1f)) {
                oval.set(cx + s * eyeDx - er, eyeY - er * 0.5f, cx + s * eyeDx + er, eyeY + er * 0.7f)
                c.drawArc(oval, 20f, 140f, false, line) // paupières fermées
            }
            else -> {
                if (mood == Mood.SURPRISED) er *= 1.2f
                val lookX = when (mood) {
                    Mood.THINKING -> r * 0.1f
                    Mood.SAD -> 0f
                    else -> sin(t * 0.7f) * r * 0.06f
                }
                val lookY = when (mood) { Mood.THINKING -> -r * 0.12f; Mood.SAD -> r * 0.1f; else -> 0f }
                val pupil = if (mood == Mood.SURPRISED) 0.35f else 0.55f
                for (s in floatArrayOf(-1f, 1f)) {
                    val ex = cx + s * eyeDx
                    oval.set(ex - er, eyeY - er * blink, ex + er, eyeY + er * blink)
                    c.drawOval(oval, white)
                    oval.set(ex + lookX - er * pupil, eyeY + lookY - er * pupil * blink,
                        ex + lookX + er * pupil, eyeY + lookY + er * pupil * blink)
                    c.drawOval(oval, dark)
                    if (blink > 0.5f) c.drawCircle(ex + lookX - er * 0.2f, eyeY + lookY - er * 0.25f, er * 0.2f, white)
                    // sourcils
                    if (mood == Mood.WORRIED || mood == Mood.SAD) {
                        val tilt = er * 0.5f * s
                        c.drawLine(ex - er, eyeY - er * 1.3f + tilt, ex + er, eyeY - er * 1.3f - tilt, line)
                    }
                }
            }
        }

        // --- joues
        if (mood != Mood.SAD && mood != Mood.WORRIED) for (s in floatArrayOf(-1f, 1f)) {
            oval.set(cx + s * eyeDx * 1.35f - r * 0.12f, cy + r * 0.28f, cx + s * eyeDx * 1.35f + r * 0.12f, cy + r * 0.42f)
            c.drawOval(oval, blush)
        }

        // --- bouche
        when (mood) {
            Mood.SPEAKING -> {
                val open = abs(sin(t * 12f)) * r * 0.22f + r * 0.04f
                oval.set(cx - r * 0.17f, cy + r * 0.28f, cx + r * 0.17f, cy + r * 0.28f + open * 2f)
                c.drawOval(oval, dark)
            }
            Mood.JOY, Mood.EXCITED -> {
                val big = if (mood == Mood.EXCITED) 0.3f else 0.22f
                oval.set(cx - r * big, cy + r * 0.12f, cx + r * big, cy + r * 0.12f + r * big * 2f)
                path.reset(); path.addArc(oval, 0f, 180f); path.close(); c.drawPath(path, dark)
            }
            Mood.THINKING, Mood.LISTENING -> c.drawCircle(cx, cy + r * 0.42f, r * 0.05f, dark)
            Mood.SURPRISED -> { oval.set(cx - r * 0.1f, cy + r * 0.3f, cx + r * 0.1f, cy + r * 0.58f); c.drawOval(oval, dark) }
            Mood.SAD -> { oval.set(cx - r * 0.13f, cy + r * 0.38f, cx + r * 0.13f, cy + r * 0.6f); c.drawArc(oval, 200f, 140f, false, line) }
            Mood.WORRIED -> {
                path.reset(); path.moveTo(cx - r * 0.2f, cy + r * 0.45f)
                for (i in 1..4) path.lineTo(cx - r * 0.2f + i * r * 0.1f, cy + r * (if (i % 2 == 0) 0.45f else 0.5f))
                c.drawPath(path, line.apply { style = Paint.Style.STROKE })
            }
            Mood.SLEEPY -> { val o = r * (0.06f + 0.03f * sin(t * two * 0.4f)); c.drawCircle(cx, cy + r * 0.42f, o, dark) }
            else -> { // IDLE, LOVE : petit sourire
                oval.set(cx - r * 0.13f, cy + r * 0.22f, cx + r * 0.13f, cy + r * 0.42f)
                c.drawArc(oval, 20f, 140f, false, line)
            }
        }

        // --- accessoires d'humeur
        when (mood) {
            Mood.LISTENING -> for (i in 0..1) {
                val p = (t * 0.9f + i * 0.5f) % 1f
                line.alpha = (200 * (1f - p)).toInt()
                c.drawCircle(cx, cy, r * (1.05f + 0.35f * p), line)
                line.alpha = 255
            }
            Mood.THINKING -> for (i in 0..2) {
                white.alpha = if (i <= (t * 3f).toInt() % 3) 255 else 70
                c.drawCircle(cx + r * 0.75f + i * r * 0.22f, cy - r * 0.85f, r * 0.07f, white)
                white.alpha = 255
            }
            Mood.EXCITED -> for (i in 0..3) {
                val a = t * 2f + i * PI.toFloat() / 2f
                sparkle(c, cx + cos(a) * r * 1.2f, cy - r * 0.2f + sin(a) * r * 1.0f, r * (0.14f + 0.04f * sin(t * 8f + i)))
            }
            Mood.JOY, Mood.LOVE -> for (i in 0..2) {
                val p = ((t * 0.5f + i / 3f) % 1f)
                heart.alpha = (255 * (1f - p)).toInt()
                heartAt(c, cx + (i - 1) * r * 0.7f + sin(p * 6f) * r * 0.1f, cy - r * 0.9f - p * r * 0.9f, r * 0.12f, heart)
                heart.alpha = 255
            }
            Mood.SAD -> {
                val p = (t * 0.8f) % 1f
                c.drawCircle(cx - eyeDx, eyeY + er + p * r * 0.5f, r * 0.06f, tear)
            }
            Mood.SLEEPY -> for (i in 0..2) {
                val p = ((t * 0.3f + i / 3f) % 1f)
                text.alpha = (255 * (1f - p)).toInt(); text.textSize = r * (0.25f + 0.15f * i)
                c.drawText("z", cx + r * 0.7f + p * r * 0.4f, cy - r * 0.5f - p * r * 0.8f, text)
            }
            Mood.WORRIED -> {
                c.drawCircle(cx + r * 0.95f, cy - r * 0.5f, r * 0.07f, tear)
            }
            else -> Unit
        }
    }
}
