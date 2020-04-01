package com.github.lazoyoung.craftgames.event

import com.github.lazoyoung.craftgames.api.GameResult
import com.github.lazoyoung.craftgames.game.Game
import org.bukkit.entity.Player
import org.bukkit.event.HandlerList
import org.bukkit.scoreboard.Team

class GameFinishEvent(
        game: Game,
        private val result: GameResult,
        private val team: Team?,
        private val winners: List<Player>?
) : GameEvent(game) {

    companion object {
        private val handlerList = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList {
            return this.handlerList
        }
    }

    override fun getHandlers(): HandlerList {
        return handlerList
    }

    fun getWinners(): List<Player> {
        return this.winners ?: emptyList()
    }

    fun getWinningTeam(): Team? {
        return this.team
    }

    fun getResult(): GameResult {
        return this.result
    }
}