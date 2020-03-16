package com.github.lazoyoung.craftgames.command

import com.github.lazoyoung.craftgames.coordtag.BlockCapture
import com.github.lazoyoung.craftgames.coordtag.CoordTag
import com.github.lazoyoung.craftgames.coordtag.EntityCapture
import com.github.lazoyoung.craftgames.coordtag.TagMode
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
                val capture = editor.capture

                try {
                    mode = TagMode.valueOf(args[2].toUpperCase())
                } catch (e: IllegalArgumentException) {
                    sender.sendMessage("[CoordTag] Illegal tag mode: ${args[2]}")
                    return false
                }

                when {
                    CoordTag.getTagNames(editor.game, mode).contains(args[1]) -> {
                        sender.sendMessage("[CoordTag] This tag already exist!")
                    }
                    capture == null -> {
                        sender.sendMessage("[CoordTag] You need to capture a coordinate first.")
                    }
                    else -> {
                        capture.saveCapture(args[1])
                        sender.sendMessage("[CoordTag] Created tag: ${args[1]}")
                    }
                }
            }
            "capture" -> {
                if (args.size < 2)
                    return false

                try {
                    val mapID = editor.game.map.mapID
                    val tag = args[1]

                    when (CoordTag.getTagMode(editor.game, tag)) {
                        TagMode.ENTITY -> {
                            val loc = sender.location
                            val capture = EntityCapture(editor.game, mapID!!,
                                    loc.x, loc.y, loc.z, loc.yaw, loc.pitch)
                            capture.saveCapture(tag)
                            sender.sendMessage("[CoordTag] Captured an entity coordinate.")
                        }
                        TagMode.BLOCK -> {
                            editor.requestBlockPrompt(Consumer {
                                val capture = BlockCapture(editor.game, mapID!!, it.x, it.y, it.z)
                                capture.saveCapture(tag)
                                sender.sendMessage("[CoordTag] Captured a block coordinate.")
                            })
                            sender.sendMessage("[CoordTag] Please click a block to capture it.")
                        }
                        else -> {
                            sender.sendMessage("[CoordTag] That tag does not exist.")
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

                val game = editor.game

                if (CoordTag.removeTag(game, args[1], game.map.mapID)) {
                    sender.sendMessage("[CoordTag] Removed tag: ${args[1]}")
                } else {
                    sender.sendMessage("[CoordTag] That tag doesn't exist in this map.")
                }
            }
            "tp" -> {
                if (args.size < 2)
                    return false

                val game = editor.game
                var captures = CoordTag.getCaptures(game, null, args[1], game.map.mapID)

                if (args.size == 3) {
                    captures = captures.filter { it.index == args[2].toIntOrNull() }

                    if (captures.isEmpty()) {
                        editor.player.sendMessage("[CoordTag] Unable to find a capture with index: ${args[2]}")
                    } else {
                        captures.first().teleport(editor.player)
                        editor.player.sendMessage("[CoordTag] Teleported to ${args[1]} index ${args[2]}.")
                    }
                } else if (captures.isEmpty()) {
                    editor.player.sendMessage("[CoordTag] Tag ${args[1]} does not exist in this map.")
                } else {
                    captures.first().teleport(editor.player)
                    editor.player.sendMessage("[CoordTag] Teleported to a random capture within tag: ${args[1]}.")
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

        when (args[0].toLowerCase()) {
            "help" -> return getCompletions(args[1], "1", "2")
            "create" -> if (args.size == 3) {
                return getCompletions(args[2], "block", "entity")
            }
            "capture", "remove", "tp" -> {
                val playerData = PlayerData.get(sender as? Player)

                if (playerData !is GameEditor)
                    return mutableListOf()

                if (args[0].equals("tp", true) && args.size == 3) {
                    val game = playerData.game
                    return getCompletions(
                            query = args[2],
                            args = *CoordTag.getCaptures(game, null, args[1], game.map.mapID)
                                    .map { it.index.toString() }.toTypedArray()
                    )
                } else if (args.size == 2) {
                    val list = CoordTag.getTagNames(playerData.game, null)
                    return getCompletions(args[1], *list.toTypedArray())
                }
            }
            "list" -> {
                val playerData = PlayerData.get(sender as? Player)

                if (playerData !is GameEditor)
                    return mutableListOf()

                when (args.size % 2) { // Interpret -flag values
                    0 -> return getCompletions(args[args.size - 1], "-tag", "-mode", "-map", "-reset")
                    1 -> return when (args[args.size - 2].toLowerCase()) {
                        "-mode" -> getCompletions(args[args.size - 1], "block", "entity")
                        "-tag" -> {
                            val mode = modeSel[playerData.player.uniqueId]
                            getCompletions(args[args.size - 1],
                                    *CoordTag.getTagNames(playerData.game, mode).toTypedArray())
                        }
                        "-map" -> {
                            playerData.game.map.getMapList().toMutableList()
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
        val result = if (tagSel.isNullOrEmpty()) {
            CoordTag.getCaptures(editor.game, modeSel, null, mapSel)
        } else {
            CoordTag.getCaptures(editor.game, null, tagSel, mapSel)
        }
        if (tagSel == null) {
            player.sendMessage("[CoordTag] Searching for $modeLabel tags inside $mapLabel...")
        } else {
            player.sendMessage("[CoordTag] Searching for $tagSel tag inside $mapLabel...")
        }

        if (result.isEmpty()) {
            player.sendMessage("No result found.")
            return
        }

        result.groupBy { it.tagName }.forEach { (tagName, tagList) ->
            player.sendMessage(arrayOf(" ", "Tag \'$tagName\' >"))
            for (tag in tagList) {
                val i = tag.index
                val x = tag.x
                val y = tag.y
                val z = tag.z
                val mapID = tag.mapID

                if (tag is BlockCapture)
                    player.sendMessage("Block capture $i at ($x, $y, $z) inside $mapID") // TODO Clickable text
                else if (tag is EntityCapture)
                    player.sendMessage("Entity capture $i at ($x, $y, $z) inside $mapID")
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
        val mode = modeSel[editor.player.uniqueId]

        if (CoordTag.getTagNames(editor.game, mode).contains(name)) {
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