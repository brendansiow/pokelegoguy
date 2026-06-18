package com.pokelegoguy.config

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ConfigRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences("bot_config", Context.MODE_PRIVATE)
    }

    private val encryptedPrefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "bot_secrets",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun saveConfig(config: BotConfig) {
        prefs.edit().putString(KEY_CONFIG, json.encodeToString(config)).apply()
    }

    fun loadConfig(): BotConfig {
        val raw = prefs.getString(KEY_CONFIG, null) ?: return BotConfig()
        return runCatching { json.decodeFromString<BotConfig>(raw) }.getOrDefault(BotConfig())
    }

    fun saveApiKey(provider: AIProviderType, key: String) {
        encryptedPrefs.edit().putString(provider.name, key).apply()
    }

    fun getApiKey(provider: AIProviderType): String? {
        return encryptedPrefs.getString(provider.name, null)?.takeIf { it.isNotBlank() }
    }

    fun clearApiKey(provider: AIProviderType) {
        encryptedPrefs.edit().remove(provider.name).apply()
    }

    companion object {
        private const val KEY_CONFIG = "config_json"
    }
}
