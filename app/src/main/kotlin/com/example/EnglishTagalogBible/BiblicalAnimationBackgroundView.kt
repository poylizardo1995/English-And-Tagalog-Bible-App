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
    private var silhouetteColor = Color.argb(120, 110, 71, 44)

    init {
        // Smooth continuous loop for ambient background motion
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 25000 // 25 seconds per full smooth cycle
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                animProgress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    // Boosted opacity so silhouettes are clearly visible and sharp against themes
    fun updateTheme(accentColor: Int, isDark: Boolean) {
        val alpha = if (isDark) 140 else 100
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

        // 1. Walking Robed Figure Silhouette (Moving left-to-right)
        val figX = (widthF * 0.1f + (shift * 0.7f)) % (widthF + 300f) - 150f
        val figY = heightF * 0.78f
        drawRobedFigure(canvas, figX, figY)

        // 2. Cross with Light Rays Motif (Floating / Center-Right)
        val crossX = (widthF * 0.75f - (shift * 0.4f)) % (widthF + 300f) - 150f
        val crossY = heightF * 0.22f
        drawCrossWithRays(canvas, crossX, crossY)

        // 3. Kneeling / Praying Silhouette (Moving right-to-left)
        val prayX = (widthF * 0.9f - (shift * 0.5f)) % (widthF + 300f) - 150f
        val prayY = heightF * 0.82f
        drawPrayingFigure(canvas, prayX, prayY)
    }

    private fun drawRobedFigure(canvas: Canvas, x: Float, y: Float) {
        // Blank rounded head (silhouette style)
        paint.style = Paint.Style.FILL
        canvas.drawCircle(x, y - 50f, 20f, paint)

        // Flowing Robe Body Path
        val robe = Path().apply {
            moveTo(x, y - 30f)
            lineTo(x - 30f, y + 55f)
            lineTo(x + 30f, y + 55f)
            close()
        }
        canvas.drawPath(robe, paint)

        // Robe sash / fold accent line
        strokePaint.strokeWidth = 3f
        canvas.drawLine(x - 8f, y - 15f, x + 12f, y + 25f, strokePaint)
    }

    private fun drawCrossWithRays(canvas: Canvas, x: Float, y: Float) {
        paint.style = Paint.Style.FILL
        // Vertical beam
        canvas.drawRect(x - 5f, y - 40f, x + 5f, y + 40f, paint)
        // Horizontal beam
        canvas.drawRect(x - 24f, y - 18f, x + 24f, y - 8f, paint)

        // Light rays around the cross
        strokePaint.strokeWidth = 2f
        canvas.drawLine(x - 38f, y - 38f, x - 28f, y - 28f, strokePaint)
        canvas.drawLine(x + 38f, y - 38f, x + 28f, y - 28f, strokePaint)
        canvas.drawLine(x - 38f, y + 22f, x - 28f, y + 15f, strokePaint)
        canvas.drawLine(x + 38f, y + 22f, x + 28f, y + 15f, strokePaint)
    }

    private fun drawPrayingFigure(canvas: Canvas, x: Float, y: Float) {
        paint.style = Paint.Style.FILL
        // Head
        canvas.drawCircle(x, y - 35f, 16f, paint)
        // Kneeling body
        val body = Path().apply {
            moveTo(x, y - 18f)
            lineTo(x - 20f, y + 30f)
            lineTo(x + 18f, y + 30f)
            close()
        }
        canvas.drawPath(body, paint)
    }
}
