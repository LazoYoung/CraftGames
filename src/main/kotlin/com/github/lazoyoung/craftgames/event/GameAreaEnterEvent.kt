package com.github.lazoyoung.craftgames.event

import com.github.lazoyoung.craftgames.coordtag.tag.CoordTag
import com.github.lazoyoung.craftgames.game.Game
import org.bukkit.entity.Player
import org.bukkit.event.HandlerList

class GameAreaEnterEvent(
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
     * Get [CoordTag] name of this area.
     */
    fun getTagName(): String {
        return areaName
    }

    /**
     * Get [Player] who triggered this area.
     */
    fun getPlayer(): Player {
        return player
    }

}