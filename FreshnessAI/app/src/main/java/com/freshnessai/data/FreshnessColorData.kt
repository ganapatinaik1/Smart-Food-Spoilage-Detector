package com.freshnessai.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken

/**
 * Loads and provides the color-to-pH mapping data from the assets JSON file.
 * This is the central data source — edit freshness_color_data.json to recalibrate.
 */
class FreshnessColorData private constructor(
    val colorStops: List<ColorStop>,
    val foodCategories: Map<String, FoodCategory>,
    val interpolationConfig: InterpolationConfig
) {
    companion object {
        @Volatile
        private var instance: FreshnessColorData? = null

        fun getInstance(context: Context): FreshnessColorData {
            return instance ?: synchronized(this) {
                instance ?: loadFromAssets(context).also { instance = it }
            }
        }

        private fun loadFromAssets(context: Context): FreshnessColorData {
            val json = context.assets.open("freshness_color_data.json")
                .bufferedReader().use { it.readText() }
            val gson = Gson()
            val root = gson.fromJson(json, JsonObject::class.java)

            // Parse color stops
            val stopsType = object : TypeToken<List<ColorStopJson>>() {}.type
            val stopsJson: List<ColorStopJson> = gson.fromJson(root.getAsJsonArray("color_stops"), stopsType)
            val colorStops = stopsJson.map { it.toColorStop() }

            // Parse food categories
            val categoriesObj = root.getAsJsonObject("food_categories")
            val foodCategories = mutableMapOf<String, FoodCategory>()
            for (key in categoriesObj.keySet()) {
                val catJson = gson.fromJson(categoriesObj.getAsJsonObject(key), FoodCategoryJson::class.java)
                foodCategories[key] = catJson.toFoodCategory()
            }

            // Parse interpolation config
            val configObj = root.getAsJsonObject("interpolation_config")
            val config = if (configObj != null) {
                InterpolationConfig(
                    primaryWeightHue = configObj.get("primary_weight_hue")?.asFloat ?: 0.7f,
                    secondaryWeightSaturation = configObj.get("secondary_weight_saturation")?.asFloat ?: 0.2f,
                    tertiaryWeightValue = configObj.get("tertiary_weight_value")?.asFloat ?: 0.1f,
                    minConfidenceThreshold = configObj.get("min_confidence_threshold")?.asFloat ?: 0.4f,
                    sensorCircleRadiusRatio = configObj.get("sensor_circle_radius_ratio")?.asFloat ?: 0.12f,
                    calibrationSampleCount = configObj.get("calibration_sample_count")?.asInt ?: 50
                )
            } else {
                InterpolationConfig()
            }

            return FreshnessColorData(colorStops, foodCategories, config)
        }
    }

    fun getCategoryOrDefault(categoryKey: String): FoodCategory {
        return foodCategories[categoryKey.lowercase()] ?: foodCategories["other"]
            ?: FoodCategory(
                name = "Unknown",
                examples = emptyList(),
                typicalFreshPh = 5.0f,
                spoilageThresholdPh = 8.0f,
                spoilageRate = "moderate",
                defaultShelfLifeHours = 120,
                safetyNotes = "No specific safety data available for this food category."
            )
    }

    // ── JSON mapping classes (snake_case) ──
    private data class ColorStopJson(
        val ph_min: Float,
        val ph_max: Float,
        val hex: String,
        val hue: Float,
        val saturation: Float,
        val value: Float,
        val label: String,
        val description: String,
        val freshness_score: Int,
        val edible: Boolean
    ) {
        fun toColorStop() = ColorStop(
            phMin = ph_min, phMax = ph_max, hex = hex,
            hue = hue, saturation = saturation, value = value,
            label = label, description = description,
            freshnessScore = freshness_score, edible = edible
        )
    }

    private data class FoodCategoryJson(
        val name: String,
        val examples: List<String>,
        val typical_fresh_ph: Float,
        val spoilage_threshold_ph: Float,
        val spoilage_rate: String,
        val default_shelf_life_hours: Int,
        val safety_notes: String
    ) {
        fun toFoodCategory() = FoodCategory(
            name = name, examples = examples,
            typicalFreshPh = typical_fresh_ph,
            spoilageThresholdPh = spoilage_threshold_ph,
            spoilageRate = spoilage_rate,
            defaultShelfLifeHours = default_shelf_life_hours,
            safetyNotes = safety_notes
        )
    }
}
