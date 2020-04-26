package com.github.lazoyoung.craftgames.command

import com.github.lazoyoung.craftgames.api.ActionbarTask
import com.github.lazoyoung.craftgames.game.Game
import com.github.lazoyoung.craftgames.game.module.Module
import com.github.lazoyoung.craftgames.game.player.GameEditor
import com.github.lazoyoung.craftgames.game.player.GamePlayer
import com.github.lazoyoung.craftgames.game.player.PlayerData
import com.github.lazoyoung.craftgames.game.player.Spectator
import com.github.lazoyoung.craftgames.internal.exception.GameNotFound
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
                    *ComponentBuilder()
                            .append(BORDER_STRING, RESET_FORMAT)
                            .append("\nGame Command Manual (Page 1/1)\n\n", RESET_FORMAT)
                            .append("◎ /game start [map]\n", RESET_FORMAT)
                            .event(HoverEvent(HOVER_TEXT, ComponentBuilder("Start the current game.\nYou may choose a map to play.").create()))
                            .event(ClickEvent(SUGGEST_CMD, "/game start"))
                            .append("◎ /game stop [id]\n", RESET_FORMAT)
                            .event(HoverEvent(HOVER_TEXT, ComponentBuilder("Stop the running game.").create()))
                            .event(ClickEvent(SUGGEST_CMD, "/game stop"))
                            .append("◎ /game edit (title) (map)\n", RESET_FORMAT)
                            .event(HoverEvent(HOVER_TEXT, ComponentBuilder("Start editor mode.").create()))
                            .event(ClickEvent(SUGGEST_CMD, "/game edit"))
                            .append("◎ /game save\n", RESET_FORMAT)
                            .event(HoverEvent(HOVER_TEXT, ComponentBuilder("Leave editor mode.\nChanges are saved to disk.").create()))
                            .event(ClickEvent(SUGGEST_CMD, "/game save"))
                            .append("◎ /game kit <list/select/save/delete> (name)\n", RESET_FORMAT)
                            .event(HoverEvent(HOVER_TEXT, ComponentBuilder("Save or delete a kit inventory.").create()))
                            .event(ClickEvent(SUGGEST_CMD, "/game kit "))
                            .append(PREV_NAV_END, RESET_FORMAT)
                            .append(PAGE_NAV, RESET_FORMAT)
                            .append(NEXT_NAV_END, RESET_FORMAT)
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

                val player = PlayerData.get(sender)

                when {
                    player?.isOnline() != true -> {
                        sender.sendMessage("You're not in game.")
                    }
                    player is GameEditor -> {
                        sender.sendMessage("You must leave editor mode.")
                    }
                    player is GamePlayer || player is Spectator -> {
                        val mapID = if (args.size > 1) {
                            args[1]
                        } else {
                            null
                        }

                        try {
                            player.getGame().start(mapID, result = Consumer {
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
                            Game.getByID(args[1].toInt())
                        }
                        playerData?.isOnline() == true -> {
                            playerData.getGame()
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
                        sender.sendMessage(*ComponentBuilder("Error occurred! See console for details.").color(ChatColor.RED).create())
                        e.printStackTrace()
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
                    playerData.saveAndClose()
                }
            }
            "kit" -> {
                if (sender !is Player) {
                    sender.sendMessage("This cannot be done from console.")
                    return true
                }

                val game: Game
                val playerData = PlayerData.get(sender)

                if (playerData !is GameEditor) {
                    sender.sendMessage("You must be in editor mode.")
                    return true
                }

                game = playerData.getGame()

                if (args.size < 2)
                    return false

                when (args[1].toLowerCase()) {
                    "list" -> {
                        val list = game.resource.kitData.keys.joinToString(limit = 20)
                        val text = ComponentBuilder("Available kits: ")
                                .append(list).color(ChatColor.GREEN).create()
                        sender.sendMessage(*text)
                    }
                    "select", "test" -> {
                        if (args.size > 2) {
                            try {
                                val name = args[2]
                                Module.getItemModule(game).selectKit(name, sender)
                                Module.getItemModule(game).applyKit(sender)
                                ActionbarTask(sender, "&aKit \'$name\' has been applied.").start()
                            } catch (e: IllegalArgumentException) {
                                sender.sendMessage("That kit does not exist!")
                            }
                        } else {
                            sender.sendMessage("Provide the name of kit!")
                            return false
                        }
                    }
                    "create", "save" -> {
                        if (args.size > 2) {
                            val name = args[2]

                            Module.getItemModule(game).saveKit(name, sender)
                            ActionbarTask(sender, "&aKit \'$name\' has been saved.").start()
                        } else {
                            sender.sendMessage("Provide the name of kit!")
                            return false
                        }
                    }
                    "remove", "delete" -> {
                        if (args.size > 2) {
                            try {
                                val name = args[2]

                                Module.getItemModule(game).deleteKit(name)
                                ActionbarTask(sender, "&aKit \'$name\' has been removed.").start()
                            } catch (e: IllegalArgumentException) {
                                sender.sendMessage("\u00A7eThat kit does not exist!")
                            } catch (e: RuntimeException) {
                                sender.sendMessage("\u00A7cFailed to delete kit.")
                            }
                        } else {
                            sender.sendMessage("\u00A7eProvide the name of kit!")
                            return false
                        }
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
            return getCompletions(args[0], "help", "start", "stop", "edit", "save", "kit")

        when (args[0].toLowerCase()) {
            "start" -> {
                return if (args.size == 2) {
                    val playerData = PlayerData.get(sender as Player)
                    if (playerData?.isOnline() == true) {
                        getCompletions(
                                query = args[1],
                                options = *Game.getMapNames(playerData.getGame().name).toTypedArray()
                        )
                    } else {
                        mutableListOf()
                    }
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
            "kit" -> {
                return when (args.size) {
                    2 -> {
                        getCompletions(args[1], "list", "test", "save", "delete")
                    }
                    3 -> {
                        if (args[2].equals("list", true)) {
                            mutableListOf()
                        } else {
                            val playerData = PlayerData.get(sender as Player)

                            if (playerData?.isOnline() == true) {
                                getCompletions(
                                        query = args[2],
                                        options = *playerData.getGame().resource.kitData.keys.toTypedArray()
                                )
                            } else {
                                mutableListOf()
                            }
                        }
                    }
                    else -> mutableListOf()
                }
            }
        }
        return mutableListOf()
    }

}