package com.github.lazoyoung.craftgames.module

import com.github.lazoyoung.craftgames.Main
import com.github.lazoyoung.craftgames.coordtag.CoordTag
import com.github.lazoyoung.craftgames.coordtag.TagMode
import com.github.lazoyoung.craftgames.exception.FaultyConfiguration
import com.github.lazoyoung.craftgames.game.Game
import com.github.lazoyoung.craftgames.player.PlayerData
import net.md_5.bungee.api.ChatColor
import net.md_5.bungee.api.chat.ComponentBuilder
import org.bukkit.scheduler.BukkitRunnable
import javax.script.Bindings
import javax.script.ScriptException

class Module(val game: Game) {

    internal val spawnModule = SpawnModuleImpl(game)
    internal val lobbyModule = LobbyModuleImpl(game)
    internal val playerModule = PlayerModuleImpl(game)
    internal val tasks = HashMap<String, BukkitRunnable>()
    private val script = game.resource.script
    private val bind: Bindings

    init {
        bind = script.getBindings()
        bind["SpawnModule"] = spawnModule as SpawnModule
        bind["LobbyModule"] = lobbyModule as LobbyModule
        bind["PlayerModule"] = playerModule as PlayerModule
        script.execute("import com.github.lazoyoung.craftgames.module.Module")
        script.execute("import com.github.lazoyoung.craftgames.module.Timer")
        script.parse()
    }

    companion object {
        fun getSpawnModule(game: Game): SpawnModule {
            return game.module.spawnModule
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
                    lobbyModule.startTimer()
                }
                Game.Phase.PLAYING -> {
                    func = "initGame"
                    script.invokeFunction(func)
                    lobbyModule.reset()

                    // FIXME Populate to GameModule
                    game.players.mapNotNull { PlayerData.get(it) }.forEach { p ->
                        spawnModule.teleport(p)
                        p.player.gameMode = playerModule.gameMode
                    }
                }
                Game.Phase.FINISH -> { /* Reward logic */
                }
                Game.Phase.SUSPEND -> {
                    tasks.values.forEach(BukkitRunnable::cancel)
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
                game.stop(async = true, error = true)
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