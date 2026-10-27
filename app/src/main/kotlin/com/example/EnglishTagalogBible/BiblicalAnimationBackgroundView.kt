package com.example.EnglishTagalogBible

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.animation.ValueAnimator
import android.view.animation.LinearInterpolator

class BiblicalAnimationBackgroundView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var accentColor: Int = Color.parseColor("#6E472C")
    private var isDarkTheme: Boolean = false
    
    // Animation pulse factor (0.0 to 1.0) for smooth breathing glow
    private var pulseProgress = 0f
    private val animator: ValueAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 3500 // 3.5 seconds per breathing cycle
        repeatMode = ValueAnimator.REVERSE
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            pulseProgress = it.animatedValue as Float
            invalidate()
        }
    }

    init {
        animator.start()
    }

    /**
     * Updates the theme accent color and dark mode state dynamically.
     */
    fun updateTheme(accentColor: Int, isDark: Boolean) {
        this.accentColor = accentColor
        this.isDarkTheme = isDark
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val width = width.toFloat()
        val height = height.toFloat()
        if (width <= 0f || height <= 0f) return

        val centerX = width / 2f
        val centerY = height / 2f - 40f // Centered nicely, allowing room for the book at the base

        // Calculate dynamic breathing alpha based on animation
        val alphaMultiplier = if (isDarkTheme) 0.18f + (pulseProgress * 0.12f) else 0.12f + (pulseProgress * 0.08f)
        val baseAlpha = (alphaMultiplier * 255).toInt().coerceIn(0, 255)

        val paint = Paint().apply {
            isAntiAlias = true
        }

        // ==========================================
        // 1. DRAW GLOWING CROSS (Centered & Huge)
        // ==========================================
        val crossWidth = 140f
        val crossHeight = 280f
        val barThickness = 36f

        // Outer Glow Layers
        for (i in 3 downTo 1) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = barThickness + (i * 18f)
            paint.strokeCap = Paint.Cap.ROUND
            paint.color = accentColor
            paint.alpha = (baseAlpha / (i + 2)).coerceIn(0, 255)

            // Vertical Beam
            canvas.drawLine(centerX, centerY - crossHeight / 2f, centerX, centerY + crossHeight / 2f, paint)
            // Horizontal Beam
            canvas.drawLine(centerX - crossWidth / 2f, centerY - crossHeight / 6f, centerX + crossWidth / 2f, centerY - crossHeight / 6f, paint)
        }

        // Solid Core Cross
        paint.style = Paint.Style.FILL
        paint.color = accentColor
        paint.alpha = (baseAlpha * 1.8f).toInt().coerceIn(0, 255)

        val verticalRect = RectF(
            centerX - barThickness / 2f,
            centerY - crossHeight / 2f,
            centerX + barThickness / 2f,
            centerY + crossHeight / 2f
        )
        val horizontalRect = RectF(
            centerX - crossWidth / 2f,
            centerY - crossHeight / 6f - barThickness / 2f,
            centerX + crossWidth / 2f,
            centerY - crossHeight / 6f + barThickness / 2f
        )

        canvas.drawRoundRect(verticalRect, 16f, 16f, paint)
        canvas.drawRoundRect(horizontalRect, 16f, 16f, paint)

        // ==========================================
        // 2. DRAW OPEN BIBLE BOOK AT THE BASE
        // ==========================================
        val bookCenterY = centerY + crossHeight / 2f + 45f
        val bookWidth = 220f
        val bookHeight = 75f

        // Book Glow Aura
        paint.style = Paint.Style.FILL
        paint.color = accentColor
        paint.alpha = (baseAlpha * 0.7f).toInt().coerceIn(0, 255)

        val bookPath = Path().apply {
            moveTo(centerX, bookCenterY)
            // Left page curve
            cubicTo(
                centerX - 30f, bookCenterY - 15f,
                centerX - bookWidth / 2f, bookCenterY - 10f,
                centerX - bookWidth / 2f, bookCenterY + bookHeight
            )
            // Bottom edge left
            lineTo(centerX - 10f, bookCenterY + bookHeight + 15f)
            // Spine center bottom
            lineTo(centerX, bookCenterY + bookHeight + 22f)
            // Spine center bottom right
            lineTo(centerX + 10f, bookCenterY + bookHeight + 15f)
            // Bottom edge right
            lineTo(centerX + bookWidth / 2f, bookCenterY + bookHeight)
            // Right page curve
            cubicTo(
                centerX + bookWidth / 2f, bookCenterY - 10f,
                centerX + 30f, bookCenterY - 15f,
                centerX, bookCenterY
            )
            close()
        }
        canvas.drawPath(bookPath, paint)

        // Inner Page Details (Open Book Pages)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.color = if (isDarkTheme) Color.parseColor("#FFFFFF") else accentColor
        paint.alpha = (baseAlpha * 2.2f).toInt().coerceIn(0, 255)

        // Left Page Arc
        val leftPageRect = RectF(centerX - bookWidth / 2f + 10f, bookCenterY - 5f, centerX - 5f, bookCenterY + bookHeight - 5f)
        canvas.drawArc(leftPageRect, -10f, -170f, false, paint)

        // Right Page Arc
        val rightPageRect = RectF(centerX + 5f, bookCenterY - 5f, centerX + bookWidth / 2f - 10f, bookCenterY + bookHeight - 5f)
        canvas.drawArc(rightPageRect, 190f, 170f, false, paint)

        // Center Spine Line
        canvas.drawLine(centerX, bookCenterY, centerX, bookCenterY + bookHeight + 18f, paint)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator.cancel()
    }
}
