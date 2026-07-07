package com.freshnessai.analysis

import com.freshnessai.data.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Master freshness analysis engine. Combines:
 * 1. pH estimated from sensor color (ColorAnalyzer)
 * 2. Food category profile (from QR data + color data)
 * 3. Time factors (manufacturing date, expiry date)
 *
 * Outputs a comprehensive FreshnessResult.
 */
class FreshnessEngine(private val colorData: FreshnessColorData) {

    private val dateFormats = listOf(
        SimpleDateFormat("yyyy-MM-dd", Locale.US),
        SimpleDateFormat("dd-MM-yyyy", Locale.US),
        SimpleDateFormat("yyyy/MM/dd", Locale.US),
        SimpleDateFormat("dd/MM/yyyy", Locale.US)
    )

    /**
     * Performs comprehensive freshness analysis.
     *
     * @param colorResult The color analysis result (pH + confidence)
     * @param detectedColor The calibrated color from the sensor
     * @param foodInfo Food metadata from the QR code
     * @return Complete FreshnessResult
     */
    fun analyze(
        colorResult: ColorAnalysisResult,
        detectedColor: CalibratedColor,
        foodInfo: FoodInfo
    ): FreshnessResult {
        val category = colorData.getCategoryOrDefault(foodInfo.category)
        val now = System.currentTimeMillis()

        // ── 1. Color-based freshness score ──
        val colorScore = computeColorFreshnessScore(colorResult.estimatedPh, category)

        // ── 2. Time-based freshness score ──
        val timeScore = computeTimeFreshnessScore(foodInfo, category, now)

        // ── 3. Combined score with weighting ──
        // Color sensor is the primary signal (70%), time is secondary (30%)
        // If we have good time data, it's a useful sanity check
        val combinedScore = if (timeScore >= 0) {
            (colorScore * 0.7f + timeScore * 0.3f).roundToInt()
        } else {
            colorScore
        }
        val finalScore = combinedScore.coerceIn(0, 100)

        // ── 4. Determine status ──
        val status = when {
            finalScore >= 85 -> FreshnessStatus.EXCELLENT
            finalScore >= 70 -> FreshnessStatus.GOOD
            finalScore >= 50 -> FreshnessStatus.FAIR
            finalScore >= 30 -> FreshnessStatus.CAUTION
            finalScore >= 10 -> FreshnessStatus.SPOILED
            else -> FreshnessStatus.DANGEROUS
        }

        // ── 5. Edibility check ──
        val phBasedEdible = colorResult.estimatedPh < category.spoilageThresholdPh
        val isExpired = isExpired(foodInfo, now)
        val edible = phBasedEdible && !isExpired && finalScore >= 30

        // ── 6. Remaining hours estimate ──
        val remainingHours = estimateRemainingHours(
            colorResult.estimatedPh, category, foodInfo, now
        )

        // ── 7. Generate recommendations ──
        val recommendations = generateRecommendations(
            finalScore, status, colorResult, category, foodInfo, isExpired, remainingHours
        )

        // ── 8. Status description ──
        val statusDescription = generateStatusDescription(
            status, colorResult.estimatedPh, category, remainingHours
        )

        // ── 9. Find matching color stop for label ──
        val colorLabel = colorResult.matchedLabel

        return FreshnessResult(
            overallScore = finalScore,
            status = status,
            estimatedPh = colorResult.estimatedPh,
            detectedColor = detectedColor,
            foodInfo = foodInfo,
            remainingHours = remainingHours,
            edible = edible,
            confidence = colorResult.confidence,
            recommendations = recommendations,
            statusLabel = status.displayName,
            statusDescription = statusDescription,
            colorLabel = colorLabel,
            safetyNotes = category.safetyNotes
        )
    }

    /**
     * Computes freshness score based on pH relative to food category profile.
     * Score is 0-100 where 100 = perfectly fresh.
     */
    private fun computeColorFreshnessScore(estimatedPh: Float, category: FoodCategory): Int {
        val freshPh = category.typicalFreshPh
        val spoilagePh = category.spoilageThresholdPh
        val range = abs(spoilagePh - freshPh)

        if (range < 0.1f) return 50

        // How far is the detected pH from fresh?
        val deviation = abs(estimatedPh - freshPh)
        val normalizedDeviation = (deviation / range).coerceIn(0f, 2f)

        // Score decreases as pH deviates from fresh
        val score = when {
            normalizedDeviation < 0.1f -> 95  // Very close to fresh
            normalizedDeviation < 0.3f -> 85  // Fresh
            normalizedDeviation < 0.5f -> 70  // Acceptable
            normalizedDeviation < 0.7f -> 55  // Starting to change
            normalizedDeviation < 0.9f -> 40  // Early spoilage
            normalizedDeviation < 1.2f -> 25  // Spoiled
            normalizedDeviation < 1.5f -> 15  // Advanced spoilage
            else -> 5                          // Severe decomposition
        }

        return score
    }

