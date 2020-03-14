package com.github.lazoyoung.craftgames.command

import com.github.lazoyoung.craftgames.coordtag.CoordTag
import com.github.lazoyoung.craftgames.coordtag.TagMode
import com.github.lazoyoung.craftgames.exception.FaultyConfiguration
import com.github.lazoyoung.craftgames.exception.GameNotFound
import com.github.lazoyoung.craftgames.game.Game
import com.github.lazoyoung.craftgames.game.GameFactory
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import java.util.*
import kotlin.collections.HashMap

class CoordtagCommand : CommandBase {
    private enum class Flag {
        GAME, /* Game instance (Game?) */
        MODE, /* Coordtag Type (TagMode?) */
        TAG, /* Coordtag Name (String?) */
        MAP /* Map ID (String?) */
    }
    private val flagState = HashMap<String, EnumMap<Flag, Any>>()

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (args.isEmpty() || args[0].equals("help", true)) {
            if (args.size < 2 || args[1] == "1") {
                sender.sendMessage(arrayOf(
                        "--------------------------------------",
                        "CoordTag Command Manual (Page 1/3)",
                        "/ctag select -game <title>",
                        "/ctag select -mode <block/entity>",
                        "/ctag select -tag <name>",
                        "/ctag select -map <mapID>",
                        "/ctag select -reset",
                        "□ Move page: /ctag help <page>",
                        "--------------------------------------"
                ))
            } else if (args[1] == "2") {
                sender.sendMessage(arrayOf(
                        "--------------------------------------",
                        "CoordTag Command Manual (Page 2/3)",
                        "Select flag prior to using this command.", // TODO Clickable hyperlink text
                        "/ctag create <name> : Create a new tag based on flags.",
                        "/ctag capture : Capture a coordinate into the selected tag.",
                        "/ctag remove : Remove the selected tag.",
                        "□ Move page: /ctag help <page>",
                        "--------------------------------------"
                ))
            } else if (args[1] == "3") {
                sender.sendMessage(arrayOf(
                        "--------------------------------------",
                        "CoordTag Command Manual (Page 3/3)",
                        "Select flag prior to using this command.",
                        "/ctag list : Show all captures matching the flags.",
                        "/ctag tp <random/capture> : Teleport to the selected tag.", // TODO tp to randomized or specified capture(s)
                        "□ Move page: /ctag help <page>",
                        "--------------------------------------"
                ))
            } else return false
            return true
        }

        when (args[0].toLowerCase()) {
            "select" -> {
                var lastOption: String? = null

                for (index in args.indices) {
                    if (index % 2 == 1) {
                        lastOption = args[index]
                        continue
                    }

                    val value = args[index]
                    when (lastOption) {
                        "-game" -> selectGame(sender, value)
                        "-mode" -> selectMode(sender, value)
                        "-tag" -> selectTag(sender, value)
                        "-map" -> selectMap(sender, value)
                        "-reset" -> reset(sender)
                    }
                }
            }
            "create" -> {

            }
            "capture" -> TODO()
            "remove" -> TODO()
            "list" -> TODO()
            "tp" -> TODO()
        }

