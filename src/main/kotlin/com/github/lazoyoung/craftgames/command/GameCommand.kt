package com.github.lazoyoung.craftgames.command

import com.github.lazoyoung.craftgames.api.ActionbarTask
import com.github.lazoyoung.craftgames.game.Game
import com.github.lazoyoung.craftgames.game.player.GameEditor
import com.github.lazoyoung.craftgames.game.player.GamePlayer
import com.github.lazoyoung.craftgames.game.player.PlayerData
import com.github.lazoyoung.craftgames.game.player.Spectator
import com.github.lazoyoung.craftgames.game.script.ScriptFactory
import com.github.lazoyoung.craftgames.internal.exception.GameNotFound
import net.md_5.bungee.api.ChatColor
import net.md_5.bungee.api.chat.ClickEvent
import net.md_5.bungee.api.chat.ComponentBuilder
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.nio.file.Files
import java.util.function.Consumer
import java.util.regex.Pattern

class GameCommand : CommandBase {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (args.isEmpty() || args[0] == "help") {

            val page = try {
                args[1].toInt()
            } catch (e: Exception) {
                1
            }

            when (page) {
                1 -> sender.sendMessage(
                        *ComponentBuilder()
                                .append(BORDER_STRING, RESET_FORMAT)
                                .append("\nGame Command Manual (Page 1/2)\n\n", RESET_FORMAT)
                                .append(PageElement.getPageComponents(
                                        PageElement("◎ /game start [map]",
                                                "Start the current game.\n" +
                                                        "You may choose a map to play.",
                                                "/game start "),
                                        PageElement("◎ /game stop [id]",
                                                "Stop a running game.",
                                                "/game stop "),
                                        PageElement("◎ /game edit (title) (map)",
                                                "Start editor mode.\n" +
                                                        "Modify game elements or build map.",
                                                "/game edit "),
                                        PageElement("◎ /game save",
                                                "Leave editor mode.\n" +
                                                        "Changes are saved to disk.",
                                                "/game save")

                                ))
                                .append(PREV_NAV_END, RESET_FORMAT)
                                .append(PAGE_NAV, RESET_FORMAT)
                                .append(NEXT_NAV, RESET_FORMAT).event(ClickEvent(RUN_CMD, "/game help 2"))
                                .append(BORDER_STRING, RESET_FORMAT)
                                .create()
                )
                2 -> sender.sendMessage(
                        *ComponentBuilder()
                                .append(BORDER_STRING, RESET_FORMAT)
                                .append("\nGame Command Manual (Page 2/2)\n\n", RESET_FORMAT)
                                .append(PageElement.getPageComponents(
                                        PageElement("◎ /game kit <list/select/save/delete> (name)",
                                                "Save or delete a kit inventory.",
                                                "/game kit "),
                                        PageElement("◎ /game script (file) run [(property_name:value)...]",
                                                "Execute an entire script.\n" +
                                                        "You may supply optional properties.",
                                                "/game script test.groovy run test:true"),
                                        PageElement("◎ /game script (file) invoke (function) [argument...]",
                                                "Invoke a function defined in a script.\n" +
                                                        "&eSupplying appropriate argument(s) is mendatory!",
                                                "/game script test.groovy invoke test 123 \"Hello, world!\"")
                                ))
                                .append(PREV_NAV, RESET_FORMAT).event(ClickEvent(RUN_CMD, "/game help 1"))
                                .append(PAGE_NAV, RESET_FORMAT)
                                .append(NEXT_NAV_END, RESET_FORMAT)
                                .append(BORDER_STRING, RESET_FORMAT)
                                .create()
                )
            }

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

                val gameEditor = PlayerData.get(sender) as? GameEditor

