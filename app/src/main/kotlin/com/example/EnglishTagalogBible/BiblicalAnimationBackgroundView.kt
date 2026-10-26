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
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 20000 
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
        val alpha = if (isDark) 200 else 160
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
        paint.color = baseColor
        strokePaint.color = baseColor

        val widthF = width.toFloat()
        val heightF = height.toFloat()

        val centerX = widthF * 0.5f
        val centerY = heightF * 0.22f

        val pulse = 1f + 0.08f * kotlin.math.sin(animProgress * Math.PI * 2).toFloat()
        canvas.save()
        canvas.scale(pulse, pulse, centerX, centerY)

        if (animProgress < 0.33f) {
            val alphaProgress = (animProgress / 0.33f)
            drawTrinitySymbol(canvas, centerX, centerY, "FATHER", alphaProgress)
        } 
        else if (animProgress < 0.66f) {
            val alphaProgress = ((animProgress - 0.33f) / 0.33f)
            drawTrinitySymbol(canvas, centerX, centerY, "SON", alphaProgress)
        } 
        else {
            val alphaProgress = ((animProgress - 0.66f) / 0.34f)
            drawTrinitySymbol(canvas, centerX, centerY, "SPIRIT", alphaProgress)
        }

        canvas.restore()
    }

    private fun drawTrinitySymbol(canvas: Canvas, x: Float, y: Float, symbolType: String, progress: Float) {
        val fadeAlpha = when {
            progress < 0.2f -> progress / 0.2f
            progress > 0.8f -> (1.0f - progress) / 0.2f
            else -> 1.0f
        }
        
        val currentAlpha = (Color.alpha(baseColor) * fadeAlpha).toInt()
        paint.color = Color.argb(currentAlpha, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor))
        strokePaint.color = paint.color

        when (symbolType) {
            "FATHER" -> {
                canvas.drawCircle(x, y, 12f, paint)
                strokePaint.strokeWidth = 3f
                for (i in 0..5) {
                    val angle = i * (Math.PI / 3.0)
                    val x1 = x + (25f * kotlin.math.cos(angle)).toFloat()
                    val y1 = y + (25f * kotlin.math.sin(angle)).toFloat()
                    val x2 = x + (45f * kotlin.math.cos(angle)).toFloat()
                    val y2 = y + (45f * kotlin.math.sin(angle)).toFloat()
                    canvas.drawLine(x1, y1, x2, y2, strokePaint)
                }
            }
            "SON" -> {
                canvas.drawRect(x - 6f, y - 45f, x + 6f, y + 45f, paint)
                canvas.drawRect(x - 30f, y - 18f, x + 30f, y - 8f, paint)
            }
            "SPIRIT" -> {
                // Using quadTo for Android Canvas Path compatibility
                val flamePath = Path().apply {
                    moveTo(x, y + 30f)
                    quadTo(x - 30f, y, x, y - 45f)
                    quadTo(x + 30f, y, x, y + 30f)
                    close()
                }
                canvas.drawPath(flamePath, paint)
            }
        }
    }
}
