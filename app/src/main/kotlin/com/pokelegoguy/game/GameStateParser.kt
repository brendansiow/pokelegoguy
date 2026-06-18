package com.pokelegoguy.game

import com.pokelegoguy.config.BotConfig

class GameStateParser(private val config: BotConfig) {

    fun parse(regionTexts: Map<String, String>): BattleState {
        val phase = detectPhase(regionTexts)
        val playerPokemon = parsePokemon(
            nameText = regionTexts["playerPokemonName"].orEmpty(),
            hpText = regionTexts["playerHpText"].orEmpty(),
            isPlayer = true
        )
        val opponentPokemon = parsePokemon(
            nameText = regionTexts["opponentPokemonName"].orEmpty(),
            hpText = regionTexts["opponentHpText"].orEmpty(),
            isPlayer = false
        )
        val moves = parseMoves(regionTexts)
        val rawText = regionTexts.entries.joinToString("\n") { "[${it.key}] ${it.value}" }
        return BattleState(phase, playerPokemon, opponentPokemon, moves, rawText)
    }

    private fun detectPhase(regionTexts: Map<String, String>): BattlePhase {
        val allText = regionTexts.values.joinToString(" ").uppercase()

        // Check for battle end screens first
        if (allText.containsAny("YOU WIN", "VICTORY", "YOU LOSE", "DEFEAT", "BATTLE OVER")) {
            return BattlePhase.BATTLE_END
        }

        // Check for switch screen
        if (allText.containsAny("CHOOSE A POKEMON", "SEND OUT", "SWITCH")) {
            return BattlePhase.SWITCH_POKEMON
        }

        // Move selection: at least 2 move button regions have non-trivial text
        val moveTexts = listOf("moveButton1", "moveButton2", "moveButton3", "moveButton4")
            .mapNotNull { regionTexts[it]?.trim() }
            .count { it.length >= 3 }
        if (moveTexts >= 2) {
            return BattlePhase.MOVE_SELECTION
        }

        // If we see Pokemon names but no move buttons, it's an animation/waiting phase
        val hasPlayerName = regionTexts["playerPokemonName"]?.trim()?.length ?: 0 >= 2
        val hasOpponentName = regionTexts["opponentPokemonName"]?.trim()?.length ?: 0 >= 2
        if (hasPlayerName || hasOpponentName) {
            return BattlePhase.ANIMATION
        }

        return BattlePhase.UNKNOWN
    }

    private fun parsePokemon(nameText: String, hpText: String, isPlayer: Boolean): Pokemon? {
        val name = nameText.lines().firstOrNull { it.trim().length >= 2 }?.trim() ?: return null
        val (currentHp, maxHp) = parseHp(hpText)
        val hpPercent = if (maxHp > 0) currentHp.toFloat() / maxHp else 0f
        val status = parseStatus(hpText + " " + nameText)
        return Pokemon(
            name = name,
            currentHp = currentHp,
            maxHp = maxHp,
            hpPercent = hpPercent,
            status = status,
            isPlayer = isPlayer
        )
    }

    private fun parseMoves(regionTexts: Map<String, String>): List<Move> {
        return listOf("moveButton1", "moveButton2", "moveButton3", "moveButton4")
            .mapIndexed { index, key ->
                val text = regionTexts[key]?.trim().orEmpty()
                if (text.length < 2) return@mapIndexed null
                val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
                val name = lines.firstOrNull() ?: return@mapIndexed null
                val pp = lines.find { it.contains("/") && it.any { c -> c.isDigit() } }.orEmpty()
                val type = lines.find { it.length in 3..10 && it.all { c -> c.isLetter() } && it != name }.orEmpty()
                Move(index = index, name = name, type = type, pp = pp)
            }
            .filterNotNull()
    }

    private fun parseHp(text: String): Pair<Int, Int> {
        // Try "NNN/NNN" format first
        val match = Regex("""(\d+)\s*/\s*(\d+)""").find(text)
        if (match != null) {
            return match.groupValues[1].toIntOrNull() to match.groupValues[2].toIntOrNull()
                ?.let { Pair(match.groupValues[1].toInt(), it) } ?: Pair(0, 0)
        }
        // Try standalone number (just current HP)
        val single = Regex("""\b(\d+)\b""").find(text)
        if (single != null) {
            return Pair(single.groupValues[1].toIntOrNull() ?: 0, 0)
        }
        return Pair(0, 0)
    }

    private fun parseStatus(text: String): StatusCondition {
        val upper = text.uppercase()
        return when {
            upper.containsAny("BRN", "BURN") -> StatusCondition.BURN
            upper.containsAny("PSN", "POISON", "TOX") -> StatusCondition.POISON
            upper.containsAny("PAR", "PARALYSIS", "PARA") -> StatusCondition.PARALYSIS
            upper.containsAny("SLP", "SLEEP") -> StatusCondition.SLEEP
            upper.containsAny("FRZ", "FROZEN", "FREEZE") -> StatusCondition.FREEZE
            upper.containsAny("FNT", "FAINT", "KO") -> StatusCondition.FAINT
            else -> StatusCondition.NONE
        }
    }

    private fun String.containsAny(vararg keywords: String): Boolean =
        keywords.any { this.contains(it, ignoreCase = true) }
}
