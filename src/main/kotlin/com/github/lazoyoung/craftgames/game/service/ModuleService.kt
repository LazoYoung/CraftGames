package com.github.lazoyoung.craftgames.game.service

import com.github.lazoyoung.craftgames.api.module.*
import com.github.lazoyoung.craftgames.coordtag.tag.CoordTag
import com.github.lazoyoung.craftgames.coordtag.tag.TagMode
import com.github.lazoyoung.craftgames.game.Game
import com.github.lazoyoung.craftgames.game.GamePhase
import com.github.lazoyoung.craftgames.game.GameTask
import com.github.lazoyoung.craftgames.game.player.PlayerData
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode

class ModuleService internal constructor(val game: Game) : Module, Service {

    private val script = game.resource.gameScript
    private val gameModule = GameModuleService(game)
    private val teamModule = TeamModuleService(game)
    private val lobbyModule = LobbyModuleService(game)
    private val playerModule = PlayerModuleService(game)
    private val mobModule = MobModuleService(game)
    private val scriptModule = ScriptModuleService(game)
    private val worldModule = WorldModuleService(game)
    private val itemModule = ItemModuleService(game)

    init {
        try {
            script.startLogging()
            script.parse()
            script.injectModules(this)
        } catch (e: Exception) {
            script.writeStackTrace(e)
            game.forceStop(error = true)
        }
    }

    companion object {
        internal fun getASTClassNode(arg: String): ClassNode? {
            return when (arg) {
                "Module" -> ClassHelper.make(Module::class.java)
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

        /**
         * Returns the relevant tag matching with [name] inside [game].
         *
         * If tag mode doesn't match with any of those [modes], it's considered __irrelevant__.
         *
         * @throws IllegalArgumentException is thrown if tag is irrelevant.
         */
        internal fun getRelevantTag(game: Game, name: String, vararg modes: TagMode): CoordTag {
            val tag = game.resource.tagRegistry.get(name)
                    ?: throw IllegalArgumentException("Unable to identify $name tag!")

            for (mode in modes) {
                if (mode == tag.mode)
                    return tag
            }

            val message = modes.joinToString(
                    prefix = "Tag $name is ${tag.mode.label} mode which is irrelevant! Acceptable modes: ",
                    transform = { it.label }
            )
            throw IllegalArgumentException(message)
        }
    }

    internal fun registerTasks() {
        GameTask(game, GamePhase.LOBBY).schedule {
            lobbyModule.start()
            mobModule.start()
        }

        GameTask(game, GamePhase.EDIT).schedule {
            mobModule.start()
        }

        GameTask(game, GamePhase.PLAYING).schedule {
            lobbyModule.terminate()
            gameModule.start()
            mobModule.start()
        }

        GameTask(game, GamePhase.TERMINATE).schedule {
            lobbyModule.terminate()
            playerModule.terminate()
            mobModule.terminate()
            teamModule.terminate()
            gameModule.terminate()
            scriptModule.terminate()
        }
    }

    internal fun ejectPlayer(playerData: PlayerData) {
        if (playerData.getGame() != this.game)
            return

        gameModule.bossBar.removePlayer(playerData.getPlayer())
    }

    override fun getGameModule(): GameModule {
        return gameModule
    }

    override fun getItemModule(): ItemModule {
        return itemModule
    }

    override fun getLobbyModule(): LobbyModule {
        return lobbyModule
    }

    override fun getMobModule(): MobModule {
        return mobModule
    }

    override fun getPlayerModule(): PlayerModule {
        return playerModule
    }

    override fun getScriptModule(): ScriptModule {
        return scriptModule
    }

    override fun getTeamModule(): TeamModule {
        return teamModule
    }

    override fun getWorldModule(): WorldModule {
        return worldModule
    }

    override fun start() {}

    override fun restart() {}

    override fun terminate() {}

}