package com.pokelegoguy.service

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.core.app.ServiceCompat
import com.pokelegoguy.App
import com.pokelegoguy.ai.AIProviderException
import com.pokelegoguy.ai.AIProviderFactory
import com.pokelegoguy.config.ConfigRepository
import com.pokelegoguy.controller.TapController
import com.pokelegoguy.game.BattlePhase
import com.pokelegoguy.game.GameStateParser
import com.pokelegoguy.game.parseAction
import com.pokelegoguy.ocr.OCRProcessor
import com.pokelegoguy.ocr.RegionDef
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class BotForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private lateinit var screenCaptureManager: ScreenCaptureManager
    private lateinit var ocrProcessor: OCRProcessor
    private lateinit var gameStateParser: GameStateParser
    private lateinit var tapController: TapController
    private lateinit var configRepo: ConfigRepository

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        configRepo = ConfigRepository(this)
        val config = configRepo.loadConfig()

        val apiKey = configRepo.getApiKey(config.aiProvider)
            ?: run { Log.e(TAG, "No API key found for ${config.aiProvider}"); stopSelf(); return START_NOT_STICKY }

        val aiProvider = AIProviderFactory.create(config, apiKey)

        // Resolve screen dimensions
        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val displayMetrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        val screenDensity = displayMetrics.densityDpi

        // Start foreground notification — must happen before MediaProjection setup on API 34+
        val notification = buildNotification("Bot running…")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(this, App.NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(App.NOTIFICATION_ID, notification)
        }

        // Set up MediaProjection
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, -1) ?: -1
        @Suppress("DEPRECATION")
        val projectionData: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(EXTRA_PROJECTION_DATA, Intent::class.java)
        } else {
            intent?.getParcelableExtra(EXTRA_PROJECTION_DATA)
        }

        if (resultCode == -1 || projectionData == null) {
            Log.e(TAG, "Missing MediaProjection extras — stopping")
            stopSelf()
            return START_NOT_STICKY
        }

        screenCaptureManager = ScreenCaptureManager(this, screenWidth, screenHeight, screenDensity)
        screenCaptureManager.start(resultCode, projectionData)

        ocrProcessor = OCRProcessor()
        gameStateParser = GameStateParser(config)
        tapController = TapController()

        // Build a map of region label → RegionDef from config
        val regionMap = buildRegionMap(config.regions)

        serviceScope.launch {
            // Watch for MediaProjection revocation
            launch {
                screenCaptureManager.projectionRevokedEvent.collect {
                    updateNotification("Screen capture revoked — stopping")
                    stopSelf()
                }
            }

            // Main tick loop
            while (isActive) {
                val tickStart = System.currentTimeMillis()
                try {
                    tick(aiProvider, regionMap, screenWidth, screenHeight, config)
                } catch (e: AIProviderException) {
                    Log.e(TAG, "AI error: ${e.message}")
                    updateNotification("AI error — retrying…")
                    if (e.message?.contains("rate limited") == true) {
                        delay(config.tickIntervalMs * 5)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Unexpected tick error: ${e.message}", e)
                }
                val elapsed = System.currentTimeMillis() - tickStart
                val remaining = config.tickIntervalMs - elapsed
                if (remaining > 0) delay(remaining)
            }
        }

        return START_NOT_STICKY
    }

    private suspend fun tick(
        aiProvider: com.pokelegoguy.ai.AIProvider,
        regionMap: Map<String, RegionDef>,
        screenWidth: Int,
        screenHeight: Int,
        config: com.pokelegoguy.config.BotConfig
    ) {
        val bitmap = screenCaptureManager.captureFrame() ?: return

        val ocrResults = ocrProcessor.recognizeAllRegions(bitmap, regionMap, screenWidth, screenHeight)
        val state = gameStateParser.parse(ocrResults)

        Log.d(TAG, "Phase: ${state.phase} | Player: ${state.playerPokemon?.name} | Opponent: ${state.opponentPokemon?.name}")

        if (state.phase != BattlePhase.MOVE_SELECTION) {
            bitmap.recycle()
            return
        }

        val rawAction = aiProvider.recommendAction(bitmap, state.toPromptText())
        bitmap.recycle()

        val action = parseAction(rawAction)
        Log.i(TAG, "AI recommended: '$rawAction' → $action")

        tapController.executeAction(action, screenWidth, screenHeight, config)
        delay(config.postTapDelayMs)
    }

    private fun buildRegionMap(regions: com.pokelegoguy.config.RegionConfig): Map<String, RegionDef> = mapOf(
        "playerPokemonName"   to regions.playerPokemonName,
        "playerHpBar"         to regions.playerHpBar,
        "playerHpText"        to regions.playerHpText,
        "opponentPokemonName" to regions.opponentPokemonName,
        "opponentHpBar"       to regions.opponentHpBar,
        "opponentHpText"      to regions.opponentHpText,
        "moveButton1"         to regions.moveButton1,
        "moveButton2"         to regions.moveButton2,
        "moveButton3"         to regions.moveButton3,
        "moveButton4"         to regions.moveButton4,
        "battlePhaseIndicator" to regions.battlePhaseIndicator
    )

    private fun buildNotification(text: String): Notification =
        android.app.Notification.Builder(this, App.NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Pokemon Champions Bot")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .build()

    private fun updateNotification(text: String) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(App.NOTIFICATION_ID, buildNotification(text))
    }

    override fun onDestroy() {
        serviceScope.cancel()
        runCatching { screenCaptureManager.stop() }
        runCatching { ocrProcessor.close() }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_PROJECTION_DATA = "projection_data"
        private const val TAG = "BotForegroundService"
    }
}
