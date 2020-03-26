package com.github.lazoyoung.craftgames.event.listener

import com.github.lazoyoung.craftgames.event.GameInitEvent
import com.github.lazoyoung.craftgames.event.GameStartEvent
import com.github.lazoyoung.craftgames.module.api.ScriptModule.Companion.GAME_INIT_EVENT
import com.github.lazoyoung.craftgames.module.api.ScriptModule.Companion.GAME_START_EVENT
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener

class GameListener : Listener {

    @EventHandler
    fun onGameInit(event: GameInitEvent) {
        val script = event.game.resource.script

        try {
            script.execute()
            event.game.module.scriptModule.events[GAME_INIT_EVENT]?.accept(event)
        } catch (e: Exception) {
            script.writeStackTrace(e)
            event.isCancelled = true
        }
    }

    @EventHandler
    fun onGameStart(event: GameStartEvent) {
        val game = event.game

        try {
            game.module.scriptModule.events[GAME_START_EVENT]?.accept(event)
        } catch (e: Exception) {
            game.resource.script.writeStackTrace(e)
            game.forceStop(error = true)
        }
    }

}