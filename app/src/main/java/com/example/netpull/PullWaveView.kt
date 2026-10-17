package com.example.netpull

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator

/**
 * Smooth animated network wave effect indicating active multi-stream pulling.
 */
class PullWaveView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * resources.displayMetrics.density
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private var waveProgress = 0f
    private var animator: ValueAnimator? = null
    var isPulling: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                if (value) startAnimation() else stopAnimation()
            }
        }

    var waveColor: Int = Color.parseColor("#CBB279")
        set(value) {
            field = value
            invalidate()
        }

    init {
        // Start stopped
    }

    private fun startAnimation() {
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1800
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                waveProgress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun stopAnimation() {
        animator?.cancel()
        animator = null
        waveProgress = 0f
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val maxRadius = minOf(cx, cy) * 0.95f
        val baseRadius = maxRadius * 0.35f

        if (!isPulling) {
            // Calm stationary ring
            wavePaint.color = waveColor
            wavePaint.alpha = 40
            canvas.drawCircle(cx, cy, baseRadius, wavePaint)
            return
        }

        // Draw 3 expanding pulsing ripple waves
        for (i in 0 until 3) {
            val offset = (waveProgress + i * 0.333f) % 1f
            val radius = baseRadius + (maxRadius - baseRadius) * offset
            val alpha = ((1f - offset) * 160).toInt().coerceIn(0, 255)

            wavePaint.color = waveColor
            wavePaint.alpha = alpha
            canvas.drawCircle(cx, cy, radius, wavePaint)

            fillPaint.color = waveColor
            fillPaint.alpha = (alpha * 0.15f).toInt()
            canvas.drawCircle(cx, cy, radius, fillPaint)
        }

        // Center beacon dot
        fillPaint.color = waveColor
        fillPaint.alpha = 220
        canvas.drawCircle(cx, cy, baseRadius * 0.4f, fillPaint)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopAnimation()
    }
}
