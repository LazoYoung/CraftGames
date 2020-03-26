package com.github.lazoyoung.craftgames.event.listener

import com.github.lazoyoung.craftgames.event.GameInitEvent
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener

class GameListener : Listener {

    @EventHandler
    fun onGameInit(event: GameInitEvent) {
        val script = event.game.resource.script

        try {
            script.execute()
        } catch (e: Exception) {
            script.writeStackTrace(e)
            event.isCancelled = true
        }
    }

}