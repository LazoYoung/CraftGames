package com.github.lazoyoung.craftgames.command

import com.github.lazoyoung.craftgames.coordtag.BlockCapture
import com.github.lazoyoung.craftgames.coordtag.CoordTag
import com.github.lazoyoung.craftgames.coordtag.EntityCapture
import com.github.lazoyoung.craftgames.coordtag.TagMode
import com.github.lazoyoung.craftgames.game.Game
import com.github.lazoyoung.craftgames.player.GameEditor
import com.github.lazoyoung.craftgames.player.PlayerData
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.util.*
import java.util.function.Consumer
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
                        "CoordTag Command Manual (Page 1/2)",
                        "To use these commands, you must be in editor mode.", // TODO Clickable hyperlink command: /game edit
                        "/ctag create <tag> <mode> : Create a new tag.",
                        "/ctag capture <mode> : Capture a coordinate into the selected tag.",
                        "/ctag remove <tag> : Remove the selected tag.",
                        "/ctag tp <tag> <random/capture> : Teleport to the selected tag.", // TODO tp to randomized or specified capture(s)
                        "□ Move page: /ctag help <page>",
                        "--------------------------------------"
                ))
            } else if (args[1] == "2") {
                sender.sendMessage(arrayOf(
                        "--------------------------------------",
                        "CoordTag Command Manual (Page 2/2)",
                        "To use these commands, you must be in editor mode.", // TODO Clickable hyperlink command: /game edit
                        "/ctag list : Show all captures matching the flags.",
                        "/ctag list -mode <block/entity>",
                        "/ctag list -tag <name>",
                        "/ctag list -map <mapID>",
                        "/ctag list -reset",
                        "□ Move page: /ctag help <page>",
                        "--------------------------------------"
                ))
            } else return false
            return true
        }

        if (sender !is Player) {
            sender.sendMessage("This cannot be done from console.")
            return true
        }

        val playerData = PlayerData.get(sender)

        if (playerData !is GameEditor) {
            sender.sendMessage("You must be in editor mode.")
            return true
        }

        when (args[0].toLowerCase()) {
            "create" -> {
                if (args.size < 3)
                    return false

                val mode: TagMode
                val capture = playerData.capture

                try {
                    mode = TagMode.valueOf(args[2].toUpperCase())
                } catch (e: IllegalArgumentException) {
                    sender.sendMessage("[CoordTag] Illegal tag mode: ${args[2]}")
                    return false
                }

                when {
                    CoordTag.getTagNames(playerData.game, mode).contains(args[1]) -> {
                        sender.sendMessage("[CoordTag] This tag already exist!")
                    }
                    capture == null -> {
                        sender.sendMessage("[CoordTag] You need to capture a coordinate first.")
                    }
                    else -> {
                        capture.saveCapture(args[1])
                        sender.sendMessage("Created tag: ${args[1]}")
                    }
                }
            }
            "capture" -> {
                if (args.size < 2)
                    return false

                try {
                    when (TagMode.valueOf(args[1].toUpperCase())) {
                        TagMode.BLOCK -> {
                            playerData.requestBlockPrompt(Consumer {
                                playerData.capture = BlockCapture(playerData.game.map, it.x, it.y, it.z)
                                sender.sendMessage("Captured a block coordinate.")
                            })
                            sender.sendMessage("Please click a block to capture it.")
                        }
                        TagMode.ENTITY -> {
                            val loc = sender.location
                            playerData.capture = EntityCapture(playerData.game.map,
                                    loc.x, loc.y, loc.z, loc.yaw, loc.pitch)
                            sender.sendMessage("Captured an entity coordinate.")
                        }
                    }
                } catch (e: IllegalArgumentException) {
                    sender.sendMessage("[CoordTag] Illegal tag mode: ${args[2]}")
                }
            }
            "remove" -> TODO()
            "tp" -> TODO()
            "list" -> {
                var lastOption: String? = null

                if (args.size == 1) {
                    // TODO Display the list of result

                }
                else for (index in args.indices) {
                    if (index == 0)
                        continue

                    if (index % 2 == 1) {
                        lastOption = args[index]
                        continue
                    }

                    val value = args[index]
                    when (lastOption) {
                        "-mode" -> selectMode(sender, value)
                        "-tag" -> selectTag(sender, value)
                        "-map" -> selectMap(sender, value)
                        "-reset" -> reset(sender)
                    }
                }
            }
            else -> return false
        }
        return true
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

    private fun selectMode(sender: CommandSender, label: String) {
        try {
            select(sender, Flag.MODE, TagMode.valueOf(label.toUpperCase()))
            sender.sendMessage("[CoordTag] Selected the mode: ${label.toUpperCase()}")
        } catch (e: IllegalArgumentException) {
            sender.sendMessage("[CoordTag] Illegal flag: -select ${label.toUpperCase()}")
        }
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): MutableList<String>? {
        if (args.isEmpty())
            return command.aliases

        if (args.size == 1)
            return getCompletions(args[0], "help", "create", "capture", "remove", "list", "tp")

        when (args[0].toLowerCase()) {
            "help" -> return getCompletions(args[1], "1", "2")
            "capture", "create" -> return when (args.size) {
                3 -> getCompletions(args[args.size - 1], "block", "entity")
                else -> mutableListOf()
            }
            "remove" -> return mutableListOf()
            "list" -> {
                when (args.size % 2) { // Interpret -flag values
                    0 -> return getCompletions(args[args.size - 1], "-tag", "-mode", "-map", "-reset")
                    1 -> return when (args[args.size - 2].toLowerCase()) {
                        "-mode" -> getCompletions(args[args.size - 1], "block", "entity")
                        "-tag" -> { // TODO Game source is moved to GameEditor
                            val game = getSelection(sender, Flag.GAME) as Game?
                            val mode = getSelection(sender, Flag.MODE) as TagMode?
                            game?.let { getCompletions(args[args.size - 1],
                                    *CoordTag.getTagNames(it, mode).toTypedArray()) }
                                    ?: mutableListOf()
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

    private fun getSelection(sender: CommandSender, flag: Flag): Any? {
        val map = flagState[sender.name] ?: EnumMap(Flag::class.java)
        return map[flag]
    }

    private fun select(sender: CommandSender, flag: Flag, value: Any) {
        val map = flagState[sender.name] ?: EnumMap(Flag::class.java)
        map[flag] = value
        flagState[sender.name] = map
    }
}