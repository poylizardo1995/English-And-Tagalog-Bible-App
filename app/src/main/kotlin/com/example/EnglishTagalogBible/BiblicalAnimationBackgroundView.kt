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
    
    // Smooth breathing pulse animation for the meditative glow
    private var pulseProgress = 0f
    private val animator: ValueAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 5000 // Gentle 5-second meditative breathing cycle
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
        val centerY = height / 2f - 20f

        val paint = Paint().apply {
            isAntiAlias = true
        }

        // ==========================================
        // 1. MEDITATIVE RADIAL GLOW AURA (Behind Cross)
        // ==========================================
        val maxRadius = 320f + (pulseProgress * 50f)
        val auraShader = RadialGradient(
            centerX, centerY, maxRadius,
            intArrayOf(
                Color.argb((if (isDarkTheme) 70 + (pulseProgress * 30) else 50 + (pulseProgress * 20)).toInt(), Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor)),
                Color.argb((if (isDarkTheme) 25 else 15), Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor)),
                Color.TRANSPARENT
            ),
            floatArrayOf(0.0f, 0.6f, 1.0f),
            Shader.TileMode.CLAMP
        )
        paint.shader = auraShader
        canvas.drawCircle(centerX, centerY, maxRadius, paint)
        paint.shader = null // Reset shader

        // ==========================================
        // 2. RADIANT LIGHT RAYS
        // ==========================================
        val rayAlpha = if (isDarkTheme) 20 + (pulseProgress * 15).toInt() else 15 + (pulseProgress * 10).toInt()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = accentColor
        paint.alpha = rayAlpha

        val rayCount = 12
        val innerRadius = 50f
        val outerRadius = 280f
        for (i in 0 until rayCount) {
            val angle = (i * (360f / rayCount)) * (Math.PI / 180f)
            val startX = centerX + (innerRadius * kotlin.math.cos(angle)).toFloat()
            val startY = centerY + (innerRadius * kotlin.math.sin(angle)).toFloat()
            val endX = centerX + (outerRadius * kotlin.math.cos(angle)).toFloat()
            val endY = centerY + (outerRadius * kotlin.math.sin(angle)).toFloat()
            canvas.drawLine(startX, startY, endX, endY, paint)
        }

        // ==========================================
        // 3. PROFESSIONAL ELEGANT CROSS
        // ==========================================
        val crossWidth = 130f
        val crossHeight = 260f
        val barThickness = 32f

        // Soft drop shadow / depth layer
        paint.style = Paint.Style.FILL
        paint.color = if (isDarkTheme) Color.parseColor("#0A0A0A") else Color.parseColor("#2A180E")
        paint.alpha = 180

        val shadowOffset = 3f
        val shadowVertRect = RectF(
            centerX - barThickness / 2f + shadowOffset,
            centerY - crossHeight / 2f + shadowOffset,
            centerX + barThickness / 2f + shadowOffset,
            centerY + crossHeight / 2f + shadowOffset
        )
        val shadowHorizRect = RectF(
            centerX - crossWidth / 2f + shadowOffset,
            centerY - crossHeight / 5f - barThickness / 2f + shadowOffset,
            centerX + crossWidth / 2f + shadowOffset,
            centerY - crossHeight / 5f + barThickness / 2f + shadowOffset
        )
        canvas.drawRoundRect(shadowVertRect, 8f, 8f, paint)
        canvas.drawRoundRect(shadowHorizRect, 8f, 8f, paint)

        // Main Solid Cross Body
        paint.color = accentColor
        paint.alpha = 245 // Highly solid, professional finish

        val verticalRect = RectF(
            centerX - barThickness / 2f,
            centerY - crossHeight / 2f,
            centerX + barThickness / 2f,
            centerY + crossHeight / 2f
        )
        val horizontalRect = RectF(
            centerX - crossWidth / 2f,
            centerY - crossHeight / 5f - barThickness / 2f,
            centerX + crossWidth / 2f,
            centerY - crossHeight / 5f + barThickness / 2f
        )

        canvas.drawRoundRect(verticalRect, 8f, 8f, paint)
        canvas.drawRoundRect(horizontalRect, 8f, 8f, paint)

        // Center Divine Light Highlight Spot
        paint.color = Color.WHITE
        paint.alpha = (120 + (pulseProgress * 80)).toInt().coerceIn(0, 255)
        val centerGleam = RectF(centerX - 5f, centerY - crossHeight / 5f - 5f, centerX + 5f, centerY - crossHeight / 5f + 5f)
        canvas.drawOval(centerGleam, paint)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator.cancel()
    }
}
