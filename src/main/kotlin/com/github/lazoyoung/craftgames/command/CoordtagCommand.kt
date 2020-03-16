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
                        "/ctag capture <mode> : Capture a coordinate into the selected tag.",
                        "/ctag remove <tag> : Remove the selected tag.",
                        "/ctag tp <tag> <random/capture> : Teleport to the selected tag.",
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

                val mapID = editor.game.map.mapID

                try {
                    when (TagMode.valueOf(args[1].toUpperCase())) {
                        TagMode.BLOCK -> {
                            editor.requestBlockPrompt(Consumer {
                                editor.capture = BlockCapture(editor.game, mapID!!, it.x, it.y, it.z)
                                sender.sendMessage("[CoordTag] Captured a block coordinate.")
                            })
                            sender.sendMessage("[CoordTag] Please click a block to capture it.")
                        }
                        TagMode.ENTITY -> {
                            val loc = sender.location
                            editor.capture = EntityCapture(editor.game, mapID!!,
                                    loc.x, loc.y, loc.z, loc.yaw, loc.pitch)
                            sender.sendMessage("[CoordTag] Captured an entity coordinate.")
                        }
                    }
                } catch (e: IllegalArgumentException) {
                    sender.sendMessage("[CoordTag] Illegal tag mode: ${args[2]}")
                } catch (e: NullPointerException) {
                    sender.sendMessage("[CoordTag] Unexpected error! See console for details.")
                    e.printStackTrace()
                }
            }
            "remove" -> TODO()
            "tp" -> TODO()
            "list" -> {
                var lastOption: String? = null

                if (args.size == 1) {
                    showCaptureList(editor)
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
                        "-mode" -> selectMode(editor, value)
                        "-tag" -> selectTag(editor, value)
                        "-map" -> selectMap(editor, value)
                        "-reset" -> reset(editor)
                    }
                }
            }
            else -> return false
        }
        return true
    }

    private fun showCaptureList(editor: GameEditor) {
        val player = editor.player
        val mode = modeSel[editor.player.uniqueId]
        val tagSel = tagSel[editor.player.uniqueId]
        val map = mapSel[editor.player.uniqueId]
        val modeLabel = mode?.label ?: "all"
        val mapLabel = if (map.isNullOrBlank()) {
            "every maps"
        } else {
            map
        }
        val result = CoordTag.getCaptures(editor.game, mode, tagSel, map)

        if (tagSel == null) {
            player.sendMessage("Searching for $modeLabel captures inside $mapLabel...")
        } else {
            player.sendMessage("Searching for $modeLabel captures of $tagSel tag inside $mapLabel...")
        }

        if (result.isEmpty()) {
            player.sendMessage("No result.")
            return
        }

        result.groupBy { it.tagName }.forEach { (tagName, tagList) ->
            player.sendMessage("[Captures of Tag $tagName]")
            for (tag in tagList) {
                val i = tag.index
                val x = tag.x
                val y = tag.y
                val z = tag.z
                val mapID = tag.mapID

                if (tag is BlockCapture)
                    player.sendMessage("Block capture $i - $x, $y, $z inside $mapID")
                else if (tag is EntityCapture)
                    player.sendMessage("Entity capture $i - $x, $y, $z inside $mapID")
            }
            player.sendMessage(" ")
        }
    }

    private fun reset(editor: GameEditor) {
        modeSel.remove(editor.player.uniqueId)
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
            "tp" -> {
                return if (args.size == 2) {
                    getCompletions(args[1], "random", "capture")
                } else {
                    mutableListOf()
                }
            }
            else -> return mutableListOf()
        }
    }
}