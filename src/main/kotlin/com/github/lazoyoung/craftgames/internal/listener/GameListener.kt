package com.github.lazoyoung.craftgames.internal.listener

import com.github.lazoyoung.craftgames.api.*
import com.github.lazoyoung.craftgames.event.*
import com.github.lazoyoung.craftgames.game.Game
import com.github.lazoyoung.craftgames.game.module.Module
import com.github.lazoyoung.craftgames.game.player.GameEditor
import com.github.lazoyoung.craftgames.game.player.PlayerData
import org.bukkit.event.Cancellable
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener

class GameListener : Listener {

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onGameInit(event: GameInitEvent) {
        val game = event.getGame()
        val script = game.resource.script

        try {
            script.execute()
            Module.getScriptModule(game).events[EventType.GAME_INIT_EVENT]?.accept(event)
        } catch (e: Exception) {
            script.writeStackTrace(e)
            game.close()
            event.isCancelled = true
        }
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

        if (event.getPlayerType() == PlayerType.EDITOR) {
            updateEditors(event.getGame())
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onGameLeave(event: GameLeaveEvent) {
        relayToScript(event, EventType.GAME_LEAVE_EVENT)
    }

    @Suppress("DEPRECATION")
    @EventHandler(priority = EventPriority.HIGHEST)
    fun onGameTimeout(event: GameTimeoutEvent) {
        relayToScript(event, EventType.GAME_TIMEOUT_EVENT)

        val game = event.getGame()

        if (game.phase != Game.Phase.FINISH) {
            Module.getGameModule(game).drawGame(Timer(TimeUnit.SECOND, 5))
        }
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

    private fun updateEditors(game: Game) {
        val coopList = game.getPlayers().map { PlayerData.get(it) }
                .filterIsInstance(GameEditor::class.java).shuffled()

        coopList.forEach { editor ->
            val memberList = coopList.filterNot { it.getPlayer() == editor.getPlayer() }

            var text = arrayOf(
                    "&b&lEDIT MODE &r&b(&e${editor.mapID} &bin &e${game.name}&b)",
                    "&aType &b/game save &r&ato save changes and exit."
            )

            if (memberList.isNotEmpty()) {
                text = text.plus(memberList.joinToString(
                        prefix = "&aCooperator: &r",
                        limit = 3,
                        transform = { it.getPlayer().displayName }
                ))
            }

            editor.mainActionbar?.clear()
            editor.mainActionbar = ActionbarTask(
                    player = editor.getPlayer(),
                    repeat = true,
                    text = *text
            ).start()
        }
    }

}