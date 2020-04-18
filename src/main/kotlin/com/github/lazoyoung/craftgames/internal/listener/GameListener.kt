package com.github.lazoyoung.craftgames.internal.listener

import com.github.lazoyoung.craftgames.api.EventType
import com.github.lazoyoung.craftgames.event.*
import com.github.lazoyoung.craftgames.game.module.Module
import org.bukkit.event.Cancellable
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener

class GameListener : Listener {

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onGameInit(event: GameInitEvent) {
        relayToScript(event, EventType.GAME_INIT_EVENT)
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onGameStart(event: GameStartEvent) {
        relayToScript(event, EventType.GAME_START_EVENT)
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onGameJoin(event: GameJoinEvent) {
        relayToScript(event, EventType.GAME_JOIN_EVENT)
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onGameJoinPost(event: GameJoinPostEvent) {
        relayToScript(event, EventType.GAME_JOIN_POST_EVENT)
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onGameLeave(event: GameLeaveEvent) {
        relayToScript(event, EventType.GAME_LEAVE_EVENT)
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onGameTimeout(event: GameTimeoutEvent) {
        relayToScript(event, EventType.GAME_TIMEOUT_EVENT)
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onGameFinish(event: GameFinishEvent) {
        relayToScript(event, EventType.GAME_FINISH_EVENT)
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onAreaEnter(event: GameAreaEnterEvent) {
        relayToScript(event, EventType.AREA_ENTER_EVENT)
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onAreaExit(event: GameAreaExitEvent) {
        relayToScript(event, EventType.AREA_EXIT_EVENT)
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onPlayerKill(event: GamePlayerKillEvent) {
        relayToScript(event, EventType.PLAYER_KILL_EVENT)
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onPlayerDeath(event: GamePlayerDeathEvent) {
        relayToScript(event, EventType.PLAYER_DEATH_EVENT)
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onPlayerInteract(event: GamePlayerInteractEvent) {
        relayToScript(event, EventType.PLAYER_INTERACT_EVENT)
    }

    private fun <T : GameEvent> relayToScript(event: T, type: EventType) {
        val game = event.getGame()

        try {
            Module.getScriptModule(game).events[type]?.accept(event)
        } catch (e: Exception) {
            game.resource.script.writeStackTrace(e)
            game.close()

            if (event is Cancellable) {
                event.isCancelled = true
            }
        }
    }

}