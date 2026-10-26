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
    
    private var animProgress = 0f
    private var silhouetteColor = Color.argb(30, 110, 71, 44) // Subtle translucent tint

    init {
        // Continuous smooth loop for ambient floating background motion
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 15000 // 15 seconds per loop cycle
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                animProgress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    // Dynamically update the silhouette tint based on your active ThemeConfig accent color
    fun updateTheme(accentColor: Int, isDark: Boolean) {
        val alpha = if (isDark) 35 else 25
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

        val widthF = width.toFloat()
        val heightF = height.toFloat()

        // Parallax slow-scrolling offset based on animation progress
        val shiftX = animProgress * widthF

        // 1. Draw Minimalist Robed Silhouette (Stickman style with blank white/tinted face and robe)
        val figureX = (widthF * 0.2f + shiftX) % (widthF + 200f) - 100f
        val figureY = heightF * 0.82f

        // Head (blank circular silhouette)
        canvas.drawCircle(figureX, figureY - 60f, 18f, paint)
        
        // Robe body path
        val robePath = Path().apply {
            moveTo(figureX, figureY - 42f)
            lineTo(figureX - 25f, figureY + 40f)
            lineTo(figureX + 25f, figureY + 40f)
            close()
        }
        canvas.drawPath(robePath, paint)

        // 2. Draw Floating Minimalist Cross / Star Motif on the opposite side
        val crossX = (widthF * 0.75f - (shiftX * 0.5f)) % (widthF + 150f)
        val crossY = heightF * 0.18f

        // Simple cross silhouette
        canvas.drawRect(crossX - 4f, crossY - 25f, crossX + 4f, crossY + 25f, paint)
        canvas.drawRect(crossX - 16f, crossY - 10f, crossX + 16f, crossY - 2f, paint)

        // 3. Ambient floating blessing particles / stars
        val starX1 = (widthF * 0.5f + (shiftX * 0.3f)) % widthF
        canvas.drawCircle(starX1, heightF * 0.45f, 5f, paint)
    }
}
