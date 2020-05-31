package com.github.lazoyoung.craftgames.impl.command

import com.github.lazoyoung.craftgames.api.ActionbarTask
import com.github.lazoyoung.craftgames.api.TimeUnit
import com.github.lazoyoung.craftgames.api.Timer
import com.github.lazoyoung.craftgames.api.tag.coordinate.*
import com.github.lazoyoung.craftgames.impl.command.base.*
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

class CoordTagCommand : CommandBase("CoordTag") {
    private val modeSel = HashMap<UUID, TagMode>()
    private val mapSel = HashMap<UUID, String>()
    private val tagSel = HashMap<UUID, String>()
    private val help = CommandHelp(
            "CoordTag", "/ctag",
            PageBody {
                val pdata = PlayerData.get(it as Player)
                val list = LinkedList(listOf(
                        PageBody.Element(
                                "◎ /ctag create (tag) <mode>",
                                "Create a new tag.",
                                "/ctag create "
                        ),
                        PageBody.Element(
                                "◎ /ctag capture (tag)",
                                "Capture current coordinate.",
                                "/ctag capture "
                        ),
                        PageBody.Element(
                                "◎ /ctag remove (tag) [index]",
                                "Wipe out the whole tag. You may supply &eindex\n"
                                        + "&rif you need to delete a specific capture only.",
                                "/ctag remove "
                        ),
                        PageBody.Element(
                                "◎ /ctag tp (tag) [index]",
                                "Teleport to one of captures defined in the tag.",
                                "/ctag tp "
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
            },
            PageBody {
                val list = LinkedList(listOf(
                        PageBody.Element(
                                "◎ /ctag list",
                                "Show all captures matching the flags.\n" +
                                        "Flags: -mode, -tag, -map",
                                "/ctag list"
                        ),
                        PageBody.Element(
                                "◎ /ctag list -reset",
                                "Reset all the flags.",
                                "/ctag list -reset"
                        )
                ))

                if (PlayerData.get(it as Player) == null) {
                    list.addFirst(
                            PageBody.Element(
                                    "&eNote: You must be in a game.",
                                    "&6Click here to play game.",
                                    "/join "
                            )
                    )
                }

                list
            },
            PageBody {
                val list = LinkedList(listOf(
                        PageBody.Element(
                                "◎ /ctag display (tag) <index>",
                                "Display a particular capture.",
                                "/ctag display "
                        )
                ))

                if (PlayerData.get(it as Player) == null) {
                    list.addFirst(
                            PageBody.Element(
                                    "&eNote: You must be in a game.",
                                    "&6Click here to play game.",
                                    "/join "
                            )
                    )
                }

                list
            }
    )

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("$error This cannot be run from console.")
            return true
        }

        when {
            CommandHelp.isPrompted(args) -> {
                return help.display(sender, args)
            }
            args[0].equals("suppress", true) -> {
                if (args.size < 4) {
                    return false
                }

                try {
                    val suppress = args[3].toBoolean()
                    val tag = requireNotNull(TagRegistry(args[1]).getCoordTag(args[2]))

                    tag.suppress(suppress)
                    tag.registry.saveToDisk()

                    if (suppress) {
                        sender.sendMessage("$info Warning for ${args[2]} is now suppressed.")
                    } else {
                        sender.sendMessage("$info Warning for ${args[2]} is now accepted.")
                    }
                } catch (e: Exception) {
                    return false
                }
                return true

            }
        }

        val pdata = PlayerData.get(sender)
        val game = if (pdata?.isOnline() == true) {
            pdata.getGame()
        } else {
            val text = PageBody(
                    PageBody.Element(
                            "$error You must be in a game.",
                            "&6Click here to play game.",
                            "/join "
                    )
            ).getBodyText(sender)
            sender.sendMessage(*text)
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
                    val text = PageBody(
                            PageBody.Element(
                                    "$error You must be in a game.",
                                    "&6Click here to play game.",
                                    "/join "
                            )
                    ).getBodyText(sender)
                    sender.sendMessage(*text)
                    return true
                }

                val tag = game.resource.tagRegistry.getCoordTag(args[1])
                val captures = tag?.getCaptures(game.map.id)
                val tagName = tag?.name

                if (tag == null) {
                    sender.sendMessage("$error Tag ${args[1]} does not exist.")
                    return true
                }
                if (captures.isNullOrEmpty()) {
                    sender.sendMessage("$error Tag $tagName has no captures in this map.")
                    return true
                }

                if (args[0].equals("tp", true)) {
                    when (args.size) {
                        2 -> {
                            val capture = captures.random()
                            val future = capture.teleport(sender)

                            future.handle { _, t ->
                                if (t != null) {
                                    sender.sendMessage("$error ${t.localizedMessage}")
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
                                sender.sendMessage("$error Unable to find capture with index ${args[2]}.")
                            } else {
                                val future = capture.teleport(sender)

                                future.handle { _, t ->
                                    if (t != null) {
                                        sender.sendMessage("$error ${t.localizedMessage}")
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
                        sender.sendMessage("$error Display feature doesn't support ${tag.mode} tags yet.")
                        return true
                    }

                    if (areaCaptures.isEmpty()) {
                        sender.sendMessage("$error This tag is empty.")
                        return true
                    }

                    if (args.size > 2) {
                        try {
                            val capture = areaCaptures.firstOrNull { it.index == args[2].toInt() }

                            if (capture != null) {
                                areaCaptures.clear()
                                areaCaptures.add(capture)
                            } else {
                                sender.sendMessage("$error Unable to find capture by index: ${args[2]}")
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
            val text = PageBody(
                    PageBody.Element(
                            "$error You must be in editor mode.",
                            "&6Click here to start editing.",
                            "/game edit "
                    )
            ).getBodyText(sender)
            sender.sendMessage(*text)
            return true
        }

        when (args[0].toLowerCase()) {
            "create" -> {
                if (args.size < 3)
                    return false

                val mode: TagMode
                val registry = game.resource.tagRegistry

                try {
                    mode = TagMode.valueOf(args[2].toUpperCase())
                } catch (e: IllegalArgumentException) {
                    sender.sendMessage("$error Illegal tag mode: ${args[2]}")
                    return false
                }

                when {
                    registry.getCoordTag(args[1]) != null -> {
                        sender.sendMessage("$error This tag already exist!")
                    }
                    else -> {
                        val tagName = args[1].toLowerCase()

                        try {
                            registry.createCoordTag(game.map.id, mode, tagName)
                            ActionbarTask(sender, "&6Tag &r$tagName &6has been created.").start()
                        } catch (e: IllegalArgumentException) {
                            sender.sendMessage("$error ${e.message}")
                        }
                    }
                }
            }
            "capture" -> {
                if (args.size < 2)
                    return false

                try {
                    val tag = pdata.getGame().resource.tagRegistry.getCoordTag(args[1])

                    if (tag == null) {
                        sender.sendMessage("$error That tag does not exist.")
                    } else when (tag.mode) {
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
                    sender.sendMessage("$error Unexpected error! See console for details.")
                    e.printStackTrace()
                }
            }
            "remove" -> {
                if (args.size < 2)
                    return false

                val tag = pdata.getGame().resource.tagRegistry.getCoordTag(args[1])
                val tagName = tag?.name

                if (tag == null) {
                    sender.sendMessage("$error ${args[1]} does not exist.")
                } else if (args.size == 2) {
                    tag.remove()
                    ActionbarTask(sender, "&6Tag &r$tagName &6has been removed.").start()
                } else {
                    val capture = tag.getCaptures(pdata.mapID).firstOrNull { it.index == args[2].toIntOrNull() }

                    if (capture != null) {
                        tag.removeCapture(capture)
                        ActionbarTask(sender, "&6Capture &r$tagName&6/&r${capture.index} &6has been removed.").start()
                    } else {
                        sender.sendMessage("$error Unknown capture index: ${args[2]}")
                    }
                }
            }
            else -> return false
        }
        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<String>): List<String>? {
        when {
            args.size == 1 ->
                return getCompletions(args[0], "help", "create", "capture", "remove", "list", "tp", "display")
            args[0] == "help" && args.size == 2 -> {
                return help.pageRange.map { it.toString() }
            }
        }

        val pdata = PlayerData.get(sender as? Player) ?: return emptyList()
        val game = if (pdata.isOnline()) {
            pdata.getGame()
        } else {
            return emptyList()
        }

        when (args[0].toLowerCase()) {
            "list" -> {
                return when (args.size % 2) { // Interpret -flag values
                    0 -> getCompletions(args.last(), "-tag", "-mode", "-map", "-reset")
                    1 -> when (args[args.size - 2].toLowerCase()) {
                        "-mode" -> getCompletions(args.last(), "block", "area", "spawn")
                        "-tag" -> {
                            val mode = modeSel[pdata.getPlayer().uniqueId]
                            val map = mapSel[pdata.getPlayer().uniqueId]
                            getCompletions(
                                    query = args.last(),
                                    options = game.resource.tagRegistry.getCoordTags()
                                            .filter {
                                                mode == null || mode == it.mode
                                                        && it.getCaptures(map).isNotEmpty()
                                            }
                                            .map { it.name }
                            )
                        }
                        "-map" -> {
                            game.resource.mapRegistry.getMapNames().toMutableList()
                        }
                        else -> emptyList()
                    }
                    else -> emptyList()
                }
            }
            "display", "tp" -> return when (args.size) {
                2 -> getCompletions(
                        query = args[1],
                        options = game.resource.tagRegistry.getCoordTags()
                                .filter { it.getCaptures(game.map.id).isNotEmpty() }
                                .map { it.name })
                3 -> getCompletions(
                        query = args[2],
                        options = game.resource.tagRegistry.getCoordTag(args[1])
                                ?.getCaptures(game.map.id)
                                ?.map { it.index.toString() }
                                ?: emptyList()
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
                2 -> getCompletions(args.last(), game.resource.tagRegistry.getCoordTags().map { it.name })
                else -> emptyList()
            }
            "remove" -> return when (args.size) {
                2 -> getCompletions(args.last(), game.resource.tagRegistry.getCoordTags().map { it.name })
                3 -> {
                    val items = game.resource.tagRegistry.getCoordTag(args[1])
                            ?.getCaptures(pdata.mapID)
                            ?.mapNotNull { it.index?.toString() }

                    getCompletions(args.last(), items ?: emptyList())
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
                    val hoverText = ComponentBuilder()
                            .color(ChatColor.GOLD)
                            .append("Click here to teleport.")
                            .create()
                    text.hoverEvent = HoverEvent(HOVER_TEXT, hoverText)
                    text.clickEvent = ClickEvent(RUN_CMD, "/ctag tp $name $i")
                } else {
                    val hoverText = ComponentBuilder()
                            .color(ChatColor.YELLOW)
                            .append("This is outside this map.")
                            .create()
                    text.hoverEvent = HoverEvent(HOVER_TEXT, hoverText)
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