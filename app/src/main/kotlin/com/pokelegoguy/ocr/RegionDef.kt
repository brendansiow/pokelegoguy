package com.pokelegoguy.ocr

import android.graphics.Rect
import kotlinx.serialization.Serializable

/**
 * Bounding box as fractions of screen dimensions (0.0–1.0), origin top-left.
 * Converted to pixel coordinates at runtime using actual screen size.
 */
@Serializable
data class RegionDef(
    val leftFraction: Float,
    val topFraction: Float,
    val rightFraction: Float,
    val bottomFraction: Float
) {
    fun toPixelRect(screenWidth: Int, screenHeight: Int): Rect = Rect(
        (leftFraction * screenWidth).toInt(),
        (topFraction * screenHeight).toInt(),
        (rightFraction * screenWidth).toInt(),
        (bottomFraction * screenHeight).toInt()
    )
}
