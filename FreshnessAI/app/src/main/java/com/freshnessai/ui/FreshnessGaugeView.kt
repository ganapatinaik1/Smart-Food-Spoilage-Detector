package com.freshnessai.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.freshnessai.R
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Custom view that draws a radial gauge to display the freshness score (0-100).
 * It animates from 0 to the target score when set.
 */
class FreshnessGaugeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.divider)
        style = Paint.Style.STROKE
        strokeWidth = resources.getDimension(R.dimen.gauge_stroke_width)
        strokeCap = Paint.Cap.ROUND
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.primary)
        style = Paint.Style.STROKE
        strokeWidth = resources.getDimension(R.dimen.gauge_stroke_width)
        strokeCap = Paint.Cap.ROUND
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.text_primary)
        textSize = resources.getDimension(R.dimen.text_display)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    private val subTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.text_hint)
        textSize = resources.getDimension(R.dimen.text_caption)
        textAlign = Paint.Align.CENTER
    }

    private val rectF = RectF()
    private var score = 0f
    private var animatedScore = 0f

    // Arc angles: start at 135 degrees, sweep 270 degrees total
    private val startAngle = 135f
    private val sweepAngle = 270f

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val padding = bgPaint.strokeWidth / 2f + 4f
        val size = min(w, h).toFloat()
        rectF.set(
            (w - size) / 2f + padding,
            (h - size) / 2f + padding,
            (w + size) / 2f - padding,
            (h + size) / 2f - padding
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw background arc
        canvas.drawArc(rectF, startAngle, sweepAngle, false, bgPaint)

        // Draw filled arc
        val animatedSweep = (animatedScore / 100f) * sweepAngle
        canvas.drawArc(rectF, startAngle, animatedSweep, false, fillPaint)

        // Draw text
        val centerX = width / 2f
        val centerY = height / 2f
        
        // Vertically center the text manually based on font metrics
        val textOffset = (textPaint.descent() + textPaint.ascent()) / 2f
        
        canvas.drawText(animatedScore.roundToInt().toString(), centerX, centerY - textOffset, textPaint)
        
        // Draw "Score" subtitle below
        canvas.drawText("SCORE", centerX, centerY + textPaint.textSize * 0.8f, subTextPaint)
    }

    /**
     * Sets the score and animates the gauge.
     */
    fun setScore(targetScore: Int, colorResId: Int? = null) {
        this.score = targetScore.coerceIn(0, 100).toFloat()
        
        if (colorResId != null) {
            fillPaint.color = ContextCompat.getColor(context, colorResId)
        }

        val animator = ValueAnimator.ofFloat(0f, score)
        animator.duration = 1200
        animator.interpolator = DecelerateInterpolator(1.5f)
        animator.addUpdateListener { animation ->
            animatedScore = animation.animatedValue as Float
            invalidate()
        }
        animator.start()
    }
}