                if (gameEditor?.isOnline() != true) {
                    sender.sendMessage("You must be in editor mode.")
                } else {
                    gameEditor.saveAndClose()
                }
            }
            "kit" -> {
                if (sender !is Player) {
                    sender.sendMessage("This cannot be done from console.")
                    return true
                }

                val game: Game
                val gameEditor = PlayerData.get(sender) as? GameEditor

                if (gameEditor?.isOnline() != true) {
                    sender.sendMessage("You must be in editor mode.")
                    return true
                }

                game = gameEditor.getGame()
                val itemService = game.getItemService()

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
                                itemService.selectKit(name, sender)
                                itemService.applyKit(sender)
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

                            itemService.saveKit(name, sender)
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

                                itemService.deleteKit(name)
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
            "script" -> {
                if (sender !is Player) {
                    sender.sendMessage("This cannot be done from console.")
                    return true
                }

                val gameEditor = PlayerData.get(sender) as? GameEditor

                if (gameEditor?.isOnline() != true) {
                    sender.sendMessage("You must be in editor mode.")
                    return true
                }

                if (args.size < 3) {
                    return false
                }

                val game = gameEditor.getGame()
                val script = try {
                    ScriptFactory.get(game.resource.scriptRoot.resolve(args[1]))
                } catch (e: Exception) {
                    sender.sendMessage("\u00A7c${e.localizedMessage}")
                    return true
                }

                try {
                    if (args[2] == "run") {
                        if (args.size > 3) {
                            val propArgs = try {
                                joinStringFromArguments(args.drop(3).toTypedArray())
                            } catch (e: IllegalArgumentException) {
                                sender.sendMessage("\u00A7cSyntax error. ${e.localizedMessage}")
                                return true
                            }

                            for (propArg in propArgs) {
                                val prop = propArg.split(Pattern.compile(":"), 2)

                                if (prop.size < 2) {
                                    sender.sendMessage("Wrong property syntax: $propArg")
                                    return false
                                } else {
                                    val name = prop[0]
                                    val value = translateStringToPrimitive(prop[1])
                                    sender.sendMessage("Binding property: $name - $value")
                                    script.bind(name, value)
                                }
                            }
                        }

                        script.startLogging()
                        script.parse()
                        script.injectModules(game.module)
                        script.run()
                        sender.sendMessage("Successfully executed ${args[1]}")
                    } else if (args[2] == "invoke") {
                        if (args.size < 4) {
                            return false
                        }

                        val func = args[3]

                        script.startLogging()
                        script.parse()
                        script.injectModules(game.module)

                        if (args.size > 4) {
                            val funcArgs = try {
                                joinStringFromArguments(args.drop(4).toTypedArray())
                            } catch (e: IllegalArgumentException) {
                                sender.sendMessage("\u00A7cSyntax error. ${e.localizedMessage}")
                                return true
                            }

                            script.invokeFunction(
                                    name = func,
                                    args = *funcArgs.map {
                                        val primitive = translateStringToPrimitive(it)
                                        sender.sendMessage("Passing argument: $primitive")
                                        primitive
                                    }.toTypedArray()
                            )
                        } else {
                            script.invokeFunction(func)
                        }

                        sender.sendMessage("Successfully invoked $func in ${args[1]}")
                    }
                } catch (e: Exception) {
                    script.writeStackTrace(e)
                    sender.sendMessage("\u00A7cError occurred. See console for details.")
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
            return getCompletions(args[0], "help", "start", "stop", "edit", "save", "kit", "script")

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
                    2 -> getCompletions(args[1], "list", "test", "save", "delete")
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
            "script" -> {
                val game = (PlayerData.get(sender as Player) as? GameEditor)?.getGame()
                        ?: return mutableListOf()

                when (args.size) {
                    2 -> {
                        val root = game.resource.scriptRoot
                        val supportedExt: Set<String> = ScriptFactory.Engine.values().flatMap {
                            it.extension.toList()
                        }.toSet()

                        Files.newDirectoryStream(root) {
                            supportedExt.contains(it.toFile().extension)
                        }.use {
                            return getCompletions(args[1], *it.map { path -> path.toFile().name }.toTypedArray())
                        }
                    }
                    3 -> return getCompletions(args[2], "run", "invoke")
                    else -> {
                        return if (args[2] == "invoke") {
                            if (args.size < 5) {
                                mutableListOf("(function)")
                            } else {
                                mutableListOf("[argument]")
                            }
                        } else if (args[2] == "run") {
                            mutableListOf("[property_name:value]")
                        } else {
                            mutableListOf()
                        }
                    }
                }
            }
        }
        return mutableListOf()
    }

}