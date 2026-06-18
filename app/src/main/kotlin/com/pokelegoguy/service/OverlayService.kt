package com.pokelegoguy.service

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.pokelegoguy.App
import com.pokelegoguy.MainActivity
import com.pokelegoguy.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.abs

class OverlayService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: View
    private lateinit var params: WindowManager.LayoutParams

    // Drag state
    private var dragInitialX = 0
    private var dragInitialY = 0
    private var dragInitialTouchX = 0f
    private var dragInitialTouchY = 0f
    private var isDragging = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Settings.canDrawOverlays(this)) {
            Log.e(TAG, "SYSTEM_ALERT_WINDOW not granted — stopping")
            stopSelf()
            return START_NOT_STICKY
        }
        if (!::overlayView.isInitialized) {
            setupOverlay()
        }
        return START_STICKY
    }

    private fun setupOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_control, null)

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // NOT_FOCUSABLE: overlay doesn't steal focus/keyboard from the game
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 20
            y = 250
        }

        bindCollapsedView()
        bindExpandedView()
        observeBotState()

        windowManager.addView(overlayView, params)
        Log.i(TAG, "Overlay added to WindowManager")
    }

    // ─── Collapsed ────────────────────────────────────────────────────────────

    private fun bindCollapsedView() {
        overlayView.findViewById<View>(R.id.cardCollapsed)
            .setOnTouchListener(makeDragOrClickListener { showExpanded() })
    }

    // ─── Expanded ─────────────────────────────────────────────────────────────

    private fun bindExpandedView() {
        // Drag handle lets the user move the overlay while expanded
        overlayView.findViewById<View>(R.id.viewDragHandle)
            .setOnTouchListener(makeDragOrClickListener())

        // Minimise → back to pill
        overlayView.findViewById<TextView>(R.id.btnMinimize)
            .setOnClickListener { showCollapsed() }

        // Open App button: bring MainActivity to front
        overlayView.findViewById<MaterialButton>(R.id.btnOverlayOpenApp)
            .setOnClickListener {
                startActivity(
                    Intent(this, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    }
                )
            }

        // Stop Bot button: only visible when bot is running
        overlayView.findViewById<MaterialButton>(R.id.btnOverlayStop)
            .setOnClickListener {
                stopService(Intent(this, BotForegroundService::class.java))
            }
    }

    // ─── State observation ────────────────────────────────────────────────────

    private fun observeBotState() {
        serviceScope.launch {
            App.instance.isBotRunning.collect { running ->
                updateStatusDisplay(running)
            }
        }
    }

    private fun updateStatusDisplay(running: Boolean) {
        val dotView     = overlayView.findViewById<View>(R.id.viewStatusDot)
        val tvStatus    = overlayView.findViewById<TextView>(R.id.tvOverlayStatus)
        val tvLastAction = overlayView.findViewById<TextView>(R.id.tvLastAction)
        val btnStop     = overlayView.findViewById<MaterialButton>(R.id.btnOverlayStop)
        val btnOpenApp  = overlayView.findViewById<MaterialButton>(R.id.btnOverlayOpenApp)

        if (running) {
            dotView.setBackgroundResource(R.drawable.status_dot_green)
            tvStatus.text = "● Running"
            tvStatus.setTextColor(0xFF55EE55.toInt())
            tvLastAction.text = "Last action: ${App.instance.lastBotAction}"
            btnStop.visibility = View.VISIBLE
            btnOpenApp.text = "Settings"
        } else {
            dotView.setBackgroundResource(R.drawable.status_dot_gray)
            tvStatus.text = "● Stopped"
            tvStatus.setTextColor(0xFFAAAAAA.toInt())
            tvLastAction.text = "Last action: —"
            btnStop.visibility = View.GONE
            btnOpenApp.text = "Open App"
        }
    }

    // ─── Visibility toggle ────────────────────────────────────────────────────

    private fun showExpanded() {
        overlayView.findViewById<View>(R.id.cardCollapsed).visibility = View.GONE
        overlayView.findViewById<View>(R.id.cardExpanded).visibility = View.VISIBLE
        // Refresh last action when expanding
        if (App.instance.isBotRunning.value) {
            overlayView.findViewById<TextView>(R.id.tvLastAction).text =
                "Last action: ${App.instance.lastBotAction}"
        }
    }

    private fun showCollapsed() {
        overlayView.findViewById<View>(R.id.cardCollapsed).visibility = View.VISIBLE
        overlayView.findViewById<View>(R.id.cardExpanded).visibility = View.GONE
    }

    // ─── Drag helper ──────────────────────────────────────────────────────────

    /**
     * Returns a touch listener that:
     *  - Drags the overlay window on MOVE
     *  - Calls [onClick] on UP if the gesture was a tap (not a drag)
     */
    private fun makeDragOrClickListener(onClick: (() -> Unit)? = null): View.OnTouchListener {
        return View.OnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dragInitialX = params.x
                    dragInitialY = params.y
                    dragInitialTouchX = event.rawX
                    dragInitialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - dragInitialTouchX).toInt()
                    val dy = (event.rawY - dragInitialTouchY).toInt()
                    if (abs(dx) > 6 || abs(dy) > 6) {
                        isDragging = true
                        params.x = (dragInitialX + dx).coerceAtLeast(0)
                        params.y = (dragInitialY + dy).coerceAtLeast(0)
                        windowManager.updateViewLayout(overlayView, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) onClick?.invoke()
                    true
                }
                else -> false
            }
        }
    }

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    override fun onDestroy() {
        serviceScope.cancel()
        if (::overlayView.isInitialized) {
            runCatching { windowManager.removeView(overlayView) }
        }
        Log.i(TAG, "Overlay removed")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "OverlayService"
    }
}
