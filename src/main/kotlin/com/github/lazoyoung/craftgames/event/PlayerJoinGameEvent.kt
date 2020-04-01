package com.github.lazoyoung.craftgames.event

import com.github.lazoyoung.craftgames.game.Game
import org.bukkit.entity.Player
import org.bukkit.event.HandlerList

class PlayerJoinGameEvent(game: Game, private val player: Player) : GameEvent(game) {

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

    fun isGameStarted(): Boolean {
        return game.phase == Game.Phase.PLAYING
    }
}