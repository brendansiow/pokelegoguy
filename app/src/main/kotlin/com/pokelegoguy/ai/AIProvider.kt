package com.pokelegoguy.ai

import android.graphics.Bitmap
import com.pokelegoguy.config.AIProviderType
import com.pokelegoguy.config.BotConfig

const val SYSTEM_PROMPT = """You are an expert Pokemon battle strategist controlling an automated bot.
Analyze the battle screenshot and the structured state below.
Choose the optimal move to win this battle.

Respond with ONLY one of these tokens — nothing else:
MOVE_1
MOVE_2
MOVE_3
MOVE_4

Consider type advantages, damage output, opponent HP, and strategic positioning."""

interface AIProvider {
    @Throws(AIProviderException::class)
    suspend fun recommendAction(bitmap: Bitmap, gameStateText: String): String
}

class AIProviderException(message: String, cause: Throwable? = null) : Exception(message, cause)

object AIProviderFactory {
    fun create(config: BotConfig, apiKey: String): AIProvider = when (config.aiProvider) {
        AIProviderType.OPENAI    -> OpenAIProvider(config.openAiModel, apiKey)
        AIProviderType.ANTHROPIC -> AnthropicProvider(config.anthropicModel, apiKey)
        AIProviderType.GEMINI    -> GeminiProvider(config.geminiModel, apiKey)
    }
}
