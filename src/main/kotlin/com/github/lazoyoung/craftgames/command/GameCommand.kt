package com.github.lazoyoung.craftgames.command

import com.github.lazoyoung.craftgames.exception.FaultyConfiguration
import com.github.lazoyoung.craftgames.exception.GameNotFound
import com.github.lazoyoung.craftgames.exception.ScriptEngineNotFound
import com.github.lazoyoung.craftgames.game.Game
import com.github.lazoyoung.craftgames.game.GameFactory
import com.github.lazoyoung.craftgames.player.GameEditor
import com.github.lazoyoung.craftgames.player.GamePlayer
import com.github.lazoyoung.craftgames.player.PlayerData
import com.github.lazoyoung.craftgames.player.Spectator
import com.github.lazoyoung.craftgames.script.ScriptBase
import groovy.lang.GroovyRuntimeException
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.util.function.Consumer

class GameCommand : CommandBase {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (args.isEmpty()) {
            sender.sendMessage(arrayOf(
                    "/game start <title> <map>",
                    "/game stop <id>",
                    "/game edit <title> <map> : Enter the edit mode.",
                    "/game save : Save the changes and leave edit mode.",
                    "/game script <script> execute"
            ))
            return true
        }

        when (args[0].toLowerCase()) {
            "start" -> {
                if (args.size < 3)
                    return false

                if (sender !is Player) {
                    sender.sendMessage("This cannot be done from console.")
                    return true
                }

                if (PlayerData.get(sender) != null) {
                    sender.sendMessage("You are already in a game.")
                    return true
                }

                val game = openGame(args[1], sender)

                if (game == null) {
                    sender.sendMessage("Failed to open game! See console for details.")
                    return true
                }

                if (game.map.getMapList().none { it == args[2] }) {
                    sender.sendMessage("Map \'${args[2]}\' does not exist in this game!")
                    game.stop()
                    return true
                }

                game.join(sender)
                game.start(args[2], Consumer{
                    sender.sendMessage("Started ${game.name} with map: ${game.map.mapID}")
                })
            }
            "stop" -> {
                if (args.size < 2 || args[1].toIntOrNull() == null)
                    return false

                val id = args[1].toInt()

                try {
                    val game = GameFactory.findByID(id)

                    if (game != null) {
                        sender.sendMessage("Forcing to stop the game.")
                        game.stop()
                    } else {
                        sender.sendMessage("That game does not exist.")
                    }
                } catch (e: NullPointerException) {
                    sender.sendMessage("No game is running with id $id.")
                    return true
                }
            }
            "edit" -> {
                if (args.size < 3)
                    return false

                if (sender !is Player) {
                    sender.sendMessage("This cannot be done from console.")
                    return true
                }

                val present = GameFactory.find(args[1], true).firstOrNull()

                if (present != null && present.map.mapID == args[2]) {
                    sender.sendMessage("That map is being edited by someone else.")
                    return true
                }

                when (PlayerData.get(sender)) {
                    is Spectator, is GamePlayer -> {
                        sender.sendMessage("You have to leave the game you're at.")
                        return true
                    }
                    is GameEditor -> {
                        sender.sendMessage("You're already in editor mode.")
                        return true
                    }
                    null -> {
                        GameEditor.start(sender, args[1], args[2])
                    }
                }
            }
            "save" -> {
                if (sender !is Player) {
                    sender.sendMessage("This cannot be done from console.")
                    return true
                }

                val playerData = PlayerData.get(sender)

                if (playerData !is GameEditor) {
                    sender.sendMessage("You must be in editor mode.")
                } else {
                    playerData.saveAndLeave()
                }
            }
            "script" -> {
                if (args.size < 4)
                    return false

                if (sender !is Player) {
                    sender.sendMessage("This cannot be done from console.")
                    return true
                }

                val playerData = PlayerData.get(sender)
                val scriptID = args[2]
                val script: ScriptBase?

                if (playerData !is GameEditor) {
                    sender.sendMessage("You must be in editor mode.")
                    return true
                }

                script = playerData.game.scriptReg[scriptID]

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
            }
            else -> return false
        }
        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>)
            : MutableList<String>? {
        if (args.isEmpty())
            return command.aliases

        if (args.size == 1)
            return getCompletions(args[0], "start", "stop", "edit", "save", "script")

        when (args[0].toLowerCase()) {
            "start", "edit" -> {
                return when (args.size) {
                    2 -> getGameTitles(args[1])
                    3 -> {
                        try {
                            getCompletions(args[2], *GameFactory.getDummy(args[1]).map.getMapList())
                        } catch (e: Exception) { return mutableListOf() }
                    }
                    else -> mutableListOf()
                }
            }
            "stop" -> {
                return when (args.size) {
                    2 -> getCompletions(args[1], *GameFactory.find().map { it.id.toString() }.toTypedArray())
                    else -> mutableListOf()
                }
            }
            "script" -> {
                return when (args.size) {
                    2 -> getGameTitles(args[1])
                    3 -> {
                        try {
                            getCompletions(
                                    query = args[2],
                                    args = *GameFactory.getDummy(args[1]).scriptReg.keys.toList().toTypedArray()
                            )
                        } catch (e: Exception) { return mutableListOf() }
                    }
                    4 -> getCompletions(args[3], "execute")
                    else -> mutableListOf()
                }
            }
        }
        return mutableListOf()
    }

    private fun openGame(name: String, sender: CommandSender): Game? {
        val game: Game

        try {
            game = GameFactory.openNew(name, false)
        } catch (e: GameNotFound) {
            sender.sendMessage(e.localizedMessage)
            return null
        } catch (e: FaultyConfiguration) {
            e.printStackTrace()
            sender.sendMessage(e.localizedMessage)
            return null
        } catch (e: RuntimeException) {
            e.printStackTrace()
            sender.sendMessage(e.localizedMessage)
            return null
        } catch (e: ScriptEngineNotFound) {
            e.printStackTrace()
            sender.sendMessage(e.message)
            return null
        }
        return game
    }

}