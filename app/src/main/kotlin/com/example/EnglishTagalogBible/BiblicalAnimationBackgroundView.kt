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
    private var silhouetteColor = Color.argb(100, 110, 71, 44)

    init {
        // Smooth continuous loop for ambient background motion
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 35000 // 35 seconds for a soothing, slow loop cycle
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                animProgress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    // Balanced opacity so silhouettes are clearly visible behind your translucent card
    fun updateTheme(accentColor: Int, isDark: Boolean) {
        val alpha = if (isDark) 95 else 80
        silhouetteColor = Color.argb(
            alpha,
            Color.red(accentColor),
            Color.green(accentColor),
            Color.blue(accentColor)
        )
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        paint.color = silhouetteColor
        strokePaint.color = silhouetteColor

        val widthF = width.toFloat()
        val heightF = height.toFloat()
        val shift = animProgress * widthF

    // 1. Radiant Cross (Upper background layer with gentle floating movement)
        val crossX = widthF * 0.5f + kotlin.math.sin(animProgress * Math.PI * 2).toFloat() * 35f
        val crossY = heightF * 0.18f
        drawCross(canvas, crossX, crossY)

        // 2. Walking Robed Figure (Mid-screen layer, smoothly gliding across)
        val figX = (widthF * 0.15f + (shift * 0.45f)) % (widthF + 250f) - 120f
        val figY = heightF * 0.48f
        drawRobedFigure(canvas, figX, figY)

        // 3. Kneeling / Praying Silhouette (Lower layer, gentle drift)
        val prayX = (widthF * 0.85f - (shift * 0.3f)) % (widthF + 250f) - 120f
        val prayY = heightF * 0.72f
        drawPrayingFigure(canvas, prayX, prayY)
    }

    private fun drawRobedFigure(canvas: Canvas, x: Float, y: Float) {
        // Head
        paint.style = Paint.Style.FILL
        canvas.drawCircle(x, y - 50f, 20f, paint)

        // Robe Body
        val robe = Path().apply {
            moveTo(x, y - 30f)
            lineTo(x - 30f, y + 50f)
            lineTo(x + 30f, y + 50f)
            close()
        }
        canvas.drawPath(robe, paint)

        // Inner sash fold line
        strokePaint.strokeWidth = 2.5f
        canvas.drawLine(x - 7f, y - 12f, x + 12f, y + 22f, strokePaint)
    }

    private fun drawCross(canvas: Canvas, x: Float, y: Float) {
        paint.style = Paint.Style.FILL
        // Vertical beam
        canvas.drawRect(x - 6f, y - 50f, x + 6f, y + 50f, paint)
        // Horizontal beam
        canvas.drawRect(x - 28f, y - 20f, x + 28f, y - 10f, paint)

        // Radiating light aura dots
        val auraPaint = Paint(paint).apply { alpha = (Color.alpha(silhouetteColor) * 0.5f).toInt() }
        canvas.drawCircle(x, y - 65f, 4f, auraPaint)
        canvas.drawCircle(x - 42f, y - 15f, 4f, auraPaint)
        canvas.drawCircle(x + 42f, y - 15f, 4f, auraPaint)
    }

    private fun drawPrayingFigure(canvas: Canvas, x: Float, y: Float) {
        paint.style = Paint.Style.FILL
        // Head
        canvas.drawCircle(x, y - 35f, 16f, paint)
        // Kneeling body
        val body = Path().apply {
            moveTo(x, y - 18f)
            lineTo(x - 20f, y + 28f)
            lineTo(x + 18f, y + 28f)
            close()
        }
        canvas.drawPath(body, paint)
    }
}
