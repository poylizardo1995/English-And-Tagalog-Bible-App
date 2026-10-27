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
    
    // Smooth pulse progress for glowing core and rays
    private var pulseProgress = 0f
    private val animator: ValueAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 4000
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
        val centerY = height / 2f - 40f

        val paint = Paint().apply {
            isAntiAlias = true
        }

        // ==========================================
        // 1. RADIANT SUNBURST RAYS (Behind Cross)
        // ==========================================
        val rayAlpha = if (isDarkTheme) 35 + (pulseProgress * 15).toInt() else 25 + (pulseProgress * 10).toInt()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.color = accentColor
        paint.alpha = rayAlpha

        val rayCount = 16
        val rayLength = 320f
        for (i in 0 until rayCount) {
            val angle = (i * (360f / rayCount)) * (Math.PI / 180f)
            val startX = centerX + (40f * kotlin.math.cos(angle)).toFloat()
            val startY = centerY + (40f * kotlin.math.sin(angle)).toFloat()
            val endX = centerX + (rayLength * kotlin.math.cos(angle)).toFloat()
            val endY = centerY + (rayLength * kotlin.math.sin(angle)).toFloat()
            canvas.drawLine(startX, startY, endX, endY, paint)
        }

        // ==========================================
        // 2. SOLID CROSS (Crisp & Opaque)
        // ==========================================
        val crossWidth = 140f
        val crossHeight = 280f
        val barThickness = 38f

        // Outer crisp dark border / shadow for solidity
        paint.style = Paint.Style.FILL
        paint.color = if (isDarkTheme) Color.parseColor("#111111") else Color.parseColor("#3B2213")
        paint.alpha = 220

        val shadowOffset = 4f
        val shadowVertRect = RectF(
            centerX - barThickness / 2f + shadowOffset,
            centerY - crossHeight / 2f + shadowOffset,
            centerX + barThickness / 2f + shadowOffset,
            centerY + crossHeight / 2f + shadowOffset
        )
        val shadowHorizRect = RectF(
            centerX - crossWidth / 2f + shadowOffset,
            centerY - crossHeight / 6f - barThickness / 2f + shadowOffset,
            centerX + crossWidth / 2f + shadowOffset,
            centerY - crossHeight / 6f + barThickness / 2f + shadowOffset
        )
        canvas.drawRoundRect(shadowVertRect, 12f, 12f, paint)
        canvas.drawRoundRect(shadowHorizRect, 12f, 12f, paint)

        // Solid Main Cross Body
        paint.color = accentColor
        paint.alpha = 255 // Fully solid and opaque

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

        canvas.drawRoundRect(verticalRect, 12f, 12f, paint)
        canvas.drawRoundRect(horizontalRect, 12f, 12f, paint)

        // Center Gleam / Highlight
        paint.color = Color.WHITE
        paint.alpha = (90 + (pulseProgress * 60)).toInt().coerceIn(0, 255)
        val gleamRect = RectF(centerX - 6f, centerY - crossHeight / 6f - 6f, centerX + 6f, centerY - crossHeight / 6f + 6f)
        canvas.drawOval(gleamRect, paint)

        // ==========================================
        // 3. SOLID OPEN BIBLE BOOK AT THE BASE
        // ==========================================
        val bookCenterY = centerY + crossHeight / 2f + 45f
        val bookWidth = 210f
        val bookHeight = 70f

        // Book Base Fill (Solid)
        paint.style = Paint.Style.FILL
        paint.color = if (isDarkTheme) Color.parseColor("#222222") else Color.parseColor("#F5EBE6")
        paint.alpha = 240

        val bookPath = Path().apply {
            moveTo(centerX, bookCenterY)
            cubicTo(
                centerX - 30f, bookCenterY - 12f,
                centerX - bookWidth / 2f, bookCenterY - 8f,
                centerX - bookWidth / 2f, bookCenterY + bookHeight
            )
            lineTo(centerX - 10f, bookCenterY + bookHeight + 12f)
            lineTo(centerX, bookCenterY + bookHeight + 18f)
            lineTo(centerX + 10f, bookCenterY + bookHeight + 12f)
            lineTo(centerX + bookWidth / 2f, bookCenterY + bookHeight)
            cubicTo(
                centerX + bookWidth / 2f, bookCenterY - 8f,
                centerX + 30f, bookCenterY - 12f,
                centerX, bookCenterY
            )
            close()
        }
        canvas.drawPath(bookPath, paint)

        // Book Outline & Page Lines (Solid)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3.5f
        paint.color = accentColor
        paint.alpha = 255
        canvas.drawPath(bookPath, paint)

        // Center Spine & Page Curvature Lines
        canvas.drawLine(centerX, bookCenterY, centerX, bookCenterY + bookHeight + 18f, paint)
        
        paint.strokeWidth = 2f
        val leftPageRect = RectF(centerX - bookWidth / 2f + 14f, bookCenterY - 2f, centerX - 8f, bookCenterY + bookHeight - 2f)
        canvas.drawArc(leftPageRect, -10f, -165f, false, paint)

        val rightPageRect = RectF(centerX + 8f, bookCenterY - 2f, centerX + bookWidth / 2f - 14f, bookCenterY + bookHeight - 2f)
        canvas.drawArc(rightPageRect, 190f, 165f, false, paint)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator.cancel()
    }
}
