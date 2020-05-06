package com.github.lazoyoung.craftgames.internal.listener

import com.github.lazoyoung.craftgames.event.GameLeaveEvent
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener

class GameListener : Listener {

    @EventHandler
    fun onGameLeave(event: GameLeaveEvent) {
        val game = event.getGame()

        if (game.players.isEmpty()) {
            game.close()
        }
    }

}