        return false
    }

    private fun reset(sender: CommandSender) {
        flagState.remove(sender.name)
        sender.sendMessage("[CoordTag] Previous flags have been reset.")
    }

    private fun selectMap(sender: CommandSender, id: String) {
        val game = getSelection(sender, Flag.GAME) as Game?

        if (game != null) {
            if (game.map.getMapList().contains(id)) {
                select(sender, Flag.MAP, id)
                sender.sendMessage("[CoordTag] Selected the map: $id")
            } else {
                sender.sendMessage("[CoordTag] Map $id is not found in ${game.id}!")
            }
        } else {
            sender.sendMessage("[CoordTag] You must select a game before: -map $id")
        }
    }

    private fun selectTag(sender: CommandSender, name: String) {
        val game = getSelection(sender, Flag.GAME) as Game?
        val mode = getSelection(sender, Flag.MODE) as TagMode?

        if (game != null) {
            if (CoordTag.getTagNames(game, mode).contains(name)) {
                select(sender, Flag.TAG, name)
                sender.sendMessage("[CoordTag] Selected the tag: $name")
            } else {
                sender.sendMessage("[CoordTag] Tag does not exist! You should create one: /ctag create <name>")
            }
        } else {
            sender.sendMessage("[CoordTag] You must select a game before: -tag $name")
        }
    }

    private fun selectGame(sender: CommandSender, name: String) {
        try {
            val game = GameFactory.getDummy(name)
            select(sender, Flag.GAME, game)
            sender.sendMessage("[CoordTag] Selected the game: ${game.id}")
            return
        } catch (e: GameNotFound) {
            e.message?.let { sender.sendMessage(it) }
        } catch (e: FaultyConfiguration) {
            e.message?.let { sender.sendMessage(it) }
        } catch (e: RuntimeException) {
            e.message?.let { sender.sendMessage(it) }
            e.printStackTrace()
        }
        sender.sendMessage("[CoordTag] Failed to select: -game ${name.toLowerCase()}")
    }

    private fun selectMode(sender: CommandSender, label: String) {
        try {
            select(sender, Flag.MODE, TagMode.valueOf(label.toUpperCase()))
            sender.sendMessage("[CoordTag] Selected the mode: ${label.toUpperCase()}")
        } catch (e: IllegalArgumentException) {
            sender.sendMessage("[CoordTag] Illegal option: -select ${label.toUpperCase()}")
        }
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): MutableList<String>? {
        if (args.isEmpty())
            return command.aliases

        if (args.size == 1)
            return getCompletions(args[0], "help", "select", "create", "capture", "remove", "list", "tp")

        when (args[0].toLowerCase()) {
            "help" -> return getCompletions(args[1], "1", "2", "3")
            "select" -> {
                when (args.size % 2) { // Interpret -option values
                    0 -> return getCompletions(args[args.size - 1], "-game", "-tag", "-mode", "-map", "-reset")
                    1 -> return when (args[args.size - 2].toLowerCase()) {
                        "-game" -> getGameTitles(args[args.size - 1])
                        "-mode" -> getCompletions("block", "entity")
                        "-tag" -> {
                            val game = getSelection(sender, Flag.GAME) as Game?
                            val mode = getSelection(sender, Flag.MODE) as TagMode?
                            game?.let { CoordTag.getTagNames(it, mode) } ?: mutableListOf()
                        }
                        "map" -> {
                            val game = getSelection(sender, Flag.GAME) as Game?
                            game?.map?.getMapList()?.toMutableList() ?: mutableListOf()
                        }
                        else -> mutableListOf()
                    }
                }
                return mutableListOf()
            }
            "create" -> return mutableListOf()
            "capture" -> return mutableListOf()
            "remove" -> return mutableListOf()
            "list" -> return mutableListOf()
            "tp" -> {
                when (args.size) {
                    2 -> return getCompletions(args[1], "random", "capture")
                    3 -> {
                        if (args[1].equals("capture", true)) {
                            val game = getSelection(sender, Flag.GAME) as Game?
                            val mode = getSelection(sender, Flag.MODE) as TagMode?
                            val name = getSelection(sender, Flag.TAG) as String?
                            val map = getSelection(sender, Flag.MAP) as String?

                            if (game != null && mode != null && name != null && map != null) {
                                return getCompletions(
                                        query = args[2],
                                        args = *CoordTag.getCaptures(game, mode, name, map)
                                                .indices.map { it.toString() }
                                                .toTypedArray()
                                )
                            }
                        }
                    }
                }
                return mutableListOf()
            }
            else -> return mutableListOf()
        }
    }

    private fun getSelection(sender: CommandSender, flag: Flag) : Any? {
        val map = flagState[sender.name] ?: EnumMap(Flag::class.java)
        return map[flag]
    }

    private fun select(sender: CommandSender, flag: Flag, value: Any) {
        val map = flagState[sender.name] ?: EnumMap(Flag::class.java)
        map[flag] = value
        flagState[sender.name] = map
    }
}