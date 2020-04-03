package com.github.lazoyoung.craftgames.command

import com.github.lazoyoung.craftgames.api.ActionbarTask
import com.github.lazoyoung.craftgames.api.TimeUnit
import com.github.lazoyoung.craftgames.api.Timer
import com.github.lazoyoung.craftgames.coordtag.capture.AreaCapture
import com.github.lazoyoung.craftgames.coordtag.capture.BlockCapture
import com.github.lazoyoung.craftgames.coordtag.capture.SpawnCapture
import com.github.lazoyoung.craftgames.coordtag.tag.CoordTag
import com.github.lazoyoung.craftgames.coordtag.tag.TagMode
import com.github.lazoyoung.craftgames.game.Game
import com.github.lazoyoung.craftgames.game.player.GameEditor
import com.github.lazoyoung.craftgames.game.player.PlayerData
import net.md_5.bungee.api.ChatColor
import net.md_5.bungee.api.chat.ClickEvent
import net.md_5.bungee.api.chat.ComponentBuilder
import net.md_5.bungee.api.chat.HoverEvent
import net.md_5.bungee.api.chat.TextComponent
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.util.*
import java.util.function.BiConsumer
import java.util.function.Consumer
import kotlin.collections.HashMap

class CoordtagCommand : CommandBase {
    private val modeSel = HashMap<UUID, TagMode>()
    private val mapSel = HashMap<UUID, String>()
    private val tagSel = HashMap<UUID, String>()

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("[CoordTag] This cannot be done from console.")
            return true
        }

        val pdata = PlayerData.get(sender)

        if (args.isEmpty() || args[0].equals("help", true)) {
            if (args.size < 2 || args[1] == "1") {
                val warning = if (pdata is GameEditor) {
                    ComponentBuilder("\n").create()
                } else {
                    ComponentBuilder("Note: You must be in editor mode.\n\n").color(ChatColor.YELLOW)
                            .event(HoverEvent(HOVER_TEXT, ComponentBuilder("Click here to start editing.").color(ChatColor.GOLD).create()))
                            .event(ClickEvent(SUGGEST_CMD, "/game edit "))
                            .create()
                }

                val elements = PageElement.getPageComponents(
                        PageElement("◎ /ctag create (tag) <mode>", "Create a new tag in specific mode.", "/ctag create"),
                        PageElement("◎ /ctag capture (tag)", "Capture the current coordinate for specific tag", "/ctag capture"),
                        PageElement("◎ /ctag remove (tag)", "Wipe out the whole tag.", "/ctag remove"),
                        PageElement("◎ /ctag tp (tag) [index]", "Teleport to one of the captures defined in this tag.", "/ctag tp")
                )

                sender.sendMessage(
                    *ComponentBuilder(BORDER_STRING)
                            .append("\nCoordTag Command Manual (Page 1/3)\n", RESET_FORMAT)
                            .append(warning, RESET_FORMAT)
                            .append(elements)
                            .append(PREV_NAV_END, RESET_FORMAT)
                            .append("- PAGE NAVIGATION -", RESET_FORMAT)
                            .append(NEXT_NAV).event(ClickEvent(RUN_CMD, "/ctag help 2"))
                            .append(BORDER_STRING, RESET_FORMAT).create()
                )
            } else if (args[1] == "2") {
                val warning = if (pdata != null) {
                    ComponentBuilder("\n").create()
                } else {
                    ComponentBuilder("Note: You must be in a game.\n\n").color(ChatColor.YELLOW)
                            .event(HoverEvent(HOVER_TEXT, ComponentBuilder("Click here to play a game.").color(ChatColor.GOLD).create()))
                            .event(ClickEvent(SUGGEST_CMD, "/join "))
                            .create()
                }

                val elements = PageElement.getPageComponents(
                        PageElement("◎ /ctag list", "Show all captures matching the flags.", "/ctag list"),
                        PageElement("◎ /ctag list -mode <mode>", "Apply filter by capture mode.", "/ctag list -mode "),
                        PageElement("◎ /ctag list -tag (name)", "Apply filter by specific tag.", "/ctag list -tag "),
                        PageElement("◎ /ctag list -map (mapID)", "Apply filter by specific map.", "/ctag list -map "),
                        PageElement("◎ /ctag list -reset", "Reset all filters.", "/ctag list -reset")
                )

                sender.sendMessage(
                    *ComponentBuilder(BORDER_STRING)
                            .append("\nCoordTag Command Manual (Page 2/3)\n", RESET_FORMAT)
                            .append(warning, RESET_FORMAT)
                            .append(elements)
                            .append(PREV_NAV, RESET_FORMAT).event(ClickEvent(RUN_CMD, "/ctag help 1"))
                            .append("- PAGE NAVIGATION -", RESET_FORMAT)
                            .append(NEXT_NAV).event(ClickEvent(RUN_CMD, "/ctag help 3"))
                            .append(BORDER_STRING, RESET_FORMAT).create()
                )
            } else if (args[1] == "3") {
                val warning = if (pdata != null) {
                    ComponentBuilder("\n").create()
                } else {
                    ComponentBuilder("Note: You must be in a game.\n\n").color(ChatColor.YELLOW)
                            .event(HoverEvent(HOVER_TEXT, ComponentBuilder("Click here to play a game.").color(ChatColor.GOLD).create()))
                            .event(ClickEvent(SUGGEST_CMD, "/join "))
                            .create()
                }

                val elements = PageElement.getPageComponents(
                        PageElement("◎ /ctag display (tag) <index>", "Display a particular capture.", "/ctag display ")
                )

                sender.sendMessage(
                        *ComponentBuilder(BORDER_STRING)
                                .append("\nCoordTag Command Manual (Page 3/3)\n", RESET_FORMAT)
                                .append(warning, RESET_FORMAT)
                                .append(elements)
                                .append(PREV_NAV, RESET_FORMAT).event(ClickEvent(RUN_CMD, "/ctag help 2"))
                                .append(NEXT_NAV_END)
                                .append(BORDER_STRING, RESET_FORMAT).create()
                )
            } else return false
            return true
        }

        when (args[0].toLowerCase()) {
            "list" -> {
                var lastOption: String? = null

                if (pdata == null) {
                    sender.sendMessage(
                            *ComponentBuilder("[CoordTag] You must be in a game.")
                                    .event(HoverEvent(HOVER_TEXT, ComponentBuilder("Click here to join.").color(ChatColor.GOLD).create()))
                                    .event(ClickEvent(SUGGEST_CMD, "/join ")).create()
                    )
                    return true
                }

                if (args.size == 1) {
                    showCaptureList(pdata)
                } else for (index in args.indices) {
                    if (index == 0)
                        continue

                    if (index % 2 == 1) {
                        if (args[index].equals("-reset", true)) {
                            reset(pdata)
                        }

                        lastOption = args[index].toLowerCase()
                        continue
                    }

                    val value = args[index]
                    when (lastOption) {
                        "-mode" -> selectMode(pdata, value)
                        "-tag" -> selectTag(pdata, value)
                        "-map" -> selectMap(pdata, value)
                    }
                }
                return true
            }
            "tp", "display" -> {
                if (args.size < 2)
                    return false

                if (pdata == null) {
                    sender.sendMessage(
                            *ComponentBuilder("[CoordTag] You must be in a game.")
                                    .event(HoverEvent(HOVER_TEXT, ComponentBuilder("Click here to join.").color(ChatColor.GOLD).create()))
                                    .event(ClickEvent(SUGGEST_CMD, "/join ")).create()
                    )
                    return true
                }

                val tag = CoordTag.getAll(pdata.game).firstOrNull { it.name == args[1] }
                val captures = tag?.getCaptures(pdata.game.map.id)
                val tagName = tag?.name

                if (tag == null) {
                    sender.sendMessage("[CoordTag] Tag $tagName does not exist.")
                    return true
                }
                if (captures.isNullOrEmpty()) {
                    sender.sendMessage("[CoordTag] Tag $tagName has no captures in this map.")
                    return true
                }

                if (args[0].equals("tp", true)) {
                    when (args.size) {
                        2 -> {
                            val capture = captures.random()

                            sender.teleport(capture.toLocation(sender.world))
                            ActionbarTask(sender, text = *arrayOf("&9Teleported to $tagName/${capture.index}"))
                                    .start()
                        }
                        else -> {
                            val capture = captures.firstOrNull {
                                it.index == args[2].toIntOrNull()
                            }

                            if (capture == null) {
                                sender.sendMessage("[CoordTag] Unable to find capture with index ${args[2]}.")
                            } else {
                                if (capture is AreaCapture) {
                                    val timer = Timer(TimeUnit.SECOND, 20)

                                    capture.displayBorder(sender.world, 5, timer)
                                }

                                sender.teleport(capture.toLocation(sender.world))
                                sender.sendMessage("[CoordTag] Teleported to $tagName/${capture.index}.")
                            }
                        }
                    }
                } else { // display
                    if (args.size == 2) {
                        sender.sendMessage("[CoordTag] Do not omit the index of capture!")
                    } else {
                        val capture = captures.firstOrNull {
                            it.index == args[2].toIntOrNull()
                        }

                        when (capture) {
                            null -> {
                                sender.sendMessage("[CoordTag] Unable to find capture with index ${args[2]}.")
                            }
                            is AreaCapture -> {
                                val timer = Timer(TimeUnit.SECOND, 20)

                                capture.displayBorder(sender.world, 5, timer)
                                ActionbarTask(
                                        player = sender,
                                        period = timer,
                                        text = *arrayOf("&9Displaying area $tagName/${capture.index}")
                                ).start()
                            }
                            else -> {
                                sender.sendMessage("[CoordTag] Display feature doesn't support ${tag.mode} tags yet.")
                            }
                        }
                    }
                }
                return true
            }
        }

        if (pdata !is GameEditor) {
            sender.sendMessage(
                    *ComponentBuilder("[CoordTag] You must be in editor mode.")
                            .event(HoverEvent(HOVER_TEXT, ComponentBuilder("Click here to start editing.").color(ChatColor.GOLD).create()))
                            .event(ClickEvent(SUGGEST_CMD, "/game edit ")).create()
            )
            return true
        }

        when (args[0].toLowerCase()) {
            "create" -> {
                if (args.size < 3)
                    return false

                val mode: TagMode
                val game = pdata.game

                try {
                    mode = TagMode.valueOf(args[2].toUpperCase())
                } catch (e: IllegalArgumentException) {
                    sender.sendMessage("[CoordTag] Illegal tag mode: ${args[2]}")
                    return false
                }

                when {
                    CoordTag.getAll(game).any { it.name == args[1] } -> {
                        sender.sendMessage("[CoordTag] This tag already exist!")
                    }
                    else -> {
                        CoordTag.create(game.resource, game.map.id, mode, args[1])
                        ActionbarTask(sender, text = *arrayOf("Tag ${args[1]} has been created."))
                                .start()
                    }
                }
            }
            "capture" -> {
                if (args.size < 2)
                    return false

                try {
                    val tag = CoordTag.getAll(pdata.game).firstOrNull { it.name == args[1] }

                    if (tag == null) {
                        sender.sendMessage("[CoordTag] That tag does not exist.")
                    }
                    else when (tag.mode) {
                        TagMode.SPAWN -> {
                            val loc = sender.location
                            SpawnCapture(loc.x, loc.y, loc.z, loc.yaw, loc.pitch, pdata.mapID)
                                    .add(tag)
                            ActionbarTask(sender, text = *arrayOf("&aCaptured a spawnpoint."))
                                    .start()
                        }
                        TagMode.BLOCK -> {
                            val dialog = ActionbarTask(
                                    sender, repeat = true, text = *arrayOf("&eClick a block to capture it!")
                            ).start()

                            pdata.requestBlockPrompt(Consumer {
                                BlockCapture(it.x, it.y, it.z, pdata.mapID)
                                        .add(tag)
                                ActionbarTask(sender, text = *arrayOf("&aCaptured a block."))
                                        .start()
                                dialog.clear()
                            })
                        }
                        TagMode.AREA -> {
                            val dialog = ActionbarTask(
                                    sender, repeat = true, text = *arrayOf("&eClick a block to capture it!")
                            ).start()

                            pdata.requestAreaPrompt(BiConsumer { b1, b2 ->
                                AreaCapture(b1.x, b2.x, b1.y, b2.y, b1.z, b2.z, pdata.mapID)
                                        .add(tag)
                                ActionbarTask(sender, text = *arrayOf("&aCaptured an area."))
                                        .start()
                                dialog.clear()
                            })
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

                val tag = CoordTag.get(pdata.game, args[1])
                val tagName = tag?.name

                if (tag == null) {
                    sender.sendMessage("[CoordTag] ${args[1]} does not exist.")
                } else if (args.size == 2) {
                    tag.remove()
                    ActionbarTask(sender, text = *arrayOf("&aTag '$tagName' has been removed."))
                            .start()
                } else {
                    val capture = tag.getCaptures(pdata.mapID).firstOrNull { it.index == args[2].toIntOrNull() }

                    if (capture != null) {
                        tag.removeCapture(capture)
                        ActionbarTask(sender, text = *arrayOf("&aCapture '$tagName/${capture.index}' has been removed."))
                                .start()
                    } else {
                        sender.sendMessage("[CoordTag] Unknown capture index: ${args[2]}")
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
            return getCompletions(args[0], "help", "create", "capture", "remove", "list", "tp", "display")

        val pdata = PlayerData.get(sender as? Player)

        if (pdata != null) {
            when (args[0].toLowerCase()) {
                "help" -> return getCompletions(args[1], "1", "2", "3")
                "list" -> {
                    when (args.size % 2) { // Interpret -flag values
                        0 -> return getCompletions(args[args.size - 1], "-tag", "-mode", "-map", "-reset")
                        1 -> return when (args[args.size - 2].toLowerCase()) {
                            "-mode" -> getCompletions(args[args.size - 1], "block", "area", "spawn")
                            "-tag" -> {
                                val mode = modeSel[pdata.player.uniqueId]
                                val map = mapSel[pdata.player.uniqueId]
                                getCompletions(
                                        query = args[args.size - 1],
                                        args = *CoordTag.getAll(pdata.game)
                                                .filter {
                                                    mode == null || mode == it.mode
                                                            && it.getCaptures(map).isNotEmpty()
                                                }
                                                .map { it.name }.toTypedArray())
                            }
                            "-map" -> {
                                Game.getMapNames(pdata.game.name).toMutableList()
                            }
                            else -> mutableListOf()
                        }
                    }
                    return mutableListOf()
                }
                "display", "tp" -> return when (args.size) {
                    2 -> getCompletions(
                            query = args[1],
                            args = *CoordTag.getAll(pdata.game).filter { it.getCaptures(pdata.game.map.id).isNotEmpty() }
                                    .map { it.name }.toTypedArray())
                    3 -> getCompletions(
                            query = args[2],
                            args = *CoordTag.get(pdata.game, args[1])
                                    ?.getCaptures(pdata.game.map.id)
                                    ?.map { it.index.toString() }
                                    ?.toTypedArray() ?: emptyArray()
                    )
                    else -> mutableListOf()
                }
                else -> if (pdata !is GameEditor) {
                    return mutableListOf()
                }
            }

            when (args[0].toLowerCase()) {
                "create" -> if (args.size == 3) {
                    return getCompletions(args[2], "block", "area", "spawn")
                }
                "capture" -> return when (args.size) {
                    2 -> getCompletions(args[1], *CoordTag.getAll(pdata.game).map { it.name }.toTypedArray())
                    else -> mutableListOf()
                }
                "remove" -> return when (args.size) {
                    2 -> getCompletions(args[1], *CoordTag.getAll(pdata.game).map { it.name }.toTypedArray())
                    3 -> {
                        val arr = CoordTag.get(pdata.game, args[1])
                                ?.getCaptures(pdata.mapID)
                                ?.mapNotNull { it.index?.toString() }
                                ?.toTypedArray()

                        getCompletions(args[2], *arr ?: emptyArray())
                    }
                    else -> mutableListOf()
                }
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

            loop@ for (capture in it.getCaptures(mapSel)) {
                val i = capture.index
                val text: TextComponent
                val mapID = capture.mapID
                val thisMapID = playerData.game.map.id

                when (capture) {
                    is SpawnCapture -> {
                        val x = capture.x
                        val y = capture.y
                        val z = capture.z
                        text = TextComponent("Spawnpoint $i at ($x, $y, $z) inside $mapID")
                    }
                    is BlockCapture -> {
                        val x = capture.x
                        val y = capture.y
                        val z = capture.z
                        text = TextComponent("Block $i at ($x, $y, $z) inside $mapID")
                    }
                    is AreaCapture -> {
                        val x1 = capture.x1
                        val x2 = capture.x2
                        val y1 = capture.y1
                        val y2 = capture.y2
                        val z1 = capture.z1
                        val z2 = capture.z2
                        text = TextComponent("Area $i at ($x1~$x2, $y1~$y2, $z1~$z2) inside $mapID")
                    }
                    else -> continue@loop
                }


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

        if (Game.getMapNames(playerData.game.name).contains(id)) {
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