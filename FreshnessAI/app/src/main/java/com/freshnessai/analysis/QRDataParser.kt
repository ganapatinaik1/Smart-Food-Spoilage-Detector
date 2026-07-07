package com.freshnessai.analysis

import com.freshnessai.data.FoodInfo
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException

/**
 * Parses the QR code payload into a FoodInfo data object.
 *
 * Expected QR JSON format:
 * {
 *   "product": "Milk",
 *   "category": "dairy",
 *   "mfg": "2026-05-20",
 *   "exp": "2026-05-30",
 *   "batch": "B2026-A1",
 *   "max_shelf_hours": 240
 * }
 */
class QRDataParser {

    private val gson = Gson()

    /**
     * Parses QR code raw value into FoodInfo.
     *
     * @param rawValue The raw string content of the QR code
     * @return FoodInfo if parsing succeeds, null otherwise
     */
    fun parse(rawValue: String): FoodInfo? {
        return try {
            val json = gson.fromJson(rawValue, QRPayload::class.java) ?: return null

            // Validate required fields
            if (json.product.isNullOrBlank() || json.category.isNullOrBlank()) {
                return null
            }

            FoodInfo(
                product = json.product.trim(),
                category = json.category.trim().lowercase(),
                manufacturingDate = json.mfg?.trim() ?: "",
                expiryDate = json.exp?.trim() ?: "",
                batch = json.batch?.trim() ?: "N/A",
                maxShelfHours = json.max_shelf_hours ?: 168
            )
        } catch (e: JsonSyntaxException) {
            null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Checks if a raw QR value looks like a FreshnessAI payload.
     * Quick check before attempting full parse.
     */
    fun isFreshnessAIQR(rawValue: String): Boolean {
        return rawValue.trimStart().startsWith("{") &&
                rawValue.contains("\"product\"") &&
                rawValue.contains("\"category\"")
    }

    // Internal payload class matching the QR JSON structure
    private data class QRPayload(
        val product: String?,
        val category: String?,
        val mfg: String?,
        val exp: String?,
        val batch: String?,
        val max_shelf_hours: Int?
    )
}
