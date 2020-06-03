package com.github.lazoyoung.craftgames.api.event

import com.github.lazoyoung.craftgames.impl.game.Game
import com.github.lazoyoung.craftgames.impl.game.player.GameEditor
import org.bukkit.event.HandlerList

/**
 * Called just before saving the game data.
 */
class GameEditorSaveEvent(
        game: Game, editor: GameEditor
): GameEditorEvent(game, editor) {

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