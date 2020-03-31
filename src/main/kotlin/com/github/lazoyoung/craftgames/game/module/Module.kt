package com.github.lazoyoung.craftgames.game.module

import com.github.lazoyoung.craftgames.api.module.*
import com.github.lazoyoung.craftgames.coordtag.tag.CoordTag
import com.github.lazoyoung.craftgames.coordtag.tag.TagMode
import com.github.lazoyoung.craftgames.game.Game
import com.github.lazoyoung.craftgames.game.player.PlayerData
import javax.script.Bindings

class Module internal constructor(val game: Game) {

    private val script = game.resource.script
    private val gameModule = GameModuleService(game)
    private val teamModule = TeamModuleService(game)
    private val lobbyModule = LobbyModuleService(game)
    private val playerModule = PlayerModuleService(game)
    private val mobModule = MobModuleService(game)
    private val scriptModule = ScriptModuleService(game.resource)
    private val worldModule = WorldModuleService(game)
    private val itemModule = ItemModuleService(game)
    private val bind: Bindings
    private var terminateSignal = false

    init {
        bind = script.getBindings()
        bind["gameModule"] = gameModule as GameModule
        bind["teamModule"] = teamModule as TeamModule
        bind["lobbyModule"] = lobbyModule as LobbyModule
        bind["playerModule"] = playerModule as PlayerModule
        bind["mobModule"] = mobModule as MobModule
        bind["scriptModule"] = scriptModule as ScriptModule
        bind["worldModule"] = worldModule as WorldModule
        bind["itemModule"] = itemModule as ItemModule
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

        fun getItemModule(game: Game): ItemModuleService {
            return game.module.itemModule
        }

        /**
         * Returns the relevant tag matching with [name] inside [game].
         *
         * If tag mode doesn't match with any of those [modes], it's considered __irrelevant__.
         *
         * @throws IllegalArgumentException is thrown if tag is irrelevant.
         */
        internal fun getRelevantTag(game: Game, name: String, vararg modes: TagMode): CoordTag {
            val tag = CoordTag.get(game, name)
                    ?: throw IllegalArgumentException("Unable to identify $name tag!")

            for (mode in modes) {
                if (mode == tag.mode)
                    return tag
            }

            throw IllegalArgumentException("$name is not relevant! Acceptable tag modes: $modes")
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