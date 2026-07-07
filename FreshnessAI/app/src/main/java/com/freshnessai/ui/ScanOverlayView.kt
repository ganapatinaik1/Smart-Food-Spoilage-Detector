package com.freshnessai.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.freshnessai.R
import kotlin.math.min

/**
 * Custom view that draws a dark semi-transparent overlay with a clear rounded rectangle
 * in the center to highlight the scanning area, plus corner brackets for targeting.
 * Also draws a faint circle in the center to show where the sensor will be sampled.
 */
class ScanOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.scanner_overlay)
        style = Paint.Style.FILL
    }

    private val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.scanner_frame)
        style = Paint.Style.STROKE
        strokeWidth = context.resources.getDimension(R.dimen.scanner_corner_thickness)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val sensorIndicatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x66FFFFFF // Semi-transparent white
        style = Paint.Style.STROKE
        strokeWidth = 2f * resources.displayMetrics.density
        pathEffect = android.graphics.DashPathEffect(floatArrayOf(10f, 10f), 0f)
    }

    private var frameRect = RectF()
    private val frameSize = context.resources.getDimension(R.dimen.scanner_frame_size)
    private val cornerLength = context.resources.getDimension(R.dimen.scanner_corner_length)
    private val cornerRadius = context.resources.getDimension(R.dimen.corner_lg)

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val left = (w - frameSize) / 2f
        val top = (h - frameSize) / 2f - (h * 0.1f) // Shift up slightly
        frameRect.set(left, top, left + frameSize, top + frameSize)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw dark overlay everywhere
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), overlayPaint)

        // Clear the center rectangle
        canvas.drawRoundRect(frameRect, cornerRadius, cornerRadius, clearPaint)

        // Draw the corner brackets
        drawCorners(canvas)

        // Draw the sensor circle indicator in the center
        // Sensor is typically 12% of the QR width
        val sensorRadius = frameSize * 0.12f
        canvas.drawCircle(frameRect.centerX(), frameRect.centerY(), sensorRadius, sensorIndicatorPaint)
    }

    private fun drawCorners(canvas: Canvas) {
        val path = Path()

        // Top Left
        path.moveTo(frameRect.left, frameRect.top + cornerLength)
        path.lineTo(frameRect.left, frameRect.top + cornerRadius)
        path.quadTo(frameRect.left, frameRect.top, frameRect.left + cornerRadius, frameRect.top)
        path.lineTo(frameRect.left + cornerLength, frameRect.top)

        // Top Right
        path.moveTo(frameRect.right - cornerLength, frameRect.top)
        path.lineTo(frameRect.right - cornerRadius, frameRect.top)
        path.quadTo(frameRect.right, frameRect.top, frameRect.right, frameRect.top + cornerRadius)
        path.lineTo(frameRect.right, frameRect.top + cornerLength)

        // Bottom Right
        path.moveTo(frameRect.right, frameRect.bottom - cornerLength)
        path.lineTo(frameRect.right, frameRect.bottom - cornerRadius)
        path.quadTo(frameRect.right, frameRect.bottom, frameRect.right - cornerRadius, frameRect.bottom)
        path.lineTo(frameRect.right - cornerLength, frameRect.bottom)

        // Bottom Left
        path.moveTo(frameRect.left + cornerLength, frameRect.bottom)
        path.lineTo(frameRect.left + cornerRadius, frameRect.bottom)
        path.quadTo(frameRect.left, frameRect.bottom, frameRect.left, frameRect.bottom - cornerRadius)
        path.lineTo(frameRect.left, frameRect.bottom - cornerLength)

        canvas.drawPath(path, framePaint)
    }
}
