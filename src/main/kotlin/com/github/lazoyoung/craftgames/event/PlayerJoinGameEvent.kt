package com.github.lazoyoung.craftgames.event

import com.github.lazoyoung.craftgames.game.Game
import org.bukkit.entity.Player
import org.bukkit.event.Cancellable
import org.bukkit.event.HandlerList

class PlayerJoinGameEvent(
        game: Game,
        private val player: Player
) : GameEvent(game), Cancellable {

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

    fun getPlayer(): Player {
        return this.player
    }

    fun isGameStarted(): Boolean {
        return game.phase == Game.Phase.PLAYING
    }

    override fun setCancelled(cancel: Boolean) {
        this.cancel = cancel
    }

    override fun isCancelled(): Boolean {
        return this.cancel
    }
}