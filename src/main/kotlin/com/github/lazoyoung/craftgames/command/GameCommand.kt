package com.github.lazoyoung.craftgames.command

import com.github.lazoyoung.craftgames.Main
import com.github.lazoyoung.craftgames.exception.FaultyConfiguration
import com.github.lazoyoung.craftgames.exception.GameNotFound
import com.github.lazoyoung.craftgames.exception.MapNotFound
import com.github.lazoyoung.craftgames.exception.ScriptEngineNotFound
import com.github.lazoyoung.craftgames.game.Game
import com.github.lazoyoung.craftgames.game.GameFactory
import com.github.lazoyoung.craftgames.script.ScriptBase
import groovy.lang.GroovyRuntimeException
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.util.function.Consumer

class GameCommand : CommandBase {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        val game: Game?

        if (args.isEmpty()) {
            sender.sendMessage(arrayOf(
                    "/game start <title> [mapID]",
                    "/game stop <id>",
                    "/game script <title> <scriptID> execute"
            ))
            return true
        }

        if (args.size == 1)
            return false

        if (args[0].equals("start", true)) {
            if (args.size < 3)
                return false

            val name = args[1]
            game = getGame(name, sender)

            if (game == null)
                return true

            try {
                game.map.generate(args[2], Consumer{
                    if (it == null) {
                        sender.sendMessage("Started $name.")
                        return@Consumer
                    }

                    if (sender is Player) {
                        // TODO This function will be replaced by Game#start()
                        sender.teleport(it.spawnLocation)
                    }
                    sender.sendMessage("Started $name with map: ${game.map.mapID}")
                })
            } catch (e: RuntimeException) {
                e.printStackTrace()
                sender.sendMessage("Unable to load map.")
            } catch (e: FaultyConfiguration) {
                e.printStackTrace()
                sender.sendMessage("Unable to load map.")
            } catch (e: MapNotFound) {
                sender.sendMessage(e.message!!)
            }
        }
        else if (args[0].equals("stop", true)) {
            val id = args[1].toIntOrNull()

            if (args.size < 2 || id == null)
                return false

            try {
                if (GameFactory.findByID(id)?.stop() == true) {
                    sender.sendMessage("The game has been stopped.")
                } else {
                    sender.sendMessage("That game does not exist.")
                }
            } catch (e: NullPointerException) {
                sender.sendMessage("No game is running with id $id.")
                return true
            }
        }
        else if (args[0].equals("script", true)) {
            if (args.size < 4)
                return false

            val name = args[1]
            game = getGame(name, sender)

            if (game == null)
                return true

            val scriptID: String = args[2]
            val script: ScriptBase? = game.scriptReg[scriptID]

            if (script == null) {
                sender.sendMessage("That script ($scriptID) does not exist.")
                return true
            }

            if (args[3] == "execute") {
                try {
                    script.parse()
                    script.execute()
                    sender.sendMessage("Script $scriptID has been executed.")
                } catch (e: GroovyRuntimeException) {
                    sender.sendMessage("Compilation error: ${e.message}")
                    e.printStackTrace()
                } catch (e: ScriptEngineNotFound) {
                    sender.sendMessage(e.message)
                }
            }
        } else return false

        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>)
            : MutableList<String>? {
        if (args.isEmpty())
            return command.aliases

        if (args.size == 1)
            return getCompletions(args[0], "start", "stop", "script")

        fun getGameNames() : MutableList<String> {
            return getCompletions(
                    query = args[1],
                    args = Main.config.getConfigurationSection("games")
                            ?.getKeys(false)
                            ?.toList()
                            ?: emptyList()
            )
        }

        when {
            args[0].equals("start", true) -> {
                return when (args.size) {
                    2 -> getGameNames()
                    3 -> {
                        val reg = GameFactory.getDummy(args[1]).map.mapRegistry
                        getCompletions(args[2], reg.mapNotNull { it["id"] as String? })
                    }
                    else -> mutableListOf()
                }
            }
            args[0].equals("stop", true) -> {
                return when (args.size) {
                    2 -> getCompletions(args[1], GameFactory.find().map { it.id.toString() })
                    else -> mutableListOf()
                }
            }
            args[0].equals("script", true) -> {
                return when (args.size) {
                    2 -> getGameNames()
                    3 -> {
                        var compl: MutableList<String> = mutableListOf()
                        try {
                            compl = getCompletions(args[2], GameFactory.getDummy(args[1]).scriptReg.keys.toList())
                        } catch (e: Exception) { /* Neglect any exception */ }
                        compl
                    }
                    4 -> getCompletions(args[3], "execute")
                    else -> mutableListOf()
                }
            }
        }
        return mutableListOf()
    }

    private fun getGame(name: String, sender: CommandSender): Game? {
        var game: Game? = null
        try {
            game = GameFactory.openNew(name)
        } catch (e: GameNotFound) {
            sender.sendMessage(e.message!!)
        } catch (e: FaultyConfiguration) {
            e.printStackTrace()
            sender.sendMessage(e.message!!)
        } catch (e: RuntimeException) {
            e.printStackTrace()
            sender.sendMessage(e.message!!)
        } catch (e: ScriptEngineNotFound) {
            e.printStackTrace()
            sender.sendMessage(e.message)
        }
        return game
    }

}