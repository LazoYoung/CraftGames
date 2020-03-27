package com.github.lazoyoung.craftgames.event

import com.github.lazoyoung.craftgames.game.Game
import org.bukkit.event.Cancellable
import org.bukkit.event.HandlerList

/**
 * This event is fired once all the modules and data is initialized.
 * However at this point, the map is not generated yet.
 */
class GameInitEvent(game: Game) : GameEvent(game), Cancellable {

    private var cancel = false

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

    override fun setCancelled(cancel: Boolean) {
        this.cancel = cancel
    }

    override fun isCancelled(): Boolean {
        return this.cancel
    }

}