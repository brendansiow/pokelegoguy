package com.pokelegoguy.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.pokelegoguy.App

class BotAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        App.instance.accessibilityService = this
        Log.i(TAG, "Accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        App.instance.accessibilityService = null
        Log.i(TAG, "Accessibility service disconnected")
        return super.onUnbind(intent)
    }

    fun performTap(x: Float, y: Float, durationMs: Long) {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        val dispatched = dispatchGesture(gesture, null, null)
        if (!dispatched) Log.w(TAG, "dispatchGesture returned false for tap at ($x, $y)")
    }

    companion object {
        private const val TAG = "BotAccessibilityService"
    }
}
