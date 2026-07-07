package com.freshnessai.analysis

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import com.freshnessai.data.CalibrationResult
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Calibrates camera exposure using QR code's known black and white modules.
 * QR finder patterns (the three big squares in corners) contain both
 * pure black and pure white regions, providing reference points.
 */
class ColorCalibrator {

    /**
     * Performs exposure calibration by sampling black and white pixels
     * from the QR code's finder patterns.
     *
     * @param bitmap The full camera frame bitmap
     * @param qrBounds Bounding box of the detected QR code
     * @return CalibrationResult with gain/offset for each RGB channel
     */
    fun calibrate(bitmap: Bitmap, qrBounds: Rect): CalibrationResult {
        // Clamp bounds to bitmap dimensions
        val left = max(0, qrBounds.left)
        val top = max(0, qrBounds.top)
        val right = min(bitmap.width, qrBounds.right)
        val bottom = min(bitmap.height, qrBounds.bottom)

        if (right - left < 20 || bottom - top < 20) {
            return CalibrationResult(1f, 1f, 1f, 0f, 0f, 0f, false)
        }

        val qrWidth = right - left
        val qrHeight = bottom - top

        // QR finder patterns are at three corners, each ~7/21 of QR size
        val finderSize = min(qrWidth, qrHeight) / 3

        // Sample from top-left finder pattern
        val blackSamples = mutableListOf<FloatArray>()
        val whiteSamples = mutableListOf<FloatArray>()

        // Three finder pattern locations: top-left, top-right, bottom-left
        val finderLocations = listOf(
            Rect(left, top, left + finderSize, top + finderSize),
            Rect(right - finderSize, top, right, top + finderSize),
            Rect(left, bottom - finderSize, left + finderSize, bottom)
        )

        for (finder in finderLocations) {
            sampleFinderPattern(bitmap, finder, blackSamples, whiteSamples)
        }

        if (blackSamples.isEmpty() || whiteSamples.isEmpty()) {
            return CalibrationResult(1f, 1f, 1f, 0f, 0f, 0f, false)
        }

        // Calculate average black and white values
        val avgBlackR = blackSamples.map { it[0] }.average().toFloat()
        val avgBlackG = blackSamples.map { it[1] }.average().toFloat()
        val avgBlackB = blackSamples.map { it[2] }.average().toFloat()

        val avgWhiteR = whiteSamples.map { it[0] }.average().toFloat()
        val avgWhiteG = whiteSamples.map { it[1] }.average().toFloat()
        val avgWhiteB = whiteSamples.map { it[2] }.average().toFloat()

        // Calculate gain: corrected = (pixel - black) * (255 / (white - black))
        val rangeR = max(1f, avgWhiteR - avgBlackR)
        val rangeG = max(1f, avgWhiteG - avgBlackG)
        val rangeB = max(1f, avgWhiteB - avgBlackB)

        val blackLum = (0.299f * avgBlackR + 0.587f * avgBlackG + 0.114f * avgBlackB)
        val whiteLum = (0.299f * avgWhiteR + 0.587f * avgWhiteG + 0.114f * avgWhiteB)

        return CalibrationResult(
            gainR = 255f / rangeR,
            gainG = 255f / rangeG,
            gainB = 255f / rangeB,
            offsetR = avgBlackR,
            offsetG = avgBlackG,
            offsetB = avgBlackB,
            isValid = (whiteLum - blackLum) > 30f, // need decent contrast
            blackLuminance = blackLum,
            whiteLuminance = whiteLum
        )
    }

    /**
     * Applies calibration correction to an RGB pixel.
     */
    fun applyCalibration(r: Int, g: Int, b: Int, cal: CalibrationResult): IntArray {
        if (!cal.isValid) return intArrayOf(r, g, b)

        val corrR = ((r - cal.offsetR) * cal.gainR).coerceIn(0f, 255f).toInt()
        val corrG = ((g - cal.offsetG) * cal.gainG).coerceIn(0f, 255f).toInt()
        val corrB = ((b - cal.offsetB) * cal.gainB).coerceIn(0f, 255f).toInt()

        return intArrayOf(corrR, corrG, corrB)
    }

    /**
     * Samples black and white pixels from a QR finder pattern region.
     * The finder pattern has alternating black/white/black/white/black stripes.
     * We use luminance thresholding to classify pixels.
     */
    private fun sampleFinderPattern(
        bitmap: Bitmap,
        region: Rect,
        blackSamples: MutableList<FloatArray>,
        whiteSamples: MutableList<FloatArray>
    ) {
        val clampedRegion = Rect(
            max(0, region.left),
            max(0, region.top),
            min(bitmap.width, region.right),
            min(bitmap.height, region.bottom)
        )

        val step = max(1, (clampedRegion.width() * clampedRegion.height()) / 100)
        var count = 0

        for (y in clampedRegion.top until clampedRegion.bottom step max(1, clampedRegion.height() / 10)) {
            for (x in clampedRegion.left until clampedRegion.right step max(1, clampedRegion.width() / 10)) {
                if (x < 0 || x >= bitmap.width || y < 0 || y >= bitmap.height) continue

                val pixel = bitmap.getPixel(x, y)
                val r = Color.red(pixel).toFloat()
                val g = Color.green(pixel).toFloat()
                val b = Color.blue(pixel).toFloat()
                val luminance = 0.299f * r + 0.587f * g + 0.114f * b

                if (luminance < 80f) {
                    blackSamples.add(floatArrayOf(r, g, b))
                } else if (luminance > 180f) {
                    whiteSamples.add(floatArrayOf(r, g, b))
                }

                count++
                if (count > 200) return
            }
        }
    }
}
