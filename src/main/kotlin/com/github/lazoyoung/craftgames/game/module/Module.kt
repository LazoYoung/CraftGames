package com.github.lazoyoung.craftgames.game.module

import com.github.lazoyoung.craftgames.api.module.*
import com.github.lazoyoung.craftgames.coordtag.tag.CoordTag
import com.github.lazoyoung.craftgames.coordtag.tag.TagMode
import com.github.lazoyoung.craftgames.game.Game
import com.github.lazoyoung.craftgames.game.player.PlayerData
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode

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
    private var terminateSignal = false

    init {
        try {
            script.bind("GameModule", gameModule as GameModule)
            script.bind("TeamModule", teamModule as TeamModule)
            script.bind("LobbyModule", lobbyModule as LobbyModule)
            script.bind("PlayerModule", playerModule as PlayerModule)
            script.bind("MobModule", mobModule as MobModule)
            script.bind("ScriptModule", scriptModule as ScriptModule)
            script.bind("WorldModule", worldModule as WorldModule)
            script.bind("ItemModule", itemModule as ItemModule)
            script.startLogging()
            script.parse()
            CoordTag.reload(game.resource)
        } catch (e: Exception) {
            e.printStackTrace()
            game.forceStop(error = true)
        }
    }

    companion object {
        internal fun getASTClassNode(arg: String): ClassNode? {
            return when (arg) {
                "GameModule" -> ClassHelper.make(GameModule::class.java)
                "TeamModule" -> ClassHelper.make(TeamModule::class.java)
                "LobbyModule" -> ClassHelper.make(LobbyModule::class.java)
                "PlayerModule" -> ClassHelper.make(PlayerModule::class.java)
                "MobModule" -> ClassHelper.make(MobModule::class.java)
                "ScriptModule" -> ClassHelper.make(ScriptModule::class.java)
                "WorldModule" -> ClassHelper.make(WorldModule::class.java)
                "ItemModule" -> ClassHelper.make(ItemModule::class.java)
                else -> null
            }
        }

        internal fun getGameModule(game: Game): GameModuleService {
            return game.module.gameModule
        }

        internal fun getTeamModule(game: Game): TeamModuleService {
            return game.module.teamModule
        }

        internal fun getLobbyModule(game: Game): LobbyModuleService {
            return game.module.lobbyModule
        }

        internal fun getPlayerModule(game: Game): PlayerModuleService {
            return game.module.playerModule
        }

        internal fun getMobModule(game: Game): MobModuleService {
            return game.module.mobModule
        }

        internal fun getScriptModule(game: Game): ScriptModuleService {
            return game.module.scriptModule
        }

        internal fun getWorldModule(game: Game): WorldModuleService {
            return game.module.worldModule
        }

        internal fun getItemModule(game: Game): ItemModuleService {
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
                Game.Phase.GENERATING -> {}
                Game.Phase.LOBBY -> {
                    lobbyModule.start()
                }
                Game.Phase.PLAYING -> {
                    lobbyModule.terminate()
                    gameModule.start()
                }
                Game.Phase.SUSPEND -> {
                    lobbyModule.terminate()
                    teamModule.terminate()
                    gameModule.terminate()
                    scriptModule.terminate()
                }
            }
        } catch (e: Exception) {
            terminateSignal = true
            e.printStackTrace()
            game.forceStop(async = true, error = true)
        }
    }

    internal fun ejectPlayer(playerData: PlayerData) {
        if (playerData.getGame() != this.game)
            return

        gameModule.bossBar.removePlayer(playerData.getPlayer())
    }

}