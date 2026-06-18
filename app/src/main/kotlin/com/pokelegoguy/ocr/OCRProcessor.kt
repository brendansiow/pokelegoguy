package com.pokelegoguy.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class OCRProcessor {

    private val recognizer: TextRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    suspend fun recognizeRegion(
        bitmap: Bitmap,
        region: RegionDef,
        screenWidth: Int,
        screenHeight: Int
    ): String {
        val rect = region.toPixelRect(screenWidth, screenHeight)
        // Guard against degenerate rects
        if (rect.width() <= 0 || rect.height() <= 0) return ""
        val cropped = Bitmap.createBitmap(bitmap, rect.left, rect.top, rect.width(), rect.height())
        return try {
            recognizeBitmap(cropped)
        } finally {
            cropped.recycle()
        }
    }

    suspend fun recognizeAllRegions(
        bitmap: Bitmap,
        regions: Map<String, RegionDef>,
        screenWidth: Int,
        screenHeight: Int
    ): Map<String, String> = coroutineScope {
        regions.map { (label, region) ->
            async { label to recognizeRegion(bitmap, region, screenWidth, screenHeight) }
        }.awaitAll().toMap()
    }

    private suspend fun recognizeBitmap(bitmap: Bitmap): String = suspendCoroutine { cont ->
        val image = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(image)
            .addOnSuccessListener { result -> cont.resume(result.text) }
            .addOnFailureListener { e -> cont.resumeWithException(e) }
    }

    fun close() {
        recognizer.close()
    }
}
