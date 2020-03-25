package com.github.lazoyoung.craftgames.module

import com.github.lazoyoung.craftgames.Main
import com.github.lazoyoung.craftgames.coordtag.CoordTag
import com.github.lazoyoung.craftgames.coordtag.TagMode
import com.github.lazoyoung.craftgames.exception.FaultyConfiguration
import com.github.lazoyoung.craftgames.game.Game
import com.github.lazoyoung.craftgames.module.api.GameModule
import com.github.lazoyoung.craftgames.module.api.LobbyModule
import com.github.lazoyoung.craftgames.module.api.MobModule
import com.github.lazoyoung.craftgames.module.api.PlayerModule
import com.github.lazoyoung.craftgames.module.service.GameModuleImpl
import com.github.lazoyoung.craftgames.module.service.LobbyModuleImpl
import com.github.lazoyoung.craftgames.module.service.MobModuleImpl
import com.github.lazoyoung.craftgames.module.service.PlayerModuleImpl
import net.md_5.bungee.api.ChatColor
import net.md_5.bungee.api.chat.ComponentBuilder
import javax.script.Bindings
import javax.script.ScriptException

class Module(val game: Game) {

    internal val gameModule = GameModuleImpl(game)
    internal val lobbyModule = LobbyModuleImpl(game)
    internal val playerModule = PlayerModuleImpl(game)
    internal val mobModule = MobModuleImpl(game)
    private val script = game.resource.script
    private val bind: Bindings

    init {
        bind = script.getBindings()
        bind["GameModule"] = gameModule as GameModule
        bind["LobbyModule"] = lobbyModule as LobbyModule
        bind["PlayerModule"] = playerModule as PlayerModule
        bind["MobModule"] = mobModule as MobModule
        script.execute("import com.github.lazoyoung.craftgames.module.Module")
        script.execute("import com.github.lazoyoung.craftgames.module.Timer")
        script.parse()
    }

    companion object {
        fun getGameModule(game: Game): GameModule {
            return game.module.gameModule
        }

        fun getLobbyModule(game: Game): LobbyModule {
            return game.module.lobbyModule
        }

        fun getPlayerModule(game: Game): PlayerModule {
            return game.module.playerModule
        }

        internal fun getSpawnTag(game: Game, name: String): CoordTag {
            val tag = CoordTag.get(game, name) ?: throw IllegalArgumentException("Unable to identify $name tag.")

            if (tag.mode != TagMode.SPAWN)
                throw IllegalArgumentException("Parameter does not accept block tag.")

            if (tag.getLocalCaptures().isEmpty())
                throw FaultyConfiguration("Tag $name doesn't have any capture in ${game.map.mapID}")

            return tag
        }
    }

    internal fun update() {
        var func: String? = null

        try {
            when (game.phase) {
                Game.Phase.LOBBY -> {
                    func = "initLobby"
                    script.invokeFunction(func)
                    lobbyModule.startService()
                }
                Game.Phase.PLAYING -> {
                    func = "initGame"
                    script.invokeFunction(func)
                    lobbyModule.endService()
                    gameModule.startService()
                }
                Game.Phase.SUSPEND -> {
                    lobbyModule.endService()
                    gameModule.endService()
                    bind.clear()
                    script.closeIO()
                    // TODO Scheduler Module: Suspend schedulers
                }
            }
        } catch (e: Exception) {
            if (game.editMode && e is ScriptException) {
                game.getPlayers().first().sendMessage(
                        *ComponentBuilder("Script error occurred! Proceeding anyway...")
                                .color(ChatColor.RED).create()
                )
            } else {
                game.forceStop(async = true, error = true)
            }
            if (func != null) {
                val path = script.writeStackTrace(e)
                Main.logger.severe("Failed to invoke a function ($func) from script!")
                Main.logger.severe("Error log at: $path")
            } else {
                e.printStackTrace()
            }
        }
    }

}