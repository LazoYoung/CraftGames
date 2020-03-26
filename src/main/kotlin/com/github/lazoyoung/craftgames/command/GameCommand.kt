package com.github.lazoyoung.craftgames.command

import com.github.lazoyoung.craftgames.exception.GameNotFound
import com.github.lazoyoung.craftgames.exception.ScriptEngineNotFound
import com.github.lazoyoung.craftgames.game.Game
import com.github.lazoyoung.craftgames.player.GameEditor
import com.github.lazoyoung.craftgames.player.GamePlayer
import com.github.lazoyoung.craftgames.player.PlayerData
import com.github.lazoyoung.craftgames.player.Spectator
import net.md_5.bungee.api.ChatColor
import net.md_5.bungee.api.chat.ClickEvent
import net.md_5.bungee.api.chat.ComponentBuilder
import net.md_5.bungee.api.chat.HoverEvent
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.util.function.Consumer

class GameCommand : CommandBase {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (args.isEmpty() || args[0].equals("help", true)) {
            sender.sendMessage(
                    *ComponentBuilder(BORDER_STRING)
                    .append("\nGame Command Manual (Page 1/1)\n\n", RESET_FORMAT)
                    .append("◎ /game start [map]\n", RESET_FORMAT)
                        .event(HoverEvent(HOVER_TEXT, ComponentBuilder("Start the current game.\nYou may choose a map to play.").create()))
                        .event(ClickEvent(SUGGEST_CMD, "/game start"))
                    .append("◎ /game stop [id]\n", RESET_FORMAT)
                        .event(HoverEvent(HOVER_TEXT, ComponentBuilder("Stop the running game.").create()))
                        .event(ClickEvent(SUGGEST_CMD, "/game stop"))
                    .append("◎ /game edit <title> <map>\n", RESET_FORMAT)
                        .event(HoverEvent(HOVER_TEXT, ComponentBuilder("Start editor mode.").create()))
                        .event(ClickEvent(SUGGEST_CMD, "/game edit"))
                    .append("◎ /game save\n", RESET_FORMAT)
                        .event(HoverEvent(HOVER_TEXT, ComponentBuilder("Leave editor mode.\nChanges are saved to disk.").create()))
                        .event(ClickEvent(SUGGEST_CMD, "/game save"))
                    .append("◎ /game script execute\n", RESET_FORMAT)
                        .event(HoverEvent(HOVER_TEXT, ComponentBuilder("Execute script in the current game.").create()))
                        .event(ClickEvent(SUGGEST_CMD, "/game script execute"))
                    .append(PREV_NAV_END, RESET_FORMAT)
                    .append("- PAGE NAVIGATION -", RESET_FORMAT)
                    .append(NEXT_NAV_END)
                    .append(BORDER_STRING, RESET_FORMAT)
                    .create()
            )
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
                        val mapID = if (args.size > 1) {
                            args[1]
                        } else {
                            null
                        }

                        try {
                            player.game.start(mapID, result = Consumer {
                                sender.sendMessage("You have forced to start the game.")
                            })
                        } catch (e: Exception) {
                            e.printStackTrace()
                            sender.sendMessage(
                                    ComponentBuilder("Error occurred. See console for details.")
                                    .color(ChatColor.RED).create().first()
                            )
                        }
                    }
                }
            }
            "stop" -> {
                try {
                    val playerData = if (sender is Player) {
                        PlayerData.get(sender)
                    } else {
                        null
                    }
                    val game = when {
                        args.size > 1 -> {
                            Game.findByID(args[1].toInt())
                        }
                        playerData != null -> {
                            playerData.game
                        }
                        else -> return if (sender is Player) {
                            sender.sendMessage("You're not in a game.")
                            true
                        } else {
                            sender.sendMessage("This cannot be done from console.")
                            true
                        }
                    }

                    if (game != null) {
                        game.forceStop(error = false)
                        sender.sendMessage("Successfully terminated.")
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
                    null -> try {
                        GameEditor.start(sender, args[1], args[2])
                    } catch (e: GameNotFound) {
                        sender.sendMessage(*ComponentBuilder("Game ${args[1]} does not exist!").color(ChatColor.RED).create())
                    } catch (e: Exception) {
                        sender.sendMessage(*ComponentBuilder(e.localizedMessage).color(ChatColor.RED).create())
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
                if (args.size < 2)
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

                if (args[1] == "execute") {
                    val script = playerData.game.resource.script

                    try {
                        script.parse()
                        script.execute()
                        sender.sendMessage("Script has been executed.")
                    } catch (e: ScriptEngineNotFound) {
                        sender.sendMessage(e.message)
                    } catch (e: Exception) {
                        sender.sendMessage("Error: ${e.message}")
                        script.writeStackTrace(e)
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
            return getCompletions(args[0], "help", "start", "stop", "edit", "save", "script")

        when (args[0].toLowerCase()) {
            "start" -> {
                return if (args.size == 2) {
                    PlayerData.get(sender as Player)?.let {
                        getCompletions(args[1], *Game.getMapNames(it.game.name).toTypedArray())
                    } ?: mutableListOf()
                } else {
                    mutableListOf()
                }
            }
            "edit" -> {
                return when (args.size) {
                    2 -> getCompletions(args[1], *Game.getGameNames())
                    3 -> getCompletions(args[2], *Game.getMapNames(args[1]).toTypedArray())
                    else -> mutableListOf()
                }
            }
            "stop" -> {
                return when (args.size) {
                    2 -> getCompletions(args[1], *Game.find().map { it.id.toString() }.toTypedArray())
                    else -> mutableListOf()
                }
            }
            "script" -> {
                if (args.size == 2) {
                    return getCompletions(args[1], "execute")
                }
            }
        }
        return mutableListOf()
    }

}