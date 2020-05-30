package com.github.lazoyoung.craftgames.impl.command

import com.github.lazoyoung.craftgames.api.ActionbarTask
import com.github.lazoyoung.craftgames.api.TimeUnit
import com.github.lazoyoung.craftgames.api.Timer
import com.github.lazoyoung.craftgames.api.tag.coordinate.*
import com.github.lazoyoung.craftgames.impl.game.player.GameEditor
import com.github.lazoyoung.craftgames.impl.game.player.PlayerData
import com.github.lazoyoung.craftgames.impl.tag.TagRegistry
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

class CoordTagCommand : CommandBase {
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

                val components = PageElement.getPageComponents(
                        PageElement("◎ /ctag create (tag) <mode>",
                                "Create a new tag.",
                                "/ctag create "),
                        PageElement("◎ /ctag capture (tag)",
                                "Capture current coordinate.",
                                "/ctag capture "),
                        PageElement("◎ /ctag remove (tag) [index]",
                                "Wipe out the whole tag. You may supply &eindex\n"
                                        + "&rif you need to delete a specific capture only.",
                                "/ctag remove "),
                        PageElement("◎ /ctag tp (tag) [index]",
                                "Teleport to one of captures defined in the tag.",
                                "/ctag tp ")
                )

                sender.sendMessage(
                    *ComponentBuilder()
                            .append(BORDER_STRING, RESET_FORMAT)
                            .append("\nCoordTag Command Manual (Page 1/3)\n", RESET_FORMAT)
                            .append(warning, RESET_FORMAT)
                            .append(components)
                            .append(PREV_NAV_END, RESET_FORMAT)
                            .append(PAGE_NAV, RESET_FORMAT)
                            .append(NEXT_NAV, RESET_FORMAT).event(ClickEvent(RUN_CMD, "/ctag help 2"))
                            .append(BORDER_STRING, RESET_FORMAT)
                            .create()
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
                        PageElement("◎ /ctag list", "Show all captures matching the flags.\n" +
                                "Flags: -mode, -tag, -map", "/ctag list"),
                        PageElement("◎ /ctag list -reset", "Reset all the flags.", "/ctag list -reset")
                )

