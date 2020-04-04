package com.github.lazoyoung.craftgames.internal.listener

import com.github.lazoyoung.craftgames.api.ActionbarTask
import com.github.lazoyoung.craftgames.api.EventType
import com.github.lazoyoung.craftgames.api.PlayerType
import com.github.lazoyoung.craftgames.event.*
import com.github.lazoyoung.craftgames.game.Game
import com.github.lazoyoung.craftgames.game.module.Module
import com.github.lazoyoung.craftgames.game.player.GameEditor
import com.github.lazoyoung.craftgames.game.player.PlayerData
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener

class GameListener : Listener {

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onGameInit(event: GameInitEvent) {
        val game = event.game
        val script = game.resource.script

        try {
            script.execute()
            Module.getScriptModule(game).events[EventType.GAME_INIT_EVENT]?.accept(event)
        } catch (e: Exception) {
            script.writeStackTrace(e)
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onGameStart(event: GameStartEvent) {
        val game = event.game

        try {
            Module.getScriptModule(game).events[EventType.GAME_START_EVENT]?.accept(event)
        } catch (e: Exception) {
            game.resource.script.writeStackTrace(e)
            game.forceStop(error = true)
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onGameFinish(event: GameFinishEvent) {
        val game = event.game

        try {
            Module.getScriptModule(game).events[EventType.GAME_FINISH_EVENT]?.accept(event)
        } catch (e: Exception) {
            game.resource.script.writeStackTrace(e)
            game.close()
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onPlayerJoinGame(event: PlayerJoinGameEvent) {
        val game = event.game

        try {
            Module.getScriptModule(game).events[EventType.PLAYER_JOIN_GAME_EVENT]?.accept(event)
        } catch (e: Exception) {
            game.resource.script.writeStackTrace(e)
            game.forceStop(error = true)
            event.isCancelled = true
        }
    }

    @EventHandler
    fun onPlayerJoinedGame(event: PlayerJoinGamePostEvent) {
        if (event.getPlayerType() == PlayerType.EDITOR) {
            updateEditors(event.game)
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onPlayerLeaveGame(event: PlayerLeaveGameEvent) {
        val game = event.game

        if (event.getPlayerType() == PlayerType.EDITOR) {
            updateEditors(game)
        }

        try {
            Module.getScriptModule(game).events[EventType.PLAYER_LEAVE_GAME_EVENT]?.accept(event)
        } catch (e: Exception) {
            game.resource.script.writeStackTrace(e)
            game.forceStop(error = true)
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