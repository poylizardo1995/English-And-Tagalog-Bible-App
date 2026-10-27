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
    
    // Smooth breathing pulse animation for the meditative wave glow
    private var pulseProgress = 0f
    private val animator: ValueAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 4500 // Gentle, soothing 4.5-second breathing wave cycle
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
        // 1. BREATHING MEDITATIVE GLOW AURA (Wave Light)
        // ==========================================
        // Pulsing radius and opacity for smooth fade-in/fade-out wave effect
        val currentAuraRadius = 260f + (pulseProgress * 70f)
        val maxAuraRadius = currentAuraRadius + 90f
        
        val baseAuraAlpha = if (isDarkTheme) {
            (50 + (pulseProgress * 45)).toInt() // Rich glow for dark/night themes
        } else {
            (75 + (pulseProgress * 55)).toInt() // High visibility vibrant warm glow for Sepia/light theme
        }

        val auraShader = RadialGradient(
            centerX, centerY, maxAuraRadius,
            intArrayOf(
                Color.argb(baseAuraAlpha, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor)),
                Color.argb((baseAuraAlpha * 0.35f).toInt(), Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor)),
                Color.TRANSPARENT
            ),
            floatArrayOf(0.0f, 0.55f, 1.0f),
            Shader.TileMode.CLAMP
        )
        paint.shader = auraShader
        canvas.drawCircle(centerX, centerY, maxAuraRadius, paint)
        paint.shader = null // Reset shader

        // ==========================================
        // 2. RADIANT LIGHT RAYS (Breathing Wave Effect)
        // ==========================================
        val rayAlpha = if (isDarkTheme) {
            (25 + (pulseProgress * 25)).toInt()
        } else {
            (40 + (pulseProgress * 30)).toInt() // Stronger visibility in Sepia mode
        }
        
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f
        paint.color = accentColor
        paint.alpha = rayAlpha.coerceIn(0, 255)

        val rayCount = 14
        val innerRadius = 45f
        val outerRadius = 270f
        for (i in 0 until rayCount) {
            val angle = (i * (360f / rayCount)) * (Math.PI / 180f)
            val startX = centerX + (innerRadius * kotlin.math.cos(angle)).toFloat()
            val startY = centerY + (innerRadius * kotlin.math.sin(angle)).toFloat()
            val endX = centerX + (outerRadius * kotlin.math.cos(angle)).toFloat()
            val endY = centerY + (outerRadius * kotlin.math.sin(angle)).toFloat()
            canvas.drawLine(startX, startY, endX, endY, paint)
        }

        // ==========================================
        // 3. SOLID, ELEGANT PROFESSIONAL CROSS
        // ==========================================
        val crossWidth = 130f
        val crossHeight = 260f
        val barThickness = 32f

        // Subtle professional depth shadow
        paint.style = Paint.Style.FILL
        paint.color = if (isDarkTheme) Color.parseColor("#0A0A0A") else Color.parseColor("#3A2216")
        paint.alpha = if (isDarkTheme) 200 else 120

        val shadowOffset = 3.5f
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

        // Main Solid Cross Body (Opaque, clean, solid finish — no center hole)
        paint.color = accentColor
        paint.alpha = 255

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
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator.cancel()
    }
}
