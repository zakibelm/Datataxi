package com.datataxi.companion

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/** Mascotte originale de DataTaxi : une petite boule jaune taxi animée (respire, cligne, réfléchit, parle). */
class MascotView(context: Context) : View(context) {

    enum class Mood { IDLE, THINKING, SPEAKING }

    var mood: Mood = Mood.IDLE

    private var t = 0f // secondes
    private val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FFC107") }
    private val shade = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FFA000") }
    private val dark = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#263238") }
    private val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val mouth = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#263238"); style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
    }
    private val oval = RectF()

    private val anim = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1000; repeatCount = ValueAnimator.INFINITE; interpolator = LinearInterpolator()
        addUpdateListener { t += 1f / 60f; invalidate() }
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); anim.start() }
    override fun onDetachedFromWindow() { anim.cancel(); super.onDetachedFromWindow() }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val bob = sin(t * 2f * PI.toFloat() * 0.6f) * h * 0.03f
        val squash = 1f + sin(t * 2f * PI.toFloat() * 0.6f) * 0.03f
        val r = minOf(w, h) * 0.42f
        val cx = w / 2f; val cy = h / 2f + bob

        // ombre
        c.drawOval(cx - r * 0.7f, h * 0.9f, cx + r * 0.7f, h * 0.97f, shade)
        // corps
        oval.set(cx - r * squash, cy - r / squash, cx + r * squash, cy + r / squash)
        c.drawOval(oval, body)
        // enseigne de taxi sur la tête
        c.drawRoundRect(cx - r * 0.35f, cy - r * 1.15f, cx + r * 0.35f, cy - r * 0.85f, 6f, 6f, dark)
        c.drawRect(cx - r * 0.25f, cy - r * 1.08f, cx + r * 0.25f, cy - r * 0.92f, white)

        // yeux : clignement toutes les ~3,5 s
        val phase = t % 3.5f
        val blink = if (phase > 3.35f) 0.1f else 1f
        val er = r * 0.17f
        val lookX = if (mood == Mood.THINKING) r * 0.1f else sin(t * 0.7f) * r * 0.06f
        val lookY = if (mood == Mood.THINKING) -r * 0.12f else 0f
        for (dx in floatArrayOf(-r * 0.38f, r * 0.38f)) {
            oval.set(cx + dx - er, cy - r * 0.15f - er * blink, cx + dx + er, cy - r * 0.15f + er * blink)
            c.drawOval(oval, white)
            oval.set(cx + dx + lookX - er * 0.55f, cy - r * 0.15f + lookY - er * 0.55f * blink,
                cx + dx + lookX + er * 0.55f, cy - r * 0.15f + lookY + er * 0.55f * blink)
            c.drawOval(oval, dark)
        }

        // bouche
        mouth.strokeWidth = r * 0.07f
        when (mood) {
            Mood.SPEAKING -> {
                val open = abs(sin(t * 12f)) * r * 0.22f + r * 0.04f
                oval.set(cx - r * 0.17f, cy + r * 0.3f, cx + r * 0.17f, cy + r * 0.3f + open * 2f)
                c.drawOval(oval, dark)
            }
            Mood.THINKING -> {
                c.drawLine(cx - r * 0.12f, cy + r * 0.42f, cx + r * 0.12f, cy + r * 0.42f, mouth)
                // trois points qui s'allument
                val on = ((t * 3f).toInt() % 3)
                for (i in 0..2) {
                    val a = if (i <= on) 255 else 70
                    white.alpha = a
                    c.drawCircle(cx + r * 0.75f + i * r * 0.22f, cy - r * 0.85f, r * 0.07f, white)
                }
                white.alpha = 255
            }
            Mood.IDLE -> {
                oval.set(cx - r * 0.22f, cy + r * 0.15f, cx + r * 0.22f, cy + r * 0.5f)
                c.drawArc(oval, 20f, 140f, false, mouth)
            }
        }
    }
}
