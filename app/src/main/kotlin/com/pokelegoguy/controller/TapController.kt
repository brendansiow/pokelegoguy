package com.pokelegoguy.controller

import android.util.Log
import com.pokelegoguy.App
import com.pokelegoguy.config.BotConfig
import com.pokelegoguy.config.TapPoint
import com.pokelegoguy.game.BotAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TapController {

    suspend fun executeAction(
        action: BotAction,
        screenWidth: Int,
        screenHeight: Int,
        config: BotConfig
    ) {
        val service = App.instance.accessibilityService
        if (service == null) {
            Log.w(TAG, "AccessibilityService not connected — skipping tap for $action")
            return
        }
        val tapPoint: TapPoint = when (action) {
            BotAction.MOVE_1 -> config.tapTargets.move1
            BotAction.MOVE_2 -> config.tapTargets.move2
            BotAction.MOVE_3 -> config.tapTargets.move3
            BotAction.MOVE_4 -> config.tapTargets.move4
            BotAction.SKIP   -> { Log.d(TAG, "SKIP action — no tap"); return }
        }
        val x = tapPoint.xFraction * screenWidth
        val y = tapPoint.yFraction * screenHeight
        Log.d(TAG, "Tapping $action at ($x, $y) [${tapPoint.xFraction}×${tapPoint.yFraction}]")
        withContext(Dispatchers.Main) {
            service.performTap(x, y, config.tapDurationMs)
        }
    }

    companion object {
        private const val TAG = "TapController"
    }
}
