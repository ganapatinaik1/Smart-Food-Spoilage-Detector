package com.freshnessai.analysis

import android.graphics.Bitmap
import android.graphics.Color
import com.freshnessai.BuildConfig
import com.freshnessai.data.ScanRecord
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

/**
 * Analyzes food images using the Gemini AI model to determine freshness.
 * Replaces the old QR-code + colorimetry pipeline with AI vision analysis.
 */
class GeminiAnalyzer {

    private val model = GenerativeModel(
        modelName = "gemini-3.1-flash-lite",
        apiKey = BuildConfig.GEMINI_API_KEY
    )

    /**
     * Response structure expected from Gemini.
     * Maps directly to ScanRecord fields.
     */
    data class GeminiResponse(
        @SerializedName("product_name") val productName: String = "Unknown Food",
        @SerializedName("category") val category: String = "general",
        @SerializedName("freshness_score") val freshnessScore: Int = 50,
        @SerializedName("status") val status: String = "FAIR",
        @SerializedName("estimated_ph") val estimatedPh: Float = 7.0f,
        @SerializedName("detected_color_hex") val detectedColorHex: String = "#808080",
        @SerializedName("edible") val edible: Boolean = true,
        @SerializedName("confidence") val confidence: Float = 0.5f,
        @SerializedName("remaining_hours") val remainingHours: Int = 48,
        @SerializedName("status_label") val statusLabel: String = "Fair Condition",
        @SerializedName("status_description") val statusDescription: String = "The food appears to be in fair condition.",
        @SerializedName("color_label") val colorLabel: String = "Neutral",
        @SerializedName("safety_notes") val safetyNotes: String = "Use your best judgment.",
        @SerializedName("recommendations") val recommendations: List<String> = listOf("Inspect carefully before consuming."),
        @SerializedName("manufacturing_date") val manufacturingDate: String = "N/A",
        @SerializedName("expiry_date") val expiryDate: String = "N/A",
        @SerializedName("batch") val batch: String = "N/A"
    )

    /**
     * Sends the captured bitmap to Gemini for freshness analysis.
     * Returns a ScanRecord ready to be inserted into the database.
     */
    suspend fun analyze(bitmap: Bitmap, foodInfo: com.freshnessai.data.FoodInfo, extractedHex: String?): ScanRecord {
        val prompt = buildPrompt(foodInfo, extractedHex)

        val response = model.generateContent(
            content {
                image(bitmap)
                text(prompt)
            }
        )

        val responseText = response.text ?: throw Exception("Gemini returned an empty response")

        // Extract JSON from the response (Gemini may wrap it in markdown code fences)
        val jsonStr = extractJson(responseText)
        val parsed = Gson().fromJson(jsonStr, GeminiResponse::class.java)
            ?: throw Exception("Failed to parse Gemini response")

        // Convert hex color to int (Defaulting to extractedHex, or parsed hex, or GRAY)
        val colorHex = parsed.detectedColorHex.takeIf { it.isNotBlank() && it != "#808080" } ?: extractedHex ?: "#808080"
        val colorInt = try {
            Color.parseColor(colorHex)
        } catch (e: Exception) {
            Color.GRAY
        }

        return ScanRecord(
            productName = parsed.productName,
            category = parsed.category,
            freshnessScore = parsed.freshnessScore.coerceIn(0, 100),
            status = parsed.status.uppercase(),
            estimatedPh = parsed.estimatedPh,
            detectedColorHex = colorHex,
            detectedColorInt = colorInt,
            scanTimestamp = System.currentTimeMillis(),
            manufacturingDate = parsed.manufacturingDate,
            expiryDate = parsed.expiryDate,
            batch = parsed.batch,
            remainingHours = parsed.remainingHours,
            edible = parsed.edible,
            confidence = parsed.confidence.coerceIn(0f, 1f),
            statusLabel = parsed.statusLabel,
            statusDescription = parsed.statusDescription,
            colorLabel = parsed.colorLabel,
            safetyNotes = parsed.safetyNotes,
            recommendationsJson = Gson().toJson(parsed.recommendations)
        )
    }

