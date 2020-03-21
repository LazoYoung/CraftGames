package com.github.lazoyoung.craftgames.command

import com.github.lazoyoung.craftgames.coordtag.BlockCapture
import com.github.lazoyoung.craftgames.coordtag.CoordTag
import com.github.lazoyoung.craftgames.coordtag.SpawnCapture
import com.github.lazoyoung.craftgames.coordtag.TagMode
import com.github.lazoyoung.craftgames.game.Game
import com.github.lazoyoung.craftgames.player.GameEditor
import com.github.lazoyoung.craftgames.player.PlayerData
import net.md_5.bungee.api.ChatColor
import net.md_5.bungee.api.chat.ClickEvent
import net.md_5.bungee.api.chat.HoverEvent
import net.md_5.bungee.api.chat.TextComponent
import org.bukkit.Location
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
                        "/ctag list -mode <mode>",
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

        val player = PlayerData.get(sender)

        if (args[0].equals("list", true)) {
            var lastOption: String? = null

            if (player == null) {
                sender.sendMessage("[CoordTag] You must be in a game.")
                return true
            }

            if (args.size == 1) {
                showCaptureList(player)
            }
            else for (index in args.indices) {
                if (index == 0)
                    continue

                if (index % 2 == 1) {
                    if (args[index].equals("-reset", true)) {
                        reset(player)
                    }

                    lastOption = args[index].toLowerCase()
                    continue
                }

                val value = args[index]
                when (lastOption) {
                    "-mode" -> selectMode(player, value)
                    "-tag" -> selectTag(player, value)
                    "-map" -> selectMap(player, value)
                }
            }
            return true
        }

        if (player !is GameEditor) {
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
                    CoordTag.getAll(player.game).any { it.name == args[1] } -> {
                        sender.sendMessage("[CoordTag] This tag already exist!")
                    }
                    else -> {
                        CoordTag.create(player.game, mode, args[1])
                        sender.sendMessage("[CoordTag] Created tag: ${args[1]}")
                    }
                }
            }
            "capture" -> {
                if (args.size < 2)
                    return false

                try {
                    val tag = CoordTag.getAll(player.game).firstOrNull { it.name == args[1] }

                    if (tag == null) {
                        sender.sendMessage("[CoordTag] That tag does not exist.")
                    }
                    else when (tag.mode) {
                        TagMode.SPAWN -> {
                            val loc = sender.location
                            val capture = SpawnCapture(loc.x, loc.y, loc.z, loc.yaw, loc.pitch, player.mapID)
                            capture.add(tag)
                            sender.sendMessage("[CoordTag] Captured a spawn coordinate.")
                        }
                        TagMode.BLOCK -> {
                            player.requestBlockPrompt(Consumer {
                                val capture = BlockCapture(it.x, it.y, it.z, player.mapID)
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

                val tag = CoordTag.getAll(player.game).firstOrNull { it.name == args[1] }

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

                val tag = CoordTag.getAll(player.game).firstOrNull { it.name == args[1] }
                val captures = tag?.getCaptures(player.mapID)

                when {
                    tag == null -> {
                        sender.sendMessage("[CoordTag] Tag ${args[1]} does not exist.")
                    }
                    captures.isNullOrEmpty() -> {
                        sender.sendMessage("[CoordTag] Tag ${args[1]} has no captures in this map.")
                    }
                    args.size == 2 -> {
                        when (val it = captures.random()) {
                            is BlockCapture
                                -> sender.teleport(Location(sender.world, it.x, it.y, it.z))
                            is SpawnCapture
                                -> sender.teleport(Location(sender.world, it.x, it.y, it.z, it.yaw, it.pitch))
                        }
                        sender.sendMessage("[CoordTag] Teleported to a random capture within tag: ${args[1]}.")
                    }
                    else -> {
                        val capture = captures.firstOrNull { it.index == args[2].toIntOrNull() }

                        if (capture != null) {
                            when (val it = captures.random()) {
                                is BlockCapture
                                    -> sender.teleport(Location(sender.world, it.x, it.y, it.z))
                                is SpawnCapture
                                    -> sender.teleport(Location(sender.world, it.x, it.y, it.z, it.yaw, it.pitch))
                            }
                            sender.sendMessage("[CoordTag] Teleported to capture ${args[2]} within tag: ${args[1]}.")
                        } else {
                            sender.sendMessage("[CoordTag] Unable to find capture with index ${args[2]}.")
                        }
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
                return getCompletions(args[2], "block", "spawn")
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
                        "-mode" -> getCompletions(args[args.size - 1], "block", "spawn")
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
                            Game.getMapList(editor.game.name).toMutableList()
                        }
                        else -> mutableListOf()
                    }
                }
                return mutableListOf()
            }
        }
        return mutableListOf()
    }

    private fun showCaptureList(playerData: PlayerData) {
        val player = playerData.player
        val modeSel = modeSel[player.uniqueId]
        val tagSel = tagSel[player.uniqueId]
        val mapSel = mapSel[player.uniqueId]
        val modeLabel = modeSel?.label ?: "all"
        val mapLabel = if (mapSel.isNullOrBlank()) {
            "every maps"
        } else {
            mapSel
        }

        /** The remainders that correspond to the selection **/
        val tags = ArrayList<CoordTag>()

        if (tagSel.isNullOrEmpty()) {
            CoordTag.getAll(playerData.game).forEach {
                if (modeSel == null || modeSel == it.mode) {
                    tags.add(it)
                }
            }
        } else {
            // Insert specific tag only. Disregard modeSel
            CoordTag.get(playerData.game, tagSel)?.let { tags.add(it) }
        }

        if (tagSel == null) {
            player.sendMessage("[CoordTag] Searching for $modeLabel tags inside $mapLabel...")
        } else {
            player.sendMessage("[CoordTag] Searching for $tagSel inside $mapLabel...")
        }

        if (tags.isEmpty()) {
            player.sendMessage("No result found.")
            return
        }

        tags.forEach {
            val mode = it.mode.label.capitalize()
            val name = it.name

            player.sendMessage(arrayOf(" ", "$mode Tag \'$name\' >"))
            for (capture in it.getCaptures(mapSel)) {
                val i = capture.index
                val x = capture.x
                val y = capture.y
                val z = capture.z
                val mapID = capture.mapID
                val thisMapID = playerData.game.map.mapID
                val text = TextComponent("Capture $i at ($x, $y, $z) inside $mapID")

                if (mapID == thisMapID) {
                    val hov = TextComponent("Click here to teleport.")
                    hov.color = ChatColor.GOLD
                    text.hoverEvent = HoverEvent(HoverEvent.Action.SHOW_TEXT, arrayOf(hov))
                    text.clickEvent = ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/ctag tp $name $i")
                } else {
                    val hov = TextComponent("This is outside the current map.")
                    hov.color = ChatColor.YELLOW
                    text.hoverEvent = HoverEvent(HoverEvent.Action.SHOW_TEXT, arrayOf(hov))
                    text.color = ChatColor.GRAY
                }

                if (capture is BlockCapture)
                    player.sendMessage(text)
                else if (capture is SpawnCapture)
                    player.sendMessage(text)
            }
            player.sendMessage(" ")
        }
    }

    private fun reset(playerData: PlayerData) {
        val player = playerData.player

        mapSel.remove(player.uniqueId)
        modeSel.remove(player.uniqueId)
        tagSel.remove(player.uniqueId)
        player.sendMessage("[CoordTag] Previous flags were reset.")
    }

    private fun selectMap(playerData: PlayerData, id: String) {
        val player = playerData.player

        if (Game.getMapList(playerData.game.name).contains(id)) {
            mapSel[playerData.player.uniqueId] = id
            player.sendMessage("[CoordTag] Selected the map: $id")
        } else {
            player.sendMessage("[CoordTag] Map $id is not found in ${playerData.game.id}!")
        }
    }

    private fun selectTag(playerData: PlayerData, name: String) {
        val player = playerData.player

        if (CoordTag.get(playerData.game, name) != null) {
            tagSel[playerData.player.uniqueId] = name
            player.sendMessage("[CoordTag] Selected the tag: $name")
        } else {
            player.sendMessage("[CoordTag] Tag does not exist! You should create one: /ctag create <name>")
        }
    }

    private fun selectMode(playerData: PlayerData, label: String) {
        val player = playerData.player

        try {
            modeSel[playerData.player.uniqueId] = TagMode.valueOf(label.toUpperCase())
            player.sendMessage("[CoordTag] Selected the mode: ${label.toUpperCase()}")
        } catch (e: IllegalArgumentException) {
            player.sendMessage("[CoordTag] Illegal flag: -select ${label.toUpperCase()}")
        }
    }
}