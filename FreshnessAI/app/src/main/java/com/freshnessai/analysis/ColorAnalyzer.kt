package com.freshnessai.analysis

import com.freshnessai.data.ColorAnalysisResult
import com.freshnessai.data.ColorStop
import com.freshnessai.data.InterpolationConfig
import kotlin.math.abs
import kotlin.math.exp

/**
 * Maps a calibrated HSV color to an estimated pH using piecewise linear
 * interpolation across the known color stops from the data file.
 *
 * Primary discriminant: Hue (carries most pH information)
 * Secondary: Saturation (used for confidence weighting)
 * Tertiary: Value/brightness (minor correction factor)
 */
class ColorAnalyzer(
    private val colorStops: List<ColorStop>,
    private val config: InterpolationConfig
) {
    // Pre-sorted stops: hue descending (pH increasing as hue decreases)
    // pH 1-2 → hue 342° ... pH 13-14 → hue 61°
    private val sortedStops = colorStops.sortedByDescending { it.hue }

    /**
     * Analyzes a calibrated HSV color and estimates the pH level.
     *
     * @param hue Calibrated hue (0-360°)
     * @param saturation Calibrated saturation (0-1)
     * @param valueBrightness Calibrated value/brightness (0-1)
     * @return ColorAnalysisResult with estimated pH and confidence
     */
    fun analyze(hue: Float, saturation: Float, valueBrightness: Float): ColorAnalysisResult {
        // Saturation and value in the data are stored as percentages (0-100)
        val satPercent = saturation * 100f
        val valPercent = valueBrightness * 100f

        // Edge cases: if hue is above the highest stop or below the lowest
        if (hue >= sortedStops.first().hue) {
            val stop = sortedStops.first()
            val conf = computeConfidence(satPercent, valPercent, stop)
            return ColorAnalysisResult(
                estimatedPh = stop.phMidpoint,
                confidence = conf,
                matchedStopIndex = 0,
                interpolationFactor = 0f,
                matchedLabel = stop.label
            )
        }

        if (hue <= sortedStops.last().hue) {
            val stop = sortedStops.last()
            val conf = computeConfidence(satPercent, valPercent, stop)
            return ColorAnalysisResult(
                estimatedPh = stop.phMidpoint,
                confidence = conf,
                matchedStopIndex = sortedStops.size - 1,
                interpolationFactor = 1f,
                matchedLabel = stop.label
            )
        }

        // Find the two bracketing stops
        var upperIdx = 0
        var lowerIdx = 1
        for (i in 0 until sortedStops.size - 1) {
            if (hue <= sortedStops[i].hue && hue >= sortedStops[i + 1].hue) {
                upperIdx = i
                lowerIdx = i + 1
                break
            }
        }

        val upper = sortedStops[upperIdx]
        val lower = sortedStops[lowerIdx]

        // Linear interpolation based on hue position
        val hueRange = upper.hue - lower.hue
        val hueFraction = if (hueRange > 0f) {
            (hue - lower.hue) / hueRange
        } else {
            0.5f
        }

        // Interpolate pH
        val phUpper = upper.phMidpoint
        val phLower = lower.phMidpoint
        var estimatedPh = phLower + (phUpper - phLower) * hueFraction

        // Apply saturation-based correction
        val expectedSat = lower.saturation + (upper.saturation - lower.saturation) * hueFraction
        val satDeviation = (satPercent - expectedSat) / 100f
        estimatedPh -= satDeviation * config.secondaryWeightSaturation * 2f

        // Apply value-based correction
        val expectedVal = lower.value + (upper.value - lower.value) * hueFraction
        val valDeviation = (valPercent - expectedVal) / 100f
        estimatedPh -= valDeviation * config.tertiaryWeightValue * 2f

        // Clamp pH to valid range
        estimatedPh = estimatedPh.coerceIn(1f, 14f)

        // Compute confidence
        val hueConf = 1f - (abs(satDeviation) + abs(valDeviation)) / 2f
        val primaryStop = if (hueFraction > 0.5f) upper else lower
        val satConf = computeConfidence(satPercent, valPercent, primaryStop)
        val confidence = (hueConf * config.primaryWeightHue + satConf * (1f - config.primaryWeightHue))
            .coerceIn(0.2f, 1f)

        // Determine matched label (closest stop)
        val matchedIdx = if (hueFraction > 0.5f) upperIdx else lowerIdx
        val matchedLabel = sortedStops[matchedIdx].label

        return ColorAnalysisResult(
            estimatedPh = estimatedPh,
            confidence = confidence,
            matchedStopIndex = matchedIdx,
            interpolationFactor = hueFraction,
            matchedLabel = matchedLabel
        )
    }

    /**
     * Computes confidence based on how well the detected saturation and value
     * match the expected values for a color stop.
     */
    private fun computeConfidence(satPercent: Float, valPercent: Float, stop: ColorStop): Float {
        val satDiff = abs(satPercent - stop.saturation) / 100f
        val valDiff = abs(valPercent - stop.value) / 100f

        // Gaussian-like confidence decay
        val satConf = exp(-satDiff * satDiff * 8f)
        val valConf = exp(-valDiff * valDiff * 8f)

        return (satConf * 0.6f + valConf * 0.4f).coerceIn(0.2f, 1f)
    }
}
