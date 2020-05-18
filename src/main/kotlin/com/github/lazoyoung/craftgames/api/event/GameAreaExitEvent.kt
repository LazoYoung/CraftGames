package com.github.lazoyoung.craftgames.api.event

import com.github.lazoyoung.craftgames.impl.game.Game
import org.bukkit.entity.Player
import org.bukkit.event.HandlerList

class GameAreaExitEvent(
        game: Game,
        private val areaName: String,
        private val player: Player
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

    /**
     * @return Name of the coordinate tag which designates this area.
     */
    fun getTagName(): String {
        return areaName
    }

    /**
     * @return The [Player] who triggered this area.
     */
    fun getPlayer(): Player {
        return player
    }

}