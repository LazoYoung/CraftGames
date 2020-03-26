package com.github.lazoyoung.craftgames.event

import com.github.lazoyoung.craftgames.game.Game
import org.bukkit.event.HandlerList

class GameStartEvent(game: Game) : GameEvent(game) {

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

}