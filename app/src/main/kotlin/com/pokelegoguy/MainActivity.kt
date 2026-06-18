package com.pokelegoguy

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.pokelegoguy.config.AIProviderType
import com.pokelegoguy.databinding.ActivityMainBinding
import com.pokelegoguy.service.BotAccessibilityService
import com.pokelegoguy.service.BotForegroundService
import com.pokelegoguy.service.OverlayService

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val configRepo by lazy { App.instance.configRepository }

    // ─── Activity result launchers ────────────────────────────────────────────

    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            startBotService(result.resultCode, result.data!!)
        } else {
            updateBotStatus("Screen capture permission denied.")
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) launchProjectionDialog()
        else Toast.makeText(this, "Notification permission needed for bot status", Toast.LENGTH_SHORT).show()
    }

    // Returns from ACTION_MANAGE_OVERLAY_PERMISSION settings screen
    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // onResume will call syncOverlayCard() to refresh status
    }

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupProviderSpinner()
        loadCurrentConfig()

        binding.btnSaveConfig.setOnClickListener { saveConfig() }
        binding.btnOpenAccessibility.setOnClickListener { openAccessibilitySettings() }
        binding.btnStartStop.setOnClickListener { onStartStopClicked() }
        binding.btnShowOverlay.setOnClickListener { onShowOverlayClicked() }
        binding.btnHideOverlay.setOnClickListener { onHideOverlayClicked() }
    }

    override fun onResume() {
        super.onResume()
        updateAccessibilityStatus()
        syncOverlayCard()
        // Keep Start/Stop label in sync with actual bot state
        if (App.instance.isBotRunning.value) {
            binding.btnStartStop.text = "Stop Bot"
            binding.tvBotStatus.text = "Bot is running…"
        }
    }

    // ─── Provider spinner ─────────────────────────────────────────────────────

    private fun setupProviderSpinner() {
        val providers = AIProviderType.values().map { it.name }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, providers)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerProvider.adapter = adapter
    }

    private fun loadCurrentConfig() {
        val config = configRepo.loadConfig()
        val providerIndex = AIProviderType.values().indexOf(config.aiProvider)
        binding.spinnerProvider.setSelection(providerIndex.coerceAtLeast(0))
        val model = when (config.aiProvider) {
            AIProviderType.OPENAI    -> config.openAiModel
            AIProviderType.ANTHROPIC -> config.anthropicModel
            AIProviderType.GEMINI    -> config.geminiModel
        }
        binding.editModel.setText(model)
        if (configRepo.getApiKey(config.aiProvider) != null) {
            binding.editApiKey.hint = "API key saved (enter new to replace)"
        }
    }

    private fun saveConfig() {
        val selectedProvider = AIProviderType.values()[binding.spinnerProvider.selectedItemPosition]
        val model = binding.editModel.text?.toString()?.trim().orEmpty()
        val apiKey = binding.editApiKey.text?.toString()?.trim().orEmpty()

        var config = configRepo.loadConfig().copy(aiProvider = selectedProvider)
        config = when (selectedProvider) {
            AIProviderType.OPENAI    -> config.copy(openAiModel = model.ifEmpty { config.openAiModel })
            AIProviderType.ANTHROPIC -> config.copy(anthropicModel = model.ifEmpty { config.anthropicModel })
            AIProviderType.GEMINI    -> config.copy(geminiModel = model.ifEmpty { config.geminiModel })
        }
        configRepo.saveConfig(config)

        if (apiKey.isNotEmpty()) {
            configRepo.saveApiKey(selectedProvider, apiKey)
            binding.editApiKey.text?.clear()
            binding.editApiKey.hint = "API key saved (enter new to replace)"
        }

        Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
    }

    // ─── Bot start / stop ─────────────────────────────────────────────────────

    private fun onStartStopClicked() {
        if (App.instance.isBotRunning.value) {
            stopService(Intent(this, BotForegroundService::class.java))
            updateBotStatus("Bot stopped.")
            binding.btnStartStop.text = "Start Bot"
        } else {
            if (!isAccessibilityServiceEnabled()) {
                Toast.makeText(this, "Please enable the Accessibility Service first", Toast.LENGTH_LONG).show()
                openAccessibilitySettings()
                return
            }
            val config = configRepo.loadConfig()
            if (configRepo.getApiKey(config.aiProvider) == null) {
                Toast.makeText(this, "Please save an API key first", Toast.LENGTH_LONG).show()
                return
            }
            requestProjectionPermission()
        }
    }

    private fun requestProjectionPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        launchProjectionDialog()
    }

    private fun launchProjectionDialog() {
        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projectionLauncher.launch(manager.createScreenCaptureIntent())
    }

    private fun startBotService(resultCode: Int, data: Intent) {
        val intent = Intent(this, BotForegroundService::class.java).apply {
            putExtra(BotForegroundService.EXTRA_RESULT_CODE, resultCode)
            putExtra(BotForegroundService.EXTRA_PROJECTION_DATA, data)
        }
        ContextCompat.startForegroundService(this, intent)
        updateBotStatus("Bot running… watching for battles.")
        binding.btnStartStop.text = "Stop Bot"
    }

    // ─── Overlay ──────────────────────────────────────────────────────────────

    private fun onShowOverlayClicked() {
        if (!Settings.canDrawOverlays(this)) {
            // Send user to the per-app overlay permission settings screen
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            overlayPermissionLauncher.launch(intent)
        } else {
            ContextCompat.startForegroundService(this, Intent(this, OverlayService::class.java))
            Toast.makeText(this, "Overlay launched — switch to the game", Toast.LENGTH_SHORT).show()
        }
    }

    private fun onHideOverlayClicked() {
        stopService(Intent(this, OverlayService::class.java))
    }

    private fun syncOverlayCard() {
        val canDraw = Settings.canDrawOverlays(this)
        binding.tvOverlayPermStatus.text = if (canDraw) "Permission: granted ✓" else "Permission: not granted"
        binding.tvOverlayPermStatus.setTextColor(
            if (canDraw) getColor(android.R.color.holo_green_dark)
            else getColor(android.R.color.holo_orange_dark)
        )
        binding.btnShowOverlay.text = if (canDraw) "Show Overlay" else "Grant Permission"
    }

    // ─── Accessibility status ─────────────────────────────────────────────────

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun updateAccessibilityStatus() {
        val enabled = isAccessibilityServiceEnabled()
        binding.tvAccessibilityStatus.text = if (enabled) "ENABLED ✓" else "DISABLED"
        binding.tvAccessibilityStatus.setTextColor(
            if (enabled) getColor(android.R.color.holo_green_dark)
            else getColor(android.R.color.holo_red_dark)
        )
    }

    private fun updateBotStatus(message: String) {
        binding.tvBotStatus.text = message
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val prefString = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val target = "${packageName}/${BotAccessibilityService::class.java.canonicalName}"
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(prefString)
        while (splitter.hasNext()) {
            if (splitter.next().equals(target, ignoreCase = true)) return true
        }
        return false
    }
}
