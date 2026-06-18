package com.pokelegoguy.config

import com.pokelegoguy.ocr.RegionDef
import kotlinx.serialization.Serializable

enum class AIProviderType { OPENAI, ANTHROPIC, GEMINI }

@Serializable
data class TapPoint(val xFraction: Float, val yFraction: Float)

@Serializable
data class TapTargetConfig(
    val move1: TapPoint = TapPoint(0.25f, 0.83f),
    val move2: TapPoint = TapPoint(0.75f, 0.83f),
    val move3: TapPoint = TapPoint(0.25f, 0.94f),
    val move4: TapPoint = TapPoint(0.75f, 0.94f)
)

@Serializable
data class RegionConfig(
    val playerPokemonName: RegionDef = RegionDef(0.05f, 0.55f, 0.50f, 0.65f),
    val playerHpBar: RegionDef      = RegionDef(0.05f, 0.65f, 0.50f, 0.72f),
    val playerHpText: RegionDef     = RegionDef(0.05f, 0.66f, 0.30f, 0.73f),
    val opponentPokemonName: RegionDef = RegionDef(0.50f, 0.10f, 0.95f, 0.20f),
    val opponentHpBar: RegionDef    = RegionDef(0.50f, 0.20f, 0.95f, 0.27f),
    val opponentHpText: RegionDef   = RegionDef(0.65f, 0.21f, 0.90f, 0.28f),
    val moveButton1: RegionDef      = RegionDef(0.02f, 0.78f, 0.48f, 0.88f),
    val moveButton2: RegionDef      = RegionDef(0.52f, 0.78f, 0.98f, 0.88f),
    val moveButton3: RegionDef      = RegionDef(0.02f, 0.89f, 0.48f, 0.99f),
    val moveButton4: RegionDef      = RegionDef(0.52f, 0.89f, 0.98f, 0.99f),
    val battlePhaseIndicator: RegionDef = RegionDef(0.02f, 0.75f, 0.98f, 0.80f)
)

@Serializable
data class BotConfig(
    val aiProvider: AIProviderType = AIProviderType.OPENAI,
    val openAiModel: String = "gpt-4o",
    val anthropicModel: String = "claude-opus-4-5",
    val geminiModel: String = "gemini-1.5-pro",
    val tickIntervalMs: Long = 2000L,
    val tapDurationMs: Long = 100L,
    val postTapDelayMs: Long = 500L,
    val regions: RegionConfig = RegionConfig(),
    val tapTargets: TapTargetConfig = TapTargetConfig(),
    val debugScreenshots: Boolean = false
)