                sender.sendMessage(
                    *ComponentBuilder()
                            .append(BORDER_STRING, RESET_FORMAT)
                            .append("\nCoordTag Command Manual (Page 2/3)\n", RESET_FORMAT)
                            .append(warning, RESET_FORMAT)
                            .append(elements)
                            .append(PREV_NAV, RESET_FORMAT).event(ClickEvent(RUN_CMD, "/ctag help 1"))
                            .append(PAGE_NAV, RESET_FORMAT)
                            .append(NEXT_NAV, RESET_FORMAT).event(ClickEvent(RUN_CMD, "/ctag help 3"))
                            .append(BORDER_STRING, RESET_FORMAT)
                            .create()
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
                        *ComponentBuilder()
                                .append(BORDER_STRING, RESET_FORMAT)
                                .append("\nCoordTag Command Manual (Page 3/3)\n", RESET_FORMAT)
                                .append(warning, RESET_FORMAT)
                                .append(elements)
                                .append(PREV_NAV, RESET_FORMAT).event(ClickEvent(RUN_CMD, "/ctag help 2"))
                                .append(PAGE_NAV, RESET_FORMAT)
                                .append(NEXT_NAV_END, RESET_FORMAT)
                                .append(BORDER_STRING, RESET_FORMAT)
                                .create()
                )
            } else return false

            return true

        } else if (args[0].equals("suppress", true)) {
            if (args.size < 4) {
                return false
            }

            try {
                val suppress = args[3].toBoolean()
                val tag = requireNotNull(TagRegistry(args[1]).getCoordTag(args[2]))

                tag.suppress(suppress)
                tag.registry.saveToDisk()

                if (suppress) {
                    sender.sendMessage("[CoordTag] Warning for ${args[2]} is now suppressed.")
                } else {
                    sender.sendMessage("[CoordTag] Warning for ${args[2]} is now accepted.")
                }
            } catch (e: Exception) {
                return false
            }
            return true

        } else if (pdata?.isOnline() != true) {
            sender.sendMessage(
                    *ComponentBuilder("[CoordTag] You must be in a game.")
                            .event(HoverEvent(HOVER_TEXT, ComponentBuilder("Click here to join.").color(ChatColor.GOLD).create()))
                            .event(ClickEvent(SUGGEST_CMD, "/join ")).create()
            )
            return true
        }

        when (args[0].toLowerCase()) {
            "list" -> {
                var lastOption: String? = null

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
                if (args.size < 2) {
                    return false
                }

                if (!pdata.isOnline()) {
                    sender.sendMessage(
                            *ComponentBuilder("[CoordTag] You must be in a game.")
                                    .event(HoverEvent(HOVER_TEXT, ComponentBuilder("Click here to join.").color(ChatColor.GOLD).create()))
                                    .event(ClickEvent(SUGGEST_CMD, "/join ")).create()
                    )
                    return true
                }

                val game = pdata.getGame()
                val tag = game.resource.tagRegistry.getCoordTag(args[1])
                val captures = tag?.getCaptures(game.map.id)
                val tagName = tag?.name

                if (tag == null) {
                    sender.sendMessage("[CoordTag] Tag ${args[1]} does not exist.")
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
                            val future = capture.teleport(sender)

                            future.handle { _, t ->
                                if (t != null) {
                                    sender.sendMessage("\u00A7c[CoordTag] ${t.localizedMessage}")
                                } else {
                                    ActionbarTask(sender, "Teleported to $tagName/${capture.index}").start()

                                    if (capture is AreaCapture) {
                                        capture.displayBorder(sender.world, Timer(TimeUnit.SECOND, 20))
                                    }
                                }
                            }
                        }
                        else -> {
                            val capture = captures.firstOrNull {
                                it.index == args[2].toIntOrNull()
                            }

                            if (capture == null) {
                                sender.sendMessage("[CoordTag] Unable to find capture with index ${args[2]}.")
                            } else {
                                val future = capture.teleport(sender)

                                future.handle { _, t ->
                                    if (t != null) {
                                        sender.sendMessage("\u00A7c[CoordTag] ${t.localizedMessage}")
                                    } else {
                                        ActionbarTask(sender, "&9Teleported to $tagName/${capture.index}").start()

                                        if (capture is AreaCapture) {
                                            capture.displayBorder(sender.world, Timer(TimeUnit.SECOND, 20))
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else if (args[0].equals("display", true)) {

                    val timer = Timer(TimeUnit.SECOND, 20)
                    val areaCaptures = LinkedList(
                            captures.filterIsInstance(AreaCapture::class.java)
                    )

                    if (tag.mode != TagMode.AREA) {
                        sender.sendMessage("[CoordTag] Display feature doesn't support ${tag.mode} tags yet.")
                        return true
                    }

                    if (areaCaptures.isEmpty()) {
                        sender.sendMessage("[CoordTag] This tag is empty.")
                        return true
                    }

                    if (args.size > 2) {
                        try {
                            val capture = areaCaptures.firstOrNull { it.index == args[2].toInt() }

                            if (capture != null) {
                                areaCaptures.clear()
                                areaCaptures.add(capture)
                            } else {
                                sender.sendMessage("[CoordTag] Unable to find capture by index: ${args[2]}")
                                return true
                            }
                        } catch (e: NumberFormatException) {
                            return false
                        }
                    }

                    if (areaCaptures.size == 1) {
                        val capture = areaCaptures.first

                        capture.displayBorder(sender.world, timer)
                        ActionbarTask(
                                player = sender,
                                period = timer,
                                text = *arrayOf("&9Displaying area: $tagName/${capture.index}")
                        ).start()
                    } else {
                        areaCaptures.forEach {
                            it.displayBorder(sender.world, timer)
                        }

                        ActionbarTask(
                                player = sender,
                                period = timer,
                                text = *arrayOf("&9Displaying area: $tagName")
                        ).start()
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
                val game = pdata.getGame()
                val registry = game.resource.tagRegistry

                try {
                    mode = TagMode.valueOf(args[2].toUpperCase())
                } catch (e: IllegalArgumentException) {
                    sender.sendMessage("[CoordTag] Illegal tag mode: ${args[2]}")
                    return false
                }

                when {
                    registry.getCoordTag(args[1]) != null -> {
                        sender.sendMessage("[CoordTag] This tag already exist!")
                    }
                    else -> {
                        val tagName = args[1].toLowerCase()

                        registry.createCoordTag(game.map.id, mode, tagName)
                        ActionbarTask(sender, "&6Tag &r$tagName &6has been created.").start()
                    }
                }
            }
            "capture" -> {
                if (args.size < 2)
                    return false

                try {
                    val tag = pdata.getGame().resource.tagRegistry.getCoordTag(args[1])

                    if (tag == null) {
                        sender.sendMessage("[CoordTag] That tag does not exist.")
                    }
                    else when (tag.mode) {
                        TagMode.SPAWN -> {
                            val loc = sender.location
                            SpawnCapture(loc.x, loc.y, loc.z, loc.yaw, loc.pitch, pdata.mapID).add(tag)
                            ActionbarTask(sender, "&6Captured a spawnpoint.").start()
                        }
                        TagMode.BLOCK -> {
                            val dialog = ActionbarTask(
                                    sender, repeat = true, text = *arrayOf("&eClick a block to capture it!")
                            ).start()

                            pdata.requestBlockPrompt(Consumer {
                                BlockCapture(it.x, it.y, it.z, pdata.mapID).add(tag)
                                ActionbarTask(sender, "&6Captured a block.").start()
                                dialog.clear()
                            })
                        }
                        TagMode.AREA -> {
                            val dialog = ActionbarTask(
                                    sender, repeat = true, text = *arrayOf("&eClick a block to capture it!")
                            ).start()

                            pdata.requestAreaPrompt(BiConsumer { b1, b2 ->
                                AreaCapture(b1.x, b2.x, b1.y, b2.y, b1.z, b2.z, pdata.mapID).add(tag)
                                ActionbarTask(sender, "&6Captured an area.").start()
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

                val tag = pdata.getGame().resource.tagRegistry.getCoordTag(args[1])
                val tagName = tag?.name

                if (tag == null) {
                    sender.sendMessage("[CoordTag] ${args[1]} does not exist.")
                } else if (args.size == 2) {
                    tag.remove()
                    ActionbarTask(sender, "&6Tag &r$tagName &6has been removed.").start()
                } else {
                    val capture = tag.getCaptures(pdata.mapID).firstOrNull { it.index == args[2].toIntOrNull() }

                    if (capture != null) {
                        tag.removeCapture(capture)
                        ActionbarTask(sender, "&6Capture &r$tagName&6/&r${capture.index} &6has been removed.").start()
                    } else {
                        sender.sendMessage("[CoordTag] Unknown capture index: ${args[2]}")
                    }
                }
            }
            else -> return false
        }
        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String>? {
        if (args.size == 1)
            return getCompletions(args[0], "help", "create", "capture", "remove", "list", "tp", "display")

        val pdata = PlayerData.get(sender as? Player)

        if (pdata?.isOnline() != true) {
            return emptyList()
        }

        val game = pdata.getGame()

        when (args[0].toLowerCase()) {
            "help" -> return getCompletions(args[1], "1", "2", "3")
            "list" -> {
                when (args.size % 2) { // Interpret -flag values
                    0 -> return getCompletions(args.last(), "-tag", "-mode", "-map", "-reset")
                    1 -> return when (args[args.size - 2].toLowerCase()) {
                        "-mode" -> getCompletions(args.last(), "block", "area", "spawn")
                        "-tag" -> {
                            val mode = modeSel[pdata.getPlayer().uniqueId]
                            val map = mapSel[pdata.getPlayer().uniqueId]
                            getCompletions(
                                    query = args.last(),
                                    options = *game.resource.tagRegistry.getCoordTags()
                                            .filter {
                                                mode == null || mode == it.mode
                                                        && it.getCaptures(map).isNotEmpty()
                                            }
                                            .map { it.name }.toTypedArray())
                        }
                        "-map" -> {
                            game.resource.mapRegistry.getMapNames().toMutableList()
                        }
                        else -> emptyList()
                    }
                }
                return emptyList()
            }
            "display", "tp" -> return when (args.size) {
                2 -> getCompletions(
                        query = args[1],
                        options = *game.resource.tagRegistry.getCoordTags().filter { it.getCaptures(game.map.id).isNotEmpty() }
                                .map { it.name }.toTypedArray())
                3 -> getCompletions(
                        query = args[2],
                        options = *game.resource.tagRegistry.getCoordTag(args[1])
                                ?.getCaptures(game.map.id)
                                ?.map { it.index.toString() }
                                ?.toTypedArray() ?: emptyArray()
                )
                else -> emptyList()
            }
            else -> if (pdata !is GameEditor) {
                return emptyList()
            }
        }

        when (args[0].toLowerCase()) {
            "create" -> if (args.size == 3) {
                return getCompletions(args.last(), "block", "area", "spawn")
            }
            "capture" -> return when (args.size) {
                2 -> getCompletions(args.last(), *game.resource.tagRegistry.getCoordTags().map { it.name }.toTypedArray())
                else -> emptyList()
            }
            "remove" -> return when (args.size) {
                2 -> getCompletions(args.last(), *game.resource.tagRegistry.getCoordTags().map { it.name }.toTypedArray())
                3 -> {
                    val arr = game.resource.tagRegistry.getCoordTag(args[1])
                            ?.getCaptures(pdata.mapID)
                            ?.mapNotNull { it.index?.toString() }
                            ?.toTypedArray()

                    getCompletions(args.last(), *arr ?: emptyArray())
                }
                else -> emptyList()
            }
        }

        return emptyList()
    }

    private fun showCaptureList(playerData: PlayerData) {
        val player = playerData.getPlayer()
        val game = playerData.getGame()
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
            game.resource.tagRegistry.getCoordTags().forEach {
                if (modeSel == null || modeSel == it.mode) {
                    tags.add(it)
                }
            }
        } else {
            // Insert specific tag only. Disregard modeSel
            game.resource.tagRegistry.getCoordTag(tagSel)?.let { tags.add(it) }
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
                val thisMapID = game.map.id

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
        val player = playerData.getPlayer()

        mapSel.remove(player.uniqueId)
        modeSel.remove(player.uniqueId)
        tagSel.remove(player.uniqueId)
        player.sendMessage("[CoordTag] Previous flags were reset.")
    }

    private fun selectMap(playerData: PlayerData, id: String) {
        val player = playerData.getPlayer()
        val game = playerData.getGame()

        if (game.resource.mapRegistry.getMapNames().contains(id)) {
            mapSel[player.uniqueId] = id
            player.sendMessage("[CoordTag] Selected the map: $id")
        } else {
            player.sendMessage("[CoordTag] Map $id is not found in ${game.id}!")
        }
    }

    private fun selectTag(playerData: PlayerData, name: String) {
        val registry = playerData.getGame().resource.tagRegistry
        val player = playerData.getPlayer()

        if (registry.getCoordTag(name) != null) {
            tagSel[player.uniqueId] = name
            player.sendMessage("[CoordTag] Selected the tag: $name")
        } else {
            player.sendMessage("[CoordTag] Tag does not exist! You should create one: /ctag create <name>")
        }
    }

    private fun selectMode(playerData: PlayerData, label: String) {
        val player = playerData.getPlayer()

        try {
            modeSel[player.uniqueId] = TagMode.valueOf(label.toUpperCase())
            player.sendMessage("[CoordTag] Selected the mode: ${label.toUpperCase()}")
        } catch (e: IllegalArgumentException) {
            player.sendMessage("[CoordTag] Illegal flag: -select ${label.toUpperCase()}")
        }
    }
}