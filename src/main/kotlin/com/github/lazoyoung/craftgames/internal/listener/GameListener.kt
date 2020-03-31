package com.github.lazoyoung.craftgames.internal.listener

import com.github.lazoyoung.craftgames.event.GameInitEvent
import com.github.lazoyoung.craftgames.event.GameStartEvent
import com.github.lazoyoung.craftgames.event.PlayerJoinGameEvent
import com.github.lazoyoung.craftgames.event.PlayerLeaveGameEvent
import com.github.lazoyoung.craftgames.game.module.Module
import com.github.lazoyoung.craftgames.api.EventType
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener

class GameListener : Listener {

    @EventHandler
    fun onGameInit(event: GameInitEvent) {
        val game = event.game
        val script = game.resource.script

        try {
            script.execute()
            Module.getScriptModule(game)
                    .events[EventType.GAME_INIT_EVENT]?.accept(event)
        } catch (e: Exception) {
            script.writeStackTrace(e)
            event.isCancelled = true
        }
    }

    @EventHandler
    fun onGameStart(event: GameStartEvent) {
        val game = event.game

        try {
            Module.getScriptModule(game)
                    .events[EventType.GAME_START_EVENT]?.accept(event)
        } catch (e: Exception) {
            game.resource.script.writeStackTrace(e)
            game.forceStop(error = true)
        }
    }

    @EventHandler
    fun onPlayerJoinGame(event: PlayerJoinGameEvent) {
        val game = event.game

        try {
            Module.getScriptModule(game)
                    .events[EventType.PLAYER_JOIN_GAME_EVENT]?.accept(event)
        } catch (e: Exception) {
            game.resource.script.writeStackTrace(e)
            game.forceStop(error = true)
        }
    }

    @EventHandler
    fun onPlayerJoinGame(event: PlayerLeaveGameEvent) {
        val game = event.game

        try {
            Module.getScriptModule(game)
                    .events[EventType.PLAYER_LEAVE_GAME_EVENT]?.accept(event)
        } catch (e: Exception) {
            game.resource.script.writeStackTrace(e)
            game.forceStop(error = true)
        }
    }

}