package com.freshnessai.data

import androidx.room.Entity
import androidx.room.PrimaryKey

// ── QR Code Payload ──
data class FoodInfo(
    val product: String,
    val category: String,
    val manufacturingDate: String,
    val expiryDate: String,
    val batch: String,
    val maxShelfHours: Int
)

// ── Color Analysis ──
data class CalibratedColor(
    val hue: Float,
    val saturation: Float,
    val value: Float,
    val rawR: Int,
    val rawG: Int,
    val rawB: Int,
    val calibratedR: Int,
    val calibratedG: Int,
    val calibratedB: Int
) {
    fun toHexString(): String {
        return String.format("#%02X%02X%02X", calibratedR, calibratedG, calibratedB)
    }

    fun toColorInt(): Int {
        return android.graphics.Color.rgb(calibratedR, calibratedG, calibratedB)
    }
}

// ── Color Stop from JSON ──
data class ColorStop(
    val phMin: Float,
    val phMax: Float,
    val hex: String,
    val hue: Float,
    val saturation: Float,
    val value: Float,
    val label: String,
    val description: String,
    val freshnessScore: Int,
    val edible: Boolean
) {
    val phMidpoint: Float get() = (phMin + phMax) / 2f
}

// ── Food Category from JSON ──
data class FoodCategory(
    val name: String,
    val examples: List<String>,
    val typicalFreshPh: Float,
    val spoilageThresholdPh: Float,
    val spoilageRate: String,
    val defaultShelfLifeHours: Int,
    val safetyNotes: String
)

// ── Interpolation Config from JSON ──
data class InterpolationConfig(
    val primaryWeightHue: Float = 0.7f,
    val secondaryWeightSaturation: Float = 0.2f,
    val tertiaryWeightValue: Float = 0.1f,
    val minConfidenceThreshold: Float = 0.4f,
    val sensorCircleRadiusRatio: Float = 0.12f,
    val calibrationSampleCount: Int = 50
)

// ── Color Analysis Result ──
data class ColorAnalysisResult(
    val estimatedPh: Float,
    val confidence: Float,
    val matchedStopIndex: Int,
    val interpolationFactor: Float,
    val matchedLabel: String
)

// ── Freshness Engine Output ──
data class FreshnessResult(
    val overallScore: Int,
    val status: FreshnessStatus,
    val estimatedPh: Float,
    val detectedColor: CalibratedColor,
    val foodInfo: FoodInfo,
    val remainingHours: Int,
    val edible: Boolean,
    val confidence: Float,
    val recommendations: List<String>,
    val statusLabel: String,
    val statusDescription: String,
    val colorLabel: String,
    val safetyNotes: String,
    val scanTimestamp: Long = System.currentTimeMillis()
)

enum class FreshnessStatus(val displayName: String, val emoji: String) {
    EXCELLENT("Excellent", "✨"),
    GOOD("Good", "👍"),
    FAIR("Fair", "⚠️"),
    CAUTION("Caution", "🟡"),
    SPOILED("Spoiled", "🔴"),
    DANGEROUS("Dangerous", "☠️")
}

// ── Calibration Data ──
data class CalibrationResult(
    val gainR: Float,
    val gainG: Float,
    val gainB: Float,
    val offsetR: Float,
    val offsetG: Float,
    val offsetB: Float,
    val isValid: Boolean,
    val blackLuminance: Float = 0f,
    val whiteLuminance: Float = 0f
)

// ── Room Entity for Scan History ──
@Entity(tableName = "scan_history")
data class ScanRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val productName: String,
    val category: String,
    val freshnessScore: Int,
    val status: String,
    val estimatedPh: Float,
    val detectedColorHex: String,
    val detectedColorInt: Int,
    val scanTimestamp: Long,
    val manufacturingDate: String,
    val expiryDate: String,
    val batch: String,
    val remainingHours: Int,
    val edible: Boolean,
    val confidence: Float,
    val statusLabel: String,
    val statusDescription: String,
    val colorLabel: String,
    val safetyNotes: String,
    val recommendationsJson: String
)