    private fun buildPrompt(foodInfo: com.freshnessai.data.FoodInfo, extractedHex: String?): String {
        return """
You are a food freshness analysis AI expert.
We have scanned a companion QR code that provides the following baseline context for this food:
- Product: ${foodInfo.product}
- Category: ${foodInfo.category}
- Manufactured: ${foodInfo.manufacturingDate}
- Expiry: ${foodInfo.expiryDate}
- Batch: ${foodInfo.batch}

IMPORTANT: In the middle of the QR code in the image is a circular pH freshness sensor embedded in the sticker.
We have physically extracted the color of this exact sensor circle. The EXACT extracted color is: ${extractedHex ?: "Unknown, analyze from image"}

CRITICAL: Your actual freshness estimation MUST be based physically on the color of that sensor. Use the food context above ONLY for reference.

Use the following "Red Cabbage Color Spectrum" baseline physics to estimate pH and freshness based on the dominant color of the sensor:
- Highly Fresh / Acidic (pH 1–2): Vibrant Red (#E60045) 🟥
- Fresh / Mildly Acidic (pH 3–5): Soft Pink / Mauve (#9B4F96) 🟪
- Fresh / Baseline Neutral (pH 6–7): Deep Purple / Violet (#5F2D79) 🟪
- Early Spoilage (pH 8–9): Ocean Blue / Teal (#2A6BB2) 🟦
- Advanced Spoilage (pH 10–12): Emerald Green (#259646) 🟩
- Extreme Decomposition (pH 13–14): Greenish Yellow (#D1D425) 🟨

Examine visual cues:
- Observe the color spectrum based on the table above and estimate the exact intermediate pH and freshness based on the extracted color (${extractedHex ?: "visible color"}) and the surrounding image.
- Evaluate the actual visible food around the QR code for texture, mold, and bruising to fine-tune the final score.

Return your analysis as a JSON object with EXACTLY these fields (no additional text, just pure JSON):

{
  "product_name": "${foodInfo.product}",
  "category": "${foodInfo.category}",
  "freshness_score": 0-100 integer (100=perfectly fresh, 0=completely spoiled),
  "status": "One of: EXCELLENT, GOOD, FAIR, CAUTION, SPOILED, DANGEROUS",
  "estimated_ph": estimated pH as float (e.g., 6.5, based on the color spectrum),
  "detected_color_hex": "${extractedHex ?: "#"}",
  "edible": true or false,
  "confidence": 0.0-1.0 float representing your confidence in this analysis,
  "remaining_hours": estimated remaining shelf life in hours as integer,
  "status_label": "Short human-readable status (e.g., Very Fresh, Slightly Aged, Past Prime)",
  "status_description": "2-3 sentence description of the food's current state based on color sensor and visuals",
  "color_label": "Name of the detected dominant color (e.g., Emerald Green, Ocean Blue)",
  "safety_notes": "Important safety information about consuming this food",
  "recommendations": ["Array", "of", "3-5 actionable recommendations for the user"],
  "manufacturing_date": "${foodInfo.manufacturingDate}",
  "expiry_date": "${foodInfo.expiryDate}",
  "batch": "${foodInfo.batch}"
}

CRITICAL RULES:
1. Return ONLY the JSON object, no markdown, no explanations, no code fences.
2. The freshness_score and status must be consistent with the pH spectrum table and the sensor color.
3. Be conservative with safety — when in doubt, lean toward lower freshness scores.
4. Status mapping: EXCELLENT(85-100), GOOD(70-84), FAIR(50-69), CAUTION(30-49), SPOILED(10-29), DANGEROUS(0-9)
""".trimIndent()
    }

    /**
     * Extracts JSON from the response text, handling possible markdown code fences.
     */
    private fun extractJson(text: String): String {
        // Find the first '{' and the last '}' in the entire text
        val jsonStart = text.indexOf('{')
        val jsonEnd = text.lastIndexOf('}')
        
        if (jsonStart >= 0 && jsonEnd > jsonStart) {
            return text.substring(jsonStart, jsonEnd + 1)
        }

        throw Exception("No valid JSON found in Gemini response: ${text.take(100)}")
    }
}