    /**
     * Computes time-based freshness score using manufacturing/expiry dates.
     * Returns -1 if no valid time data is available.
     */
    private fun computeTimeFreshnessScore(
        foodInfo: FoodInfo,
        category: FoodCategory,
        nowMs: Long
    ): Int {
        val mfgDate = parseDate(foodInfo.manufacturingDate)
        val expDate = parseDate(foodInfo.expiryDate)

        if (mfgDate == null && expDate == null) return -1

        // If we have both dates
        if (mfgDate != null && expDate != null) {
            val totalLife = expDate.time - mfgDate.time
            val elapsed = nowMs - mfgDate.time
            if (totalLife <= 0) return -1

            val remaining = (totalLife - elapsed).toFloat() / totalLife
            return (remaining * 100f).roundToInt().coerceIn(0, 100)
        }

        // If we only have expiry date
        if (expDate != null) {
            val shelfLifeMs = category.defaultShelfLifeHours * 3600000L
            val remaining = (expDate.time - nowMs).toFloat() / shelfLifeMs
            return (remaining * 100f).roundToInt().coerceIn(0, 100)
        }

        // If we only have manufacturing date
        if (mfgDate != null) {
            val shelfLifeMs = max(foodInfo.maxShelfHours, category.defaultShelfLifeHours) * 3600000L
            val elapsed = nowMs - mfgDate.time
            val remaining = 1f - (elapsed.toFloat() / shelfLifeMs)
            return (remaining * 100f).roundToInt().coerceIn(0, 100)
        }

        return -1
    }

    /**
     * Estimates remaining safe hours based on pH trajectory and category.
     */
    private fun estimateRemainingHours(
        estimatedPh: Float,
        category: FoodCategory,
        foodInfo: FoodInfo,
        nowMs: Long
    ): Int {
        val expDate = parseDate(foodInfo.expiryDate)

        // If expiry date is available and food is still fresh, use it
        if (expDate != null && expDate.time > nowMs) {
            val hoursToExpiry = ((expDate.time - nowMs) / 3600000f).toInt()

            // Adjust based on sensor reading
            val phProgress = abs(estimatedPh - category.typicalFreshPh) /
                    abs(category.spoilageThresholdPh - category.typicalFreshPh)

            return when {
                phProgress < 0.3f -> hoursToExpiry  // Sensor agrees, use expiry
                phProgress < 0.6f -> (hoursToExpiry * 0.6f).toInt()  // Some spoilage
                phProgress < 0.9f -> (hoursToExpiry * 0.2f).toInt()  // Significant spoilage
                else -> 0  // Already spoiled
            }
        }

        // No expiry date — estimate from pH and spoilage rate
        val rateMultiplier = when (category.spoilageRate) {
            "very_fast" -> 0.5f
            "fast" -> 0.7f
            "moderate" -> 1.0f
            "slow" -> 1.5f
            else -> 1.0f
        }

        val phProgress = abs(estimatedPh - category.typicalFreshPh) /
                abs(category.spoilageThresholdPh - category.typicalFreshPh)

        val baseHoursRemaining = ((1f - phProgress) * category.defaultShelfLifeHours * rateMultiplier)
        return max(0, baseHoursRemaining.toInt())
    }

