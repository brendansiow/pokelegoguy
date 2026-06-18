package com.pokelegoguy.game

enum class BattlePhase {
    MOVE_SELECTION,
    ANIMATION,
    SWITCH_POKEMON,
    BATTLE_END,
    UNKNOWN
}

enum class StatusCondition { NONE, BURN, POISON, PARALYSIS, SLEEP, FREEZE, FAINT }

data class Pokemon(
    val name: String,
    val currentHp: Int,
    val maxHp: Int,
    val hpPercent: Float,
    val status: StatusCondition = StatusCondition.NONE,
    val isPlayer: Boolean
)

data class Move(
    val index: Int,
    val name: String,
    val type: String = "",
    val pp: String = "",
    val isDisabled: Boolean = false
)

data class BattleState(
    val phase: BattlePhase,
    val playerPokemon: Pokemon?,
    val opponentPokemon: Pokemon?,
    val availableMoves: List<Move>,
    val rawOcrText: String
) {
    fun toPromptText(): String {
        val sb = StringBuilder()
        sb.appendLine("=== Battle State ===")
        playerPokemon?.let {
            val hpStr = if (it.maxHp > 0) "${it.currentHp}/${it.maxHp}" else "${(it.hpPercent * 100).toInt()}%"
            sb.appendLine("Your Pokemon: ${it.name} HP $hpStr [${it.status}]")
        } ?: sb.appendLine("Your Pokemon: unknown")
        opponentPokemon?.let {
            val hpStr = if (it.maxHp > 0) "${it.currentHp}/${it.maxHp}" else "${(it.hpPercent * 100).toInt()}%"
            sb.appendLine("Opponent:     ${it.name} HP $hpStr [${it.status}]")
        } ?: sb.appendLine("Opponent: unknown")
        if (availableMoves.isNotEmpty()) {
            sb.appendLine("Available moves:")
            availableMoves.forEach { m ->
                val ppStr = if (m.pp.isNotEmpty()) " PP:${m.pp}" else ""
                val typeStr = if (m.type.isNotEmpty()) " (${m.type})" else ""
                val disabledStr = if (m.isDisabled) " [DISABLED]" else ""
                sb.appendLine("  MOVE_${m.index + 1}: ${m.name}$typeStr$ppStr$disabledStr")
            }
        }
        return sb.toString().trim()
    }
}

enum class BotAction { MOVE_1, MOVE_2, MOVE_3, MOVE_4, SKIP }

fun parseAction(raw: String): BotAction {
    val upper = raw.uppercase().trim()
    return when {
        upper.contains("MOVE_1") || upper.contains("MOVE1") -> BotAction.MOVE_1
        upper.contains("MOVE_2") || upper.contains("MOVE2") -> BotAction.MOVE_2
        upper.contains("MOVE_3") || upper.contains("MOVE3") -> BotAction.MOVE_3
        upper.contains("MOVE_4") || upper.contains("MOVE4") -> BotAction.MOVE_4
        else -> BotAction.SKIP
    }
}
