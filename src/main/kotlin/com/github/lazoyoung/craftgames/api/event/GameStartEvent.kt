package com.github.lazoyoung.craftgames.api.event

import com.github.lazoyoung.craftgames.impl.game.Game
import org.bukkit.event.HandlerList

/**
 * This event is triggered once every players have been teleported into field.
 */
class GameStartEvent(game: Game) : GameEvent(game) {

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

}