    /**
     * Generates contextual recommendations based on analysis.
     */
    private fun generateRecommendations(
        score: Int,
        status: FreshnessStatus,
        colorResult: ColorAnalysisResult,
        category: FoodCategory,
        foodInfo: FoodInfo,
        isExpired: Boolean,
        remainingHours: Int
    ): List<String> {
        val recs = mutableListOf<String>()

        // Expiry check
        if (isExpired) {
            recs.add("⚠️ This product has passed its expiry date. Exercise caution.")
        }

        // Freshness-based
        when (status) {
            FreshnessStatus.EXCELLENT -> {
                recs.add("✅ Product is in excellent condition — safe to consume.")
                if (remainingHours > 48) {
                    recs.add("🕐 You have approximately ${remainingHours / 24} days before quality begins to decline.")
                }
            }
            FreshnessStatus.GOOD -> {
                recs.add("✅ Product is fresh and safe to consume.")
                recs.add("💡 Best consumed within the next ${min(remainingHours, 72) / 24} days for optimal quality.")
            }
            FreshnessStatus.FAIR -> {
                recs.add("⚠️ Product quality is declining. Still acceptable for consumption.")
                recs.add("🕐 Consume within ${min(remainingHours, 24)} hours for best results.")
                recs.add("👃 Check for any unusual odors or texture changes before consuming.")
            }
            FreshnessStatus.CAUTION -> {
                recs.add("🟡 Early signs of spoilage detected by the sensor.")
                recs.add("⚠️ Consume only after thorough visual and smell inspection.")
                recs.add("🔥 If consuming, cook thoroughly to reduce bacterial risk.")
                recs.add("❌ Do not serve to children, elderly, or immunocompromised individuals.")
            }
            FreshnessStatus.SPOILED -> {
                recs.add("🔴 Significant spoilage detected. NOT recommended for consumption.")
                recs.add("🗑️ Dispose of this product safely.")
                recs.add("🧤 Handle with care — bacterial contamination likely.")
            }
            FreshnessStatus.DANGEROUS -> {
                recs.add("☠️ SEVERE DECOMPOSITION DETECTED — DO NOT CONSUME.")
                recs.add("🗑️ Dispose immediately in sealed container.")
                recs.add("🧼 Wash hands thoroughly after handling.")
                recs.add("⚠️ Check other items stored with this product for cross-contamination.")
            }
        }

        // Confidence note
        if (colorResult.confidence < 0.5f) {
            recs.add("📊 Analysis confidence is low. Environment lighting may affect accuracy — try scanning in better light.")
        }

        // Category-specific
        when (category.spoilageRate) {
            "very_fast" -> recs.add("⏰ ${category.name} is highly perishable — always refrigerate.")
            "fast" -> recs.add("🧊 Keep ${category.name.lowercase()} refrigerated at all times.")
        }

        return recs
    }

    /**
     * Generates a human-readable status description.
     */
    private fun generateStatusDescription(
        status: FreshnessStatus,
        estimatedPh: Float,
        category: FoodCategory,
        remainingHours: Int
    ): String {
        return when (status) {
            FreshnessStatus.EXCELLENT ->
                "Your ${category.name.lowercase()} is perfectly fresh with a pH of ${String.format("%.1f", estimatedPh)}. " +
                        "The sensor shows optimal freshness indicators."
            FreshnessStatus.GOOD ->
                "The product is fresh with a pH reading of ${String.format("%.1f", estimatedPh)}. " +
                        "No signs of spoilage detected."
            FreshnessStatus.FAIR ->
                "The sensor detects a pH of ${String.format("%.1f", estimatedPh)}, indicating the product is " +
                        "starting to age. Still safe but consume soon."
            FreshnessStatus.CAUTION ->
                "pH reading of ${String.format("%.1f", estimatedPh)} indicates initial bacterial activity. " +
                        "The product is past its peak freshness."
            FreshnessStatus.SPOILED ->
                "A pH of ${String.format("%.1f", estimatedPh)} confirms active spoilage. " +
                        "The food is no longer safe for consumption."
            FreshnessStatus.DANGEROUS ->
                "Critical pH reading of ${String.format("%.1f", estimatedPh)} indicates extreme decomposition. " +
                        "This product is toxic and must be discarded immediately."
        }
    }

    /**
     * Checks if the food has passed its expiry date.
     */
    private fun isExpired(foodInfo: FoodInfo, nowMs: Long): Boolean {
        val expDate = parseDate(foodInfo.expiryDate) ?: return false
        return nowMs > expDate.time
    }

    /**
     * Tries to parse a date string with multiple format patterns.
     */
    private fun parseDate(dateStr: String): Date? {
        if (dateStr.isBlank()) return null
        for (fmt in dateFormats) {
            try {
                fmt.isLenient = false
                return fmt.parse(dateStr)
            } catch (_: Exception) {
                continue
            }
        }
        return null
    }
}
