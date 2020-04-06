package com.github.lazoyoung.craftgames.event

import com.github.lazoyoung.craftgames.api.PlayerType
import com.github.lazoyoung.craftgames.game.Game
import org.bukkit.entity.Player
import org.bukkit.event.Cancellable
import org.bukkit.event.HandlerList

/**
 * This event notifies that [player] has joined the [game].
 */
class GameJoinEvent(
        game: Game,
        private val player: Player,
        private val playerType: PlayerType
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

    override fun setCancelled(cancel: Boolean) {
        this.cancel = cancel
    }

    override fun isCancelled(): Boolean {
        return cancel
    }

    fun getPlayer(): Player {
        return player
    }

    fun getPlayerType(): PlayerType {
        return playerType
    }
}