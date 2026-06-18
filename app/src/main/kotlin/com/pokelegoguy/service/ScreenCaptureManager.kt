package com.pokelegoguy.service

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class ScreenCaptureManager(
    private val context: Context,
    val screenWidth: Int,
    val screenHeight: Int,
    private val screenDensity: Int
) {
    private var mediaProjection: MediaProjection? = null
    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null

    private val _projectionRevokedEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val projectionRevokedEvent: SharedFlow<Unit> = _projectionRevokedEvent.asSharedFlow()

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            Log.w(TAG, "MediaProjection revoked by user or system")
            _projectionRevokedEvent.tryEmit(Unit)
        }
    }

    fun start(resultCode: Int, data: Intent) {
        val manager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val projection = manager.getMediaProjection(resultCode, data)
        projection.registerCallback(projectionCallback, null)
        mediaProjection = projection

        val reader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2)
        imageReader = reader

        virtualDisplay = projection.createVirtualDisplay(
            "BotCapture",
            screenWidth, screenHeight, screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface, null, null
        )
        Log.i(TAG, "Screen capture started: ${screenWidth}x${screenHeight} @ ${screenDensity}dpi")
    }

    fun captureFrame(): Bitmap? {
        val reader = imageReader ?: return null
        val image = reader.acquireLatestImage() ?: return null
        return try {
            val plane = image.planes[0]
            val buffer = plane.buffer
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            // rowStride may include alignment padding — width in pixels from the buffer
            val bufferWidth = rowStride / pixelStride

            val bitmap = Bitmap.createBitmap(bufferWidth, screenHeight, Bitmap.Config.ARGB_8888)
            bitmap.copyPixelsFromBuffer(buffer)

            // Crop away any row-padding if present
            if (bufferWidth != screenWidth) {
                Bitmap.createBitmap(bitmap, 0, 0, screenWidth, screenHeight)
                    .also { bitmap.recycle() }
            } else {
                bitmap
            }
        } finally {
            image.close()
        }
    }

    fun stop() {
        runCatching { virtualDisplay?.release() }
        runCatching { imageReader?.close() }
        runCatching { mediaProjection?.stop() }
        virtualDisplay = null
        imageReader = null
        mediaProjection = null
        Log.i(TAG, "Screen capture stopped")
    }

    companion object {
        private const val TAG = "ScreenCaptureManager"
    }
}
