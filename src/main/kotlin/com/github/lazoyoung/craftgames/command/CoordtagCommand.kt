package com.github.lazoyoung.craftgames.command

import com.github.lazoyoung.craftgames.coordtag.*
import com.github.lazoyoung.craftgames.player.GameEditor
import com.github.lazoyoung.craftgames.player.PlayerData
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.util.*
import java.util.function.Consumer
import kotlin.collections.HashMap

class CoordtagCommand : CommandBase {
    private val modeSel = HashMap<UUID, TagMode>()
    private val mapSel = HashMap<UUID, String>()
    private val tagSel = HashMap<UUID, String>()

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (args.isEmpty() || args[0].equals("help", true)) {
            if (args.size < 2 || args[1] == "1") {
                sender.sendMessage(arrayOf(
                        "--------------------------------------",
                        "CoordTag Command Manual (Page 1/2)",
                        "To use these commands, you must be in editor mode.", // TODO Clickable hyperlink command: /game edit
                        "/ctag create <tag> <mode> : Create a new tag.",
                        "/ctag capture <tag> : Capture a coordinate into the tag.",
                        "/ctag remove <tag> : Remove the selected tag.",
                        "/ctag tp <tag> [index] : Teleport to tag location.",
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
            sender.sendMessage("[CoordTag] This cannot be done from console.")
            return true
        }

        val editor = PlayerData.get(sender)

        if (editor !is GameEditor) {
            sender.sendMessage("[CoordTag] You must be in editor mode.")
            return true
        }

        when (args[0].toLowerCase()) {
            "create" -> {
                if (args.size < 3)
                    return false

                val mode: TagMode

                try {
                    mode = TagMode.valueOf(args[2].toUpperCase())
                } catch (e: IllegalArgumentException) {
                    sender.sendMessage("[CoordTag] Illegal tag mode: ${args[2]}")
                    return false
                }

                when {
                    CoordTag.getAll(editor.game).any { it.name == args[1] } -> {
                        sender.sendMessage("[CoordTag] This tag already exist!")
                    }
                    else -> {
                        CoordTag.create(editor.game, mode, args[1])
                        sender.sendMessage("[CoordTag] Created tag: ${args[1]}")
                    }
                }
            }
            "capture" -> {
                if (args.size < 2)
                    return false

                try {
                    val tag = CoordTag.getAll(editor.game).firstOrNull { it.name == args[1] }

                    if (tag == null) {
                        sender.sendMessage("[CoordTag] That tag does not exist.")
                    }
                    else when (tag.mode) {
                        TagMode.ENTITY -> {
                            val loc = sender.location
                            val capture = EntityCapture(loc.x, loc.y, loc.z, loc.yaw, loc.pitch, editor.mapID!!)
                            capture.add(tag)
                            sender.sendMessage("[CoordTag] Captured an entity coordinate.")
                        }
                        TagMode.BLOCK -> {
                            editor.requestBlockPrompt(Consumer {
                                val capture = BlockCapture(it.x, it.y, it.z, editor.mapID!!)
                                capture.add(tag)
                                sender.sendMessage("[CoordTag] Captured a block coordinate.")
                            })
                            sender.sendMessage("[CoordTag] Please click a block to capture it.")
                        }
                    }
                } catch (e: NullPointerException) {
                    sender.sendMessage("[CoordTag] Unexpected error! See console for details.")
                    e.printStackTrace()
                }
            }
            "remove" -> {
                if (args.size < 2)
                    return false

                val tag = CoordTag.getAll(editor.game).firstOrNull { it.name == args[1] }

                if (tag == null) {
                    sender.sendMessage("[CoordTag] That tag doesn't exist.")
                } else {
                    tag.remove()
                    sender.sendMessage("[CoordTag] Removed tag: ${args[1]}")
                }
            }
            "tp" -> {
                if (args.size < 2)
                    return false

                val tag = CoordTag.getAll(editor.game).firstOrNull { it.name == args[1] }
                val captures = tag?.getCaptures(editor.mapID)

                when {
                    tag == null -> {
                        sender.sendMessage("[CoordTag] Tag ${args[1]} does not exist.")
                    }
                    captures.isNullOrEmpty() -> {
                        sender.sendMessage("[CoordTag] Tag ${args[1]} has no captures in this map.")
                    }
                    args.size == 2 -> {
                        captures.random().teleport(sender)
                        sender.sendMessage("[CoordTag] Teleported to a random capture within tag: ${args[1]}.")
                    }
                    else -> {
                        val capture = captures.firstOrNull { it.index == args[2].toIntOrNull() }

                        if (capture != null) {
                            capture.teleport(sender)
                            sender.sendMessage("[CoordTag] Teleported to capture ${args[2]} within tag: ${args[1]}.")
                        } else {
                            sender.sendMessage("[CoordTag] Unable to find capture with index ${args[2]}.")
                        }
                    }
                }
            }
            "list" -> {
                var lastOption: String? = null

                if (args.size == 1) {
                    showCaptureList(editor)
                }
                else for (index in args.indices) {
                    if (index == 0)
                        continue

                    if (index % 2 == 1) {
                        if (args[index].equals("-reset", true)) {
                            reset(editor)
                        }

                        lastOption = args[index].toLowerCase()
                        continue
                    }

                    val value = args[index]
                    when (lastOption) {
                        "-mode" -> selectMode(editor, value)
                        "-tag" -> selectTag(editor, value)
                        "-map" -> selectMap(editor, value)
                    }
                }
            }
            else -> return false
        }
        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): MutableList<String>? {
        if (args.isEmpty())
            return command.aliases

        if (args.size == 1)
            return getCompletions(args[0], "help", "create", "capture", "remove", "list", "tp")

        val editor = PlayerData.get(sender as? Player)

        if (editor !is GameEditor)
            return mutableListOf()

        when (args[0].toLowerCase()) {
            "help" -> return getCompletions(args[1], "1", "2")
            "create" -> if (args.size == 3) {
                return getCompletions(args[2], "block", "entity")
            }
            "tp" -> {
                return if (args.size < 3) {
                    getCompletions(
                            query = args[1],
                            args = *CoordTag.getAll(editor.game).filter { it.getLocalCaptures().isNotEmpty() }
                                    .map { it.name }.toTypedArray())
                } else {
                    getCompletions(
                            query = args[2],
                            args = *CoordTag.get(editor.game, args[1])
                                    ?.getLocalCaptures()
                                    ?.map { it.index.toString() }
                                    ?.toTypedArray() ?: emptyArray()
                    )
                }
            }
            "capture", "remove" -> {
                if (args.size == 2) {
                    return getCompletions(args[1], *CoordTag.getAll(editor.game).map { it.name }.toTypedArray())
                }
            }
            "list" -> {
                when (args.size % 2) { // Interpret -flag values
                    0 -> return getCompletions(args[args.size - 1], "-tag", "-mode", "-map", "-reset")
                    1 -> return when (args[args.size - 2].toLowerCase()) {
                        "-mode" -> getCompletions(args[args.size - 1], "block", "entity")
                        "-tag" -> {
                            val mode = modeSel[editor.player.uniqueId]
                            val map = mapSel[editor.player.uniqueId]
                            getCompletions(
                                    query = args[args.size - 1],
                                    args = *CoordTag.getAll(editor.game)
                                            .filter { mode == null || mode == it.mode
                                                    && it.getCaptures(map).isNotEmpty() }
                                            .map { it.name }.toTypedArray())
                        }
                        "-map" -> {
                            editor.game.map.getMapList().toMutableList()
                        }
                        else -> mutableListOf()
                    }
                }
                return mutableListOf()
            }
        }
        return mutableListOf()
    }

    private fun showCaptureList(editor: GameEditor) {
        val player = editor.player
        val modeSel = modeSel[editor.player.uniqueId]
        val tagSel = tagSel[editor.player.uniqueId]
        val mapSel = mapSel[editor.player.uniqueId]
        val modeLabel = modeSel?.label ?: "all"
        val mapLabel = if (mapSel.isNullOrBlank()) {
            "every maps"
        } else {
            mapSel
        }
        val captures = HashMap<CoordTag, List<CoordCapture>>()

        if (tagSel.isNullOrEmpty()) { // All tags matching the selection
            for (tag in CoordTag.getAll(editor.game)) {
                if (modeSel == null || tag.mode == modeSel) {
                    captures[tag] = tag.getCaptures(mapSel)
                }
            }
        } else { // Specific tag (ignore mode selection)
            val tag = CoordTag.get(editor.game, tagSel)

            if (tag != null) {
                captures[tag] = tag.getCaptures(mapSel)
            }
        }

        if (tagSel == null) {
            player.sendMessage("[CoordTag] Searching for $modeLabel tags inside $mapLabel...")
        } else {
            player.sendMessage("[CoordTag] Searching for $tagSel tag inside $mapLabel...")
        }

        if (captures.isEmpty()) {
            player.sendMessage("No result found.")
            return
        }

        captures.forEach {
            val mode = it.key.mode.label.capitalize()
            val name = it.key.name

            player.sendMessage(arrayOf(" ", "$mode Tag \'$name\' >"))
            for (capture in it.value) {
                val i = capture.index
                val x = capture.x
                val y = capture.y
                val z = capture.z
                val mapID = capture.mapID

                if (capture is BlockCapture)
                    player.sendMessage("Capture $i at ($x, $y, $z) inside $mapID") // TODO Clickable text
                else if (capture is EntityCapture)
                    player.sendMessage("Capture $i at ($x, $y, $z) inside $mapID")
            }
            player.sendMessage(" ")
        }
    }

    private fun reset(editor: GameEditor) {
        mapSel.remove(editor.player.uniqueId)
        modeSel.remove(editor.player.uniqueId)
        tagSel.remove(editor.player.uniqueId)
        editor.player.sendMessage("[CoordTag] Previous flags have been reset.")
    }

    private fun selectMap(editor: GameEditor, id: String) {
        val player = editor.player

        if (editor.game.map.getMapList().contains(id)) {
            mapSel[editor.player.uniqueId] = id
            player.sendMessage("[CoordTag] Selected the map: $id")
        } else {
            player.sendMessage("[CoordTag] Map $id is not found in ${editor.game.id}!")
        }
    }

    private fun selectTag(editor: GameEditor, name: String) {
        val player = editor.player

        if (CoordTag.get(editor.game, name) != null) {
            tagSel[editor.player.uniqueId] = name
            player.sendMessage("[CoordTag] Selected the tag: $name")
        } else {
            player.sendMessage("[CoordTag] Tag does not exist! You should create one: /ctag create <name>")
        }
    }

    private fun selectMode(editor: GameEditor, label: String) {
        val player = editor.player

        try {
            modeSel[editor.player.uniqueId] = TagMode.valueOf(label.toUpperCase())
            player.sendMessage("[CoordTag] Selected the mode: ${label.toUpperCase()}")
        } catch (e: IllegalArgumentException) {
            player.sendMessage("[CoordTag] Illegal flag: -select ${label.toUpperCase()}")
        }
    }
}