package com.example.EnglishTagalogBible

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator

class BiblicalAnimationBackgroundView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }
    
    private val strokePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    
    private var animProgress = 0f
    private var baseColor = Color.argb(180, 110, 71, 44)

    init {
        // Continuous smooth infinite loop cycle (15 seconds)
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 15000 
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                animProgress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    fun updateTheme(accentColor: Int, isDark: Boolean) {
        val alpha = if (isDark) 210 else 170
        baseColor = Color.argb(
            alpha,
            Color.red(accentColor),
            Color.green(accentColor),
            Color.blue(accentColor)
        )
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val widthF = width.toFloat()
        val heightF = height.toFloat()

        val centerX = widthF * 0.5f
        val centerY = heightF * 0.20f

        // Smooth sine-wave breathing scale and vertical float (100% continuous, zero hard resets)
        val pulse = 1f + 0.06f * kotlin.math.sin(animProgress * Math.PI * 2).toFloat()
        val floatOffsetY = 12f * kotlin.math.cos(animProgress * Math.PI * 2).toFloat()

        canvas.save()
        canvas.translate(0f, floatOffsetY)
        canvas.scale(pulse, pulse, centerX, centerY)

        // Draw the Sacred Radiant Cross with Pulsing Light Rays
        drawRadiantCross(canvas, centerX, centerY, animProgress)

        canvas.restore()
    }

    private fun drawRadiantCross(canvas: Canvas, x: Float, y: Float, progress: Float) {
        // Dynamic glow alpha using sine wave for butter-smooth fading
        val alphaMod = (140.0f + 60.0f * kotlin.math.sin(progress * Math.PI * 4)).toInt()
        
        paint.color = Color.argb(alphaMod, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor))
        strokePaint.color = paint.color

        // 1. Outer Radiating Glory Halo (Circles pulsing gently)
        val haloRadius = 45f + 10f * kotlin.math.sin(progress * Math.PI * 2).toFloat()
        val haloPaint = Paint(paint).apply { 
            style = Paint.Style.STROKE
            strokeWidth = 2f
            alpha = alphaMod / 3
        }
        canvas.drawCircle(x, y, haloRadius, haloPaint)

        // 2. Radiant Light Rays extending outward
        strokePaint.strokeWidth = 2.5f
        val rayLength = 35f + 8f * kotlin.math.cos(progress * Math.PI * 2).toFloat()
        for (i in 0..7) {
            val angle = i * (Math.PI / 4.0)
            val x1 = x + (30f * kotlin.math.cos(angle)).toFloat()
            val y1 = y + (30f * kotlin.math.sin(angle)).toFloat()
            val x2 = x + ((30f + rayLength) * kotlin.math.cos(angle)).toFloat()
            val y2 = y + ((30f + rayLength) * kotlin.math.sin(angle)).toFloat()
            canvas.drawLine(x1, y1, x2, y2, strokePaint)
        }

        // 3. The Central Cross
        paint.style = Paint.Style.FILL
        // Vertical beam
        canvas.drawRect(x - 6f, y - 50f, x + 6f, y + 50f, paint)
        // Horizontal beam
        canvas.drawRect(x - 30f, y - 20f, x + 30f, y - 10f, paint)
    }
}
