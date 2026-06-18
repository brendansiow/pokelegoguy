package com.pokelegoguy

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.pokelegoguy.config.ConfigRepository
import com.pokelegoguy.controller.TapController
import com.pokelegoguy.service.BotAccessibilityService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class App : Application() {

    val configRepository: ConfigRepository by lazy { ConfigRepository(this) }
    val tapController: TapController by lazy { TapController() }

    // Set by BotAccessibilityService when it connects/disconnects
    @Volatile
    var accessibilityService: BotAccessibilityService? = null

    // Observed by OverlayService to keep the floating widget in sync
    private val _isBotRunning = MutableStateFlow(false)
    val isBotRunning: StateFlow<Boolean> get() = _isBotRunning

    // Last AI action token — updated by BotForegroundService on every tick
    @Volatile
    var lastBotAction: String = "—"

    fun setBotRunning(running: Boolean) {
        _isBotRunning.value = running
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Bot Status",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows when the Pokemon Champions bot is running"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        lateinit var instance: App
            private set

        const val NOTIFICATION_CHANNEL_ID = "bot_channel"
        const val NOTIFICATION_ID = 1
    }
}
