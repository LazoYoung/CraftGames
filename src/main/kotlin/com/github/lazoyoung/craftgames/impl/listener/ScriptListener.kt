package com.github.lazoyoung.craftgames.impl.listener

import com.github.lazoyoung.craftgames.api.event.*
import org.bukkit.event.Cancellable
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener

class ScriptListener : Listener {

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onGameInit(event: GameInitEvent) {
        relayToScript(event)
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onGameStart(event: GameStartEvent) {
        relayToScript(event)
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onGameJoin(event: GameJoinEvent) {
        relayToScript(event)
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onGameJoinPost(event: GameJoinPostEvent) {
        relayToScript(event)
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onGameLeave(event: GameLeaveEvent) {
        relayToScript(event)
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onGameTimeout(event: GameTimeoutEvent) {
        relayToScript(event)
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onGameFinish(event: GameFinishEvent) {
        relayToScript(event)
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onAreaEnter(event: GameAreaEnterEvent) {
        relayToScript(event)
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onAreaExit(event: GameAreaExitEvent) {
        relayToScript(event)
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onPlayerKill(event: GamePlayerKillEvent) {
        relayToScript(event)
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onPlayerDeath(event: GamePlayerDeathEvent) {
        relayToScript(event)
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onPlayerInteract(event: GamePlayerInteractEvent) {
        relayToScript(event)
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onEntityDamage(event: GameEntityDamageEvent) {
        relayToScript(event)
    }

    private fun <T : GameEvent> relayToScript(event: T) {
        val game = event.getGame()

        try {
            game.getEventService().events[event.javaClass]?.accept(event)
        } catch (t: Throwable) {
            game.resource.mainScript.writeStackTrace(t)
            game.forceStop(error = true)

            if (event is Cancellable) {
                event.isCancelled = true
            }
        }
    }

}