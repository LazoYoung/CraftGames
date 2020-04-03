package com.github.lazoyoung.craftgames.event

import com.github.lazoyoung.craftgames.api.PlayerType
import com.github.lazoyoung.craftgames.game.Game
import org.bukkit.entity.Player
import org.bukkit.event.HandlerList

class PlayerJoinGamePostEvent(
        game: Game,
        private val player: Player,
        private val playerType: PlayerType
) : GameEvent(game) {

    companion object {
        private val handlerList = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList {
            return this.handlerList
        }
    }

    override fun getHandlers(): HandlerList {
        return getHandlerList()
    }

    fun getPlayer(): Player {
        return player
    }

    fun getPlayerType(): PlayerType {
        return playerType
    }

    fun isGameStarted(): Boolean {
        return game.phase == Game.Phase.PLAYING
    }
}