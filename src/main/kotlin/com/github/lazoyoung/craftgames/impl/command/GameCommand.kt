package com.github.lazoyoung.craftgames.impl.command

import com.github.lazoyoung.craftgames.api.ActionbarTask
import com.github.lazoyoung.craftgames.api.script.ScriptCompiler
import com.github.lazoyoung.craftgames.api.script.ScriptFactory
import com.github.lazoyoung.craftgames.impl.command.base.CommandBase
import com.github.lazoyoung.craftgames.impl.command.base.CommandHelp
import com.github.lazoyoung.craftgames.impl.command.base.PageBody
import com.github.lazoyoung.craftgames.impl.exception.GameNotFound
import com.github.lazoyoung.craftgames.impl.game.Game
import com.github.lazoyoung.craftgames.impl.game.GameMap
import com.github.lazoyoung.craftgames.impl.game.player.GameEditor
import com.github.lazoyoung.craftgames.impl.game.player.GamePlayer
import com.github.lazoyoung.craftgames.impl.game.player.PlayerData
import com.github.lazoyoung.craftgames.impl.game.player.Spectator
import net.md_5.bungee.api.ChatColor
import net.md_5.bungee.api.chat.ComponentBuilder
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.nio.file.Files
import java.util.*
import java.util.function.Consumer
import java.util.regex.Pattern

class GameCommand : CommandBase("CraftGames") {

    private val help = CommandHelp(
            "Game", "/game",
            PageBody(
                    PageBody.Element(
                            "◎ /game start [map]",
                            "Start current game.\n" +
                                    "You may choose which map to play in.",
                            "/game start "
                    ),
                    PageBody.Element(
                            "◎ /game stop [id]",
                            "Terminate current game.",
                            "/game stop "
                    ),
                    PageBody.Element(
                            "◎ /game edit (title) (map)",
                            "Start editor mode.\n" +
                                    "There you can modify game elements and blocks.",
                            "/game edit "
                    )
            ),
            PageBody {
                val pdata = PlayerData.get(it as Player)
                val list = LinkedList(listOf(
                        PageBody.Element(
                                "◎ /game save",
                                "Leave editor mode.\n" +
                                        "Changes will be saved into disk.",
                                "/game save"
                        ),
                        PageBody.Element(
                                "◎ /game kit <list/select/save/delete> (name)",
                                "Save or delete a kit inventory.",
                                "/game kit "
                        ),
                        PageBody.Element(
                                "◎ /game script (file) run [(property_name:value)...]",
                                "Execute an entire script.\n" +
                                        "You may supply optional properties.",
                                "/game script test.groovy run test:true"
                        ),
                        PageBody.Element(
                                "◎ /game script (file) invoke (function) [argument...]",
                                "Invoke a function defined in a script.\n" +
                                        "&eSupplying appropriate argument(s) is mendatory!",
                                "/game script test.groovy invoke test 123 \"Hello, world!\""
                        )
                ))

                if (pdata !is GameEditor) {
                    list.addFirst(PageBody.Element(
                            "&eNote: You must be in editor mode.",
                            "&6Click here to start editing.",
                            "/game edit "
                    ))
                }

                list
            }
    )

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        if (CommandHelp.isPrompted(args)) {
            return help.display(sender, args)
        }

