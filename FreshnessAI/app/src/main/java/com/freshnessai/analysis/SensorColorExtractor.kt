package com.freshnessai.analysis

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import com.freshnessai.data.CalibrationResult
import com.freshnessai.data.CalibratedColor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Extracts the sensor color from the circular region at the center of the QR code.
 * Uses median filtering for robustness against noise and outliers.
 */
class SensorColorExtractor(private val calibrator: ColorCalibrator) {

    /**
     * Extracts calibrated HSV color from the sensor circle at QR center.
     *
     * @param bitmap The full camera frame
     * @param qrBounds Bounding box of the detected QR code
     * @param calibration Calibration data from ColorCalibrator
     * @param radiusRatio Ratio of sensor circle radius to QR size (default 0.12)
     * @return CalibratedColor with HSV values and calibrated RGB
     */
    fun extractSensorColor(
        bitmap: Bitmap,
        qrBounds: Rect,
        calibration: CalibrationResult,
        radiusRatio: Float = 0.12f
    ): CalibratedColor? {
        val centerX = (qrBounds.left + qrBounds.right) / 2
        val centerY = (qrBounds.top + qrBounds.bottom) / 2
        val qrSize = min(qrBounds.width(), qrBounds.height())
        val radius = (qrSize * radiusRatio).toInt()

        if (radius < 3) return null

        val rawPixels = mutableListOf<IntArray>()   // Raw RGB
        val calPixels = mutableListOf<IntArray>()   // Calibrated RGB
        val hsvList = mutableListOf<FloatArray>()   // HSV values

        // Sample pixels in circular region
        for (y in (centerY - radius)..(centerY + radius)) {
            for (x in (centerX - radius)..(centerX + radius)) {
                val dx = x - centerX
                val dy = y - centerY

                // Check if within circle
                if (dx * dx + dy * dy > radius * radius) continue

                // Check bounds
                if (x < 0 || x >= bitmap.width || y < 0 || y >= bitmap.height) continue

                val pixel = bitmap.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)

                rawPixels.add(intArrayOf(r, g, b))

                // Apply calibration
                val calibrated = calibrator.applyCalibration(r, g, b, calibration)
                calPixels.add(calibrated)

                // Convert calibrated pixel to HSV
                val hsv = FloatArray(3)
                Color.RGBToHSV(calibrated[0], calibrated[1], calibrated[2], hsv)
                hsvList.add(hsv)
            }
        }

        if (hsvList.size < 10) return null

        // Use median for each HSV component (robust to outliers)
        val medianH = robustMedianHue(hsvList.map { it[0] })
        val medianS = median(hsvList.map { it[1] })
        val medianV = median(hsvList.map { it[2] })

        // Calculate median for raw and calibrated RGB
        val medianRawR = median(rawPixels.map { it[0].toFloat() }).toInt()
        val medianRawG = median(rawPixels.map { it[1].toFloat() }).toInt()
        val medianRawB = median(rawPixels.map { it[2].toFloat() }).toInt()

        val medianCalR = median(calPixels.map { it[0].toFloat() }).toInt()
        val medianCalG = median(calPixels.map { it[1].toFloat() }).toInt()
        val medianCalB = median(calPixels.map { it[2].toFloat() }).toInt()

        return CalibratedColor(
            hue = medianH,
            saturation = medianS,
            value = medianV,
            rawR = medianRawR,
            rawG = medianRawG,
            rawB = medianRawB,
            calibratedR = medianCalR,
            calibratedG = medianCalG,
            calibratedB = medianCalB
        )
    }

    /**
     * Calculates the median of a list of floats.
     */
    private fun median(values: List<Float>): Float {
        if (values.isEmpty()) return 0f
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 0) {
            (sorted[mid - 1] + sorted[mid]) / 2f
        } else {
            sorted[mid]
        }
    }

    /**
     * Calculates the robust median hue, handling the circular nature of hue (0-360).
     * Uses the technique of shifting hues to avoid wrap-around issues.
     */
    private fun robustMedianHue(hues: List<Float>): Float {
        if (hues.isEmpty()) return 0f

        // Check if hues span the 0/360 boundary
        val hasLow = hues.any { it < 60f }
        val hasHigh = hues.any { it > 300f }

        if (hasLow && hasHigh) {
            // Shift all hues by 180, compute median, then shift back
            val shifted = hues.map { (it + 180f) % 360f }
            val medianShifted = median(shifted)
            return (medianShifted + 180f) % 360f
        }

        return median(hues)
    }
}
