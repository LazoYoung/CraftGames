package com.github.lazoyoung.craftgames.api.event

import com.github.lazoyoung.craftgames.api.PlayerType
import com.github.lazoyoung.craftgames.impl.game.Game
import org.bukkit.entity.Player
import org.bukkit.event.HandlerList

class GameLeaveEvent(
        game: Game,
        private val player: Player,
        private val playerType: PlayerType
): GameEvent(game) {

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
        return this.player
    }

    fun getPlayerType(): PlayerType {
        return this.playerType
    }
}