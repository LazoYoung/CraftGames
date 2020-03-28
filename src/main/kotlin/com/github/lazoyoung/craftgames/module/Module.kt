package com.github.lazoyoung.craftgames.module

import com.github.lazoyoung.craftgames.coordtag.CoordTag
import com.github.lazoyoung.craftgames.coordtag.TagMode
import com.github.lazoyoung.craftgames.game.Game
import com.github.lazoyoung.craftgames.module.api.*
import com.github.lazoyoung.craftgames.module.service.*
import com.github.lazoyoung.craftgames.player.PlayerData
import javax.script.Bindings

class Module internal constructor(val game: Game) {

    private val script = game.resource.script
    private val gameModule = GameModuleService(game)
    private val teamModule = TeamModuleService(game)
    private val lobbyModule = LobbyModuleService(game)
    private val playerModule = PlayerModuleService(game)
    private val mobModule = MobModuleService(game)
    private val scriptModule = ScriptModuleService(script)
    private val worldModule = WorldModuleService(game)
    private var terminateSignal = false
    private val bind: Bindings

    init {
        bind = script.getBindings()
        bind["gameModule"] = gameModule as GameModule
        bind["teamModule"] = teamModule as TeamModule
        bind["lobbyModule"] = lobbyModule as LobbyModule
        bind["playerModule"] = playerModule as PlayerModule
        bind["mobModule"] = mobModule as MobModule
        bind["scriptModule"] = scriptModule as ScriptModule
        bind["worldModule"] = worldModule as WorldModule
        script.startLogging()
        script.parse()
        CoordTag.reload(game.resource)
    }

    companion object {
        fun getGameModule(game: Game): GameModuleService {
            return game.module.gameModule
        }

        fun getTeamModule(game: Game): TeamModuleService {
            return game.module.teamModule
        }

        fun getLobbyModule(game: Game): LobbyModuleService {
            return game.module.lobbyModule
        }

        fun getPlayerModule(game: Game): PlayerModuleService {
            return game.module.playerModule
        }

        fun getMobModule(game: Game): MobModuleService {
            return game.module.mobModule
        }

        fun getScriptModule(game: Game): ScriptModuleService {
            return game.module.scriptModule
        }

        fun getWorldModule(game: Game): WorldModuleService {
            return game.module.worldModule
        }

        internal fun getSpawnTag(game: Game, name: String): CoordTag {
            val tag = CoordTag.get(game, name)
                    ?: throw IllegalArgumentException("Unable to identify $name tag.")

            if (tag.mode != TagMode.SPAWN)
                throw IllegalArgumentException("You passed a BlockTag to parameter which is invalid.")

            return tag
        }
    }

    internal fun update() {
        if (terminateSignal)
            return

        try {
            when (game.phase) {
                Game.Phase.LOBBY -> {}
                Game.Phase.PLAYING -> {
                    lobbyModule.terminate()
                    gameModule.start()
                }
                Game.Phase.SUSPEND -> {
                    lobbyModule.terminate()
                    teamModule.terminate()
                    gameModule.terminate()
                    scriptModule.terminate()
                    game.resource.script.closeIO()
                    bind.clear()
                }
            }
        } catch (e: Exception) {
            terminateSignal = true
            e.printStackTrace()
            game.forceStop(async = true, error = true)
        }
    }

    internal fun ejectPlayer(playerData: PlayerData) {
        if (playerData.game != this.game)
            return

        playerModule.restore(playerData.player, leave = true)
        gameModule.bossBar.removePlayer(playerData.player)
    }

}