package com.github.lazoyoung.craftgames.command

import com.github.lazoyoung.craftgames.exception.ScriptEngineNotFound
import com.github.lazoyoung.craftgames.game.Game
import com.github.lazoyoung.craftgames.player.GameEditor
import com.github.lazoyoung.craftgames.player.GamePlayer
import com.github.lazoyoung.craftgames.player.PlayerData
import com.github.lazoyoung.craftgames.player.Spectator
import groovy.lang.GroovyRuntimeException
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.util.function.Consumer

class GameCommand : CommandBase {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (args.isEmpty()) {
            sender.sendMessage(arrayOf(
                    "/game start [map]",
                    "/game stop [id]",
                    "/game edit <title> <map> : Enter the edit mode.",
                    "/game save : Save the changes and leave edit mode.",
                    "/game script execute"
            ))
            return true
        }

        when (args[0].toLowerCase()) {
            "start" -> {
                if (sender !is Player) {
                    sender.sendMessage("This cannot be done from console.")
                    return true
                }

                when (val player = PlayerData.get(sender)) {
                    null -> {
                        sender.sendMessage("You're not in game.")
                    }
                    is GameEditor -> {
                        sender.sendMessage("You must leave editor mode.")
                    }
                    else -> {
                        player.game.start(null, Consumer {
                            sender.sendMessage("You have forced to start the game.")
                        })
                    }
                }
            }
            "stop" -> {
                try {
                    val playerData = PlayerData.get(sender as Player)
                    val game = when {
                        args.size > 1 -> {
                            Game.findByID(args[1].toInt())
                        }
                        playerData != null -> {
                            playerData.game
                        }
                        else -> {
                            sender.sendMessage("You're not in a game.")
                            return false
                        }
                    }

                    if (game != null) {
                        game.stop()
                        sender.sendMessage("Game \'${game.name}\' has been stopped.")
                    } else {
                        sender.sendMessage("That game does not exist.")
                    }
                } catch (e: NumberFormatException) {
                    return false
                }
            }
            "edit" -> {
                if (args.size < 3)
                    return false

                if (sender !is Player) {
                    sender.sendMessage("This cannot be done from console.")
                    return true
                }

                val present = Game.find(args[1], true).firstOrNull()

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
                if (args.size < 3)
                    return false

                if (sender !is Player) {
                    sender.sendMessage("This cannot be done from console.")
                    return true
                }

                val playerData = PlayerData.get(sender)

                if (playerData !is GameEditor) {
                    sender.sendMessage("You must be in editor mode.")
                    return true
                }

                if (args[2] == "execute") {
                    try {
                        val script = playerData.game.resource.script

                        script.parse()
                        script.execute()
                        sender.sendMessage("Script has been executed.")
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
                return if (args.size == 2) {
                    try {
                        PlayerData.get(sender as Player)?.let {
                            getCompletions(args[2], *Game.getMapList(it.game.name).toTypedArray())
                        } ?: mutableListOf()
                    } catch (e: Exception) { return mutableListOf() }
                } else {
                    mutableListOf()
                }
            }
            "stop" -> {
                return when (args.size) {
                    2 -> getCompletions(args[1], *Game.find().map { it.id.toString() }.toTypedArray())
                    else -> mutableListOf()
                }
            }
            "script" -> {
                return when (args.size) {
                    2 -> getGameTitles(args[1])
                    3 -> getCompletions(args[2], "execute")
                    else -> mutableListOf()
                }
            }
        }
        return mutableListOf()
    }

}