        when (args[0].toLowerCase()) {
            "start" -> {
                if (sender !is Player) {
                    sender.sendMessage("$error This cannot be done from console.")
                    return true
                }

                val player = PlayerData.get(sender)

                when {
                    player?.isOnline() != true -> {
                        val text = PageBody(
                                PageBody.Element(
                                        "$error You must be in a game.",
                                        "&6Click here to play game.",
                                        "/join "
                                )
                        ).getBodyText(sender)
                        sender.sendMessage(*text)
                    }
                    player is GameEditor -> {
                        val text = PageBody(
                                PageBody.Element(
                                        "$error You must leave editor mode.",
                                        "&6Click here to save and exit.",
                                        "/game save"
                                )
                        ).getBodyText(sender)
                        sender.sendMessage(*text)
                    }
                    player is GamePlayer || player is Spectator -> {
                        val mapID = if (args.size > 1) {
                            args[1]
                        } else {
                            null
                        }

                        try {
                            player.getGame().start(mapID, result = Consumer {
                                sender.sendMessage("$info You have forced to start the game.")
                            })
                        } catch (e: Exception) {
                            e.printStackTrace()
                            sender.sendMessage(
                                    ComponentBuilder("$error Error occurred. See console for details.")
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
                            val text = PageBody(
                                    PageBody.Element(
                                            "$error You must be in a game.",
                                            "&6Click here to play game.",
                                            "/join "
                                    )
                            ).getBodyText(sender)
                            sender.sendMessage(*text)
                            true
                        } else {
                            sender.sendMessage("$error This cannot be done from console.")
                            true
                        }
                    }

                    if (game != null) {
                        game.forceStop(error = false)
                        sender.sendMessage("$info Successfully terminated.")
                    } else {
                        sender.sendMessage("$error That game does not exist.")
                    }
                } catch (e: NumberFormatException) {
                    return false
                }
            }
            "edit" -> {
                if (args.size < 3)
                    return false

                if (sender !is Player) {
                    sender.sendMessage("$error This cannot be done from console.")
                    return true
                }

                when (PlayerData.get(sender)) {
                    is Spectator, is GamePlayer -> {
                        val text = PageBody(
                                PageBody.Element(
                                        "$error You have to leave the game you're at.",
                                        "&6Click here to exit.",
                                        "/leave"
                                )
                        ).getBodyText(sender)
                        sender.sendMessage(*text)
                        return true
                    }
                    is GameEditor -> {
                        sender.sendMessage("$error You're already in editor mode.")
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
                    val text = PageBody(
                            PageBody.Element(
                                    "$error You have to be in editor mode.",
                                    "&6Click here to start editing.",
                                    "/game edit "
                            )
                    ).getBodyText(sender)
                    sender.sendMessage(*text)
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
                    // TODO Let CommandSender select the compiler mode
                    ScriptFactory.get(game.resource.layout.scriptDir.resolve(args[1]), ScriptCompiler.STATIC)
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
                        game.module.injectModules(script)
                        script.run()
                        sender.sendMessage("Successfully executed ${args[1]}")
                    } else if (args[2] == "invoke") {
                        if (args.size < 4) {
                            return false
                        }

                        val func = args[3]
                        script.startLogging()
                        script.parse()
                        game.module.injectModules(script)

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

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String>? {
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
                                options = playerData.getGame().resource.mapRegistry.getMapNames(true)
                        )
                    } else {
                        listOf()
                    }
                } else {
                    listOf()
                }
            }
            "edit" -> {
                return when (args.size) {
                    2 -> getCompletions(args[1], *Game.getGameNames())
                    3 -> {
                        try {
                            return getCompletions(args[2], GameMap.Registry(args[1]).getMapNames())
                        } catch (e: Exception) {}

                        listOf()
                    }
                    else -> listOf()
                }
            }
            "stop" -> {
                return when (args.size) {
                    2 -> getCompletions(args[1], Game.find().map { it.id.toString() })
                    else -> listOf()
                }
            }
            "kit" -> {
                return when (args.size) {
                    2 -> getCompletions(args[1], "list", "test", "save", "delete")
                    3 -> {
                        if (args[2].equals("list", true)) {
                            listOf()
                        } else {
                            val playerData = PlayerData.get(sender as Player)

                            if (playerData?.isOnline() == true) {
                                getCompletions(
                                        query = args[2],
                                        options = playerData.getGame().resource.kitData.keys.toList()
                                )
                            } else {
                                listOf()
                            }
                        }
                    }
                    else -> listOf()
                }
            }
            "script" -> {
                val game = (PlayerData.get(sender as Player) as? GameEditor)?.getGame()
                        ?: return listOf()

                when (args.size) {
                    2 -> {
                        val root = game.resource.layout.scriptDir
                        val supportedExt: Set<String> = ScriptFactory.Engine.values().flatMap {
                            it.extension.toList()
                        }.toSet()

                        Files.newDirectoryStream(root) {
                            supportedExt.contains(it.toFile().extension)
                        }.use {
                            return getCompletions(args[1], it.map { path -> path.toFile().name })
                        }
                    }
                    3 -> return getCompletions(args[2], "run", "invoke")
                    else -> {
                        return if (args[2] == "invoke") {
                            if (args.size < 5) {
                                listOf("(function)")
                            } else {
                                listOf("[argument]")
                            }
                        } else if (args[2] == "run") {
                            listOf("[property_name:value]")
                        } else {
                            listOf()
                        }
                    }
                }
            }
        }
        return listOf()
    }

}