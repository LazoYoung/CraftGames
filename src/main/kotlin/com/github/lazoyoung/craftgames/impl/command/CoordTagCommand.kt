package com.github.lazoyoung.craftgames.impl.command

import com.github.lazoyoung.craftgames.api.ActionbarTask
import com.github.lazoyoung.craftgames.api.TimeUnit
import com.github.lazoyoung.craftgames.api.Timer
import com.github.lazoyoung.craftgames.api.tag.coordinate.*
import com.github.lazoyoung.craftgames.impl.command.page.*
import com.github.lazoyoung.craftgames.impl.game.player.GameEditor
import com.github.lazoyoung.craftgames.impl.game.player.PlayerData
import com.github.lazoyoung.craftgames.impl.tag.TagRegistry
import com.github.lazoyoung.craftgames.impl.tag.coordinate.CoordTagFilter
import net.md_5.bungee.api.ChatColor
import net.md_5.bungee.api.chat.*
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
    private val helpPage = Page(
            "CoordTag Command Manual", "/ctag help",
            PageBody {
                val pdata = PlayerData.get(it as Player)
                val list = LinkedList(listOf(
                        PageBody.Element(
                                "\u25cb /ctag create (tag) <mode>",
                                "Create a new tag.",
                                "/ctag create "
                        ),
                        PageBody.Element(
                                "\u25cb /ctag capture (tag)",
                                "Capture current coordinate.",
                                "/ctag capture "
                        ),
                        PageBody.Element(
                                "\u25cb /ctag remove (tag) [index]",
                                "Wipe out the whole tag. You may supply &eindex\n"
                                        + "&rif you need to delete a specific capture only.",
                                "/ctag remove "
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
                                "\u25cb /ctag list",
                                "Show all captures matching the flags.\n" +
                                        "Flags: -mode, -tag, -map",
                                "/ctag list"
                        ),
                        PageBody.Element(
                                "\u25cb /ctag tp (tag) [index]",
                                "Teleport to one of captures defined in the tag.",
                                "/ctag tp "
                        ),
                        PageBody.Element(
                                "\u25cb /ctag display (tag) <index>",
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
            Page.isPrompted(args) -> {
                return helpPage.display(sender, args)
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
            // /ctag list-filter [(filter_flag)...] [(filter_mapping)...]
            "list-filter" -> {
                val filterMap = HashMap<CoordTagFilter, String>()
                val options = LinkedList<CoordTagFilter>()

                for (indexed in args.withIndex()) {
                    val index = indexed.index
                    val arg = if (index == 0) {
                        continue
                    } else {
                        indexed.value
                    }

                    try {
                        options.add(CoordTagFilter.getByFlag(arg))
                    } catch (e: NoSuchElementException) {
                        val mapArgs = args.drop(index - 1).toTypedArray()
                        options.removeLast()
                        filterMap.putAll(extractFilterMappings(mapArgs))
                        break
                    }
                }

                if (options.isEmpty()) {
                    sender.sendMessage("$warn No filter available.")
                    return true
                }

                val bodies = LinkedList<PageBody>()
                val elements = LinkedList<PageBody.Element>()
                var title = "[Filters]"

                fun getButton(prefix: String, text: String, hover: HoverEvent, click: ClickEvent): PageBody.Element {
                    val builder = ComponentBuilder("\n")
                            .append(prefix, RESET_FORMAT)
                            .append(text, RESET_FORMAT)
                            .color(ChatColor.GOLD).underlined(true)
                            .event(hover)
                            .event(click)
                            .append("")
                            .color(ChatColor.RESET).underlined(false)
                            .event(HoverEvent(HOVER_TEXT, ComponentBuilder().create()))
                            .event(ClickEvent(SUGGEST_CMD, ""))
                    return PageBody.Element(builder.create())
                }

                if (options.size == 1) {
                    val hoverText = ComponentBuilder("Apply this filter...")
                            .color(ChatColor.GOLD).create()
                    val hoverEvent = HoverEvent(HOVER_TEXT, hoverText)

                    when (options.first) {
                        CoordTagFilter.MODE -> {
                            val modes = TagMode.values()
                            title = "[Mode Filters]"

                            for (indexed in modes.withIndex()) {
                                val index = indexed.index
                                val mode = indexed.value
                                filterMap[CoordTagFilter.MODE] = mode.label
                                val clickCommand = appendFilterMappings("/ctag list", filterMap)

                                elements.add(getButton("* ", mode.name, hoverEvent, ClickEvent(RUN_CMD, clickCommand)))

                                if (index == modes.lastIndex || index % 5 == 4) {
                                    bodies.add(PageBody(*elements.toTypedArray()))
                                    elements.clear()
                                }
                            }
                            filterMap.remove(CoordTagFilter.MODE)
                        }
                        CoordTagFilter.MAP -> {
                            val maps = game.resource.mapRegistry.getMapNames()
                            title = "[Map Filters]"

                            for (indexed in maps.withIndex()) {
                                val index = indexed.index
                                val map = indexed.value
                                filterMap[CoordTagFilter.MAP] = map
                                val clickCommand = appendFilterMappings("/ctag list", filterMap)

                                elements.add(getButton("* ", map, hoverEvent, ClickEvent(RUN_CMD, clickCommand)))

                                if (index == maps.lastIndex || index % 5 == 4) {
                                    bodies.add(PageBody(*elements.toTypedArray()))
                                    elements.clear()
                                }
                            }
                            filterMap.remove(CoordTagFilter.MAP)
                        }
                        else -> {
                            elements.add(PageBody.Element("&7No filter available."))
                        }
                    }
                } else {
                    val hoverText = ComponentBuilder("Select this category...")
                            .color(ChatColor.GOLD).create()
                    val hoverEvent = HoverEvent(HOVER_TEXT, hoverText)
                    title = "[Filter Categories]"

                    for (indexed in options.withIndex()) {
                        val index = indexed.index
                        val filter = indexed.value
                        val name = filter.name
                        val clickCommand = appendFilterMappings("/ctag list-filter $name", filterMap)

                        elements.add(getButton("* ", name, hoverEvent, ClickEvent(RUN_CMD, clickCommand)))

                        if (index == options.lastIndex || index % 5 == 4) {
                            bodies.add(PageBody(*elements.toTypedArray()))
                            elements.clear()
                        }
                    }
                }

                elements.addFirst(PageBody.Element("&eNote: Click on a filter to apply!"))

                val navCommand = args.joinToString(separator = " ", prefix = "/ctag ")
                val page = Page(title, navCommand, *bodies.toTypedArray())
                page.display(sender, 1)
                return true
            }
            "list" -> {
                var lastOption: String? = null
                val filter = HashMap<CoordTagFilter, String>()
                var pageNum = 1

                for (index in args.indices) {
                    if (index == 0) {
                        continue
                    }

                    if (index % 2 == 1) {
                        lastOption = args[index].toLowerCase()
                    } else when (lastOption) {
                        "-mode" -> filter[CoordTagFilter.MODE] = args[index]
                        "-tag" -> filter[CoordTagFilter.TAG] = args[index]
                        "-map" -> filter[CoordTagFilter.MAP] = args[index]
                        "-page" -> pageNum = args[index].toIntOrNull() ?: pageNum
                    }
                }

                if (filter.containsKey(CoordTagFilter.TAG)) {
                    filter.remove(CoordTagFilter.MODE)
                    browseCaptures(pdata, filter, pageNum)
                } else {
                    filter.remove(CoordTagFilter.MAP)
                    browseTags(pdata, filter, pageNum)
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
                val captures = tag?.getCaptures(game.map.id)?.toMutableList()
                        ?: mutableListOf()
                val tagName = tag?.name

                if (tag == null) {
                    sender.sendMessage("$error Tag ${args[1]} does not exist.")
                    return true
                }
                if (captures.isEmpty()) {
                    sender.sendMessage("$error Tag $tagName has no captures in this map.")
                    return true
                }

                if (args[0].equals("tp", true)) {

                    fun teleport(capture: CoordCapture) {
                        val future = capture.teleport(sender)

                        future.handleAsync { _, t ->
                            if (t != null) {
                                sender.sendMessage("$error ${t.localizedMessage}")
                            } else {
                                Bukkit.getScheduler().runTask(Main.instance, Runnable {
                                    try {
                                        ActionbarTask(sender, "&9Teleported to $tagName/${capture.index}").start()
                                        capture.displayBorder(sender.world, Timer(TimeUnit.SECOND, 10))
                                    } catch (e: IllegalStateException) {
                                        // Do nothing
                                    }
                                })
                            }
                        }
                    }

                    when (args.size) {
                        2 -> {
                            teleport(captures.random())
                        }
                        3 -> {
                            val capture = captures.firstOrNull {
                                it.index == args[2].toIntOrNull()
                            }

                            if (capture == null) {
                                sender.sendMessage("$error Unable to find capture with index ${args[2]}.")
                            } else {
                                teleport(capture)
                            }
                        }
                        else -> return false
                    }
                } else if (args[0].equals("display", true)) {
                    val timer = Timer(TimeUnit.SECOND, 20)

                    if (captures.isEmpty()) {
                        sender.sendMessage("$error This tag is empty.")
                        return true
                    }

                    if (args.size > 2) {
                        try {
                            val capture = captures.firstOrNull { it.index == args[2].toInt() }

                            if (capture != null) {
                                captures.clear()
                                captures.add(capture)
                            } else {
                                sender.sendMessage("$error Unable to find capture by index: ${args[2]}")
                                return true
                            }
                        } catch (e: NumberFormatException) {
                            return false
                        }
                    }

                    try {
                        val text = if (captures.size == 1) {
                            val capture = captures.first()
                            capture.displayBorder(sender.world, timer)

                            "&9Displaying tag: $tagName/${capture.index}"
                        } else {
                            captures.forEach {
                                it.displayBorder(sender.world, timer)
                            }

                            "&9Displaying tag: $tagName"
                        }

                        ActionbarTask(sender, timer, text = *arrayOf(text)).start()
                    } catch (e: IllegalStateException) {
                        sender.sendMessage("$error ${e.message}")
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
                return helpPage.range.map { it.toString() }
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
                    0 -> getCompletions(args.last(), "-tag", "-mode", "-map", "-page")
                    1 -> when (args[args.size - 2].toLowerCase()) {
                        "-mode" -> getCompletions(args.last(), "block", "area", "spawn")
                        "-tag" -> {
                            getCompletions(
                                    args.last(),
                                    game.resource.tagRegistry.getCoordTags().map { it.name }
                            )
                        }
                        "-map" -> {
                            game.resource.mapRegistry.getMapNames().toMutableList()
                        }
                        "-page" -> {
                            listOf("(Page_Number)")
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

    /**
     * Append [filterMappings] at the end of [command].
     *
     * An example where 2 mappings are appended: /ctag list -tag dummy -map lobby
     *
     * Do not append extra argument to this [String]
     * in order to ensure that filters are placed at the end of this command.
     */
    private fun appendFilterMappings(command: String, filterMappings: Map<CoordTagFilter, String>): String {
        val builder = StringBuilder(command)
        val tagSel = filterMappings[CoordTagFilter.TAG]
        val modeSel = filterMappings[CoordTagFilter.MODE]
        val mapSel = filterMappings[CoordTagFilter.MAP]

        if (tagSel != null) {
            builder.append(" -tag $tagSel")
        }
        if (modeSel != null) {
            builder.append(" -mode $modeSel")
        }
        if (mapSel != null) {
            builder.append(" -map $mapSel")
        }

        return builder.toString()
    }

    /**
     * @param args Command arguments
     * @return Extracted filter mappings
     */
    private fun extractFilterMappings(args: Array<String>): Map<CoordTagFilter, String> {
        var lastFilter: CoordTagFilter? = null
        val filterMap = HashMap<CoordTagFilter, String>()

        for (arg in args) {
            try {
                lastFilter = CoordTagFilter.getByFlag(arg)
            } catch (e: NoSuchElementException) {
                if (lastFilter != null) {
                    val filterValue = filterMap[lastFilter] ?: ""
                    filterMap[lastFilter] = filterValue.plus(arg)
                }
            }
        }

        return filterMap
    }

    private fun getTagButton(
            playerData: PlayerData, mapSel: String?,
            tag: CoordTag, prefix: String,
            actionText: String, command: String
    ): Array<BaseComponent> {
        val map = mapSel ?: playerData.getGame().map.id
        val global = tag.getCaptures(null).size
        val local  = tag.getCaptures(map).size
        val actionComponent = ChatColor.translateAlternateColorCodes('&', actionText)
        val counterText = when (global) {
            0 -> {
                val globalText = TextComponent("\u25cf No capture found.\n\n")
                globalText.color = ChatColor.GRAY
                globalText
            }
            else -> {
                val globalText = if (global > 1) {
                    TextComponent("\u25cf $global captures in total\n")
                } else {
                    TextComponent("\u25cf $global capture in total\n")
                }
                val localText = when {
                    local > 1 -> {
                        TextComponent("\u25cf $local captures found in $map\n\n")
                    }
                    local == 1 -> {
                        TextComponent("\u25cf $local capture found in $map\n\n")
                    }
                    else -> {
                        TextComponent("\u25cf No capture found in $map\n\n")
                    }
                }
                globalText.color = ChatColor.GRAY
                localText.color = ChatColor.GRAY
                globalText.addExtra(localText)
                globalText
            }
        }
        val hoverText = ComponentBuilder()
                .append(tag.name)
                .color(ChatColor.WHITE)
                .append("\n")
                .append("\u25cf ${tag.mode.label} mode\n").color(ChatColor.GRAY)
                .append(counterText, RESET_FORMAT)
                .append(actionComponent, RESET_FORMAT)
                .create()
        return ComponentBuilder()
                .append(prefix)
                .append(tag.name).color(ChatColor.AQUA).underlined(true)
                .event(HoverEvent(HOVER_TEXT, hoverText))
                .event(ClickEvent(RUN_CMD, command))
                .create()
    }

    private fun getFilterButton(
            filterMap: Map<CoordTagFilter, String>,
            vararg filterable: CoordTagFilter
    ): Array<BaseComponent> {
        val builder = ComponentBuilder("Filter: ")
        val selection = filterMap.filterKeys { filterable.contains(it) }
        val extraFilters = filterable.filter { !filterMap.containsKey(it) }
        val addHover = HoverEvent(HOVER_TEXT, ComponentBuilder("Click to add filter!").color(ChatColor.YELLOW).create())

        if (selection.isEmpty()) {
            val extraFlags = extraFilters.joinToString(separator = " ", transform = { it.flag })
            val addCommand = appendFilterMappings("/ctag list-filter $extraFlags", filterMap)

            builder.append("NONE").underlined(true)
                    .event(addHover)
                    .event(ClickEvent(RUN_CMD, addCommand))
        } else {
            val hoverText = ComponentBuilder("Click to remove filter.")
                    .color(ChatColor.YELLOW).create()
            val removeHover = HoverEvent(HOVER_TEXT, hoverText)

            for (entry in selection) {
                val thisFilter = entry.key
                val removedFilter = HashMap(filterMap)
                removedFilter.remove(thisFilter)
                val removeCommand = appendFilterMappings("/ctag list", removedFilter)

                builder.append(thisFilter.toString())
                        .color(ChatColor.GOLD).underlined(true)
                        .event(removeHover)
                        .event(ClickEvent(RUN_CMD, removeCommand))
                        .append(" ", RESET_FORMAT)
                        .color(ChatColor.RESET).underlined(false)
                        .event(HoverEvent(HOVER_TEXT, ComponentBuilder().create()))
                        .event(ClickEvent(SUGGEST_CMD, ""))
            }

            val flags = extraFilters.joinToString(separator = " ", transform = { it.flag })

            if (extraFilters.isNotEmpty()) {
                builder.underlined(true).color(ChatColor.GOLD)
                        .append("+")
                        .event(addHover)
                        .event(ClickEvent(RUN_CMD, "/ctag list-filter $flags"))
            }
        }

        return builder.create()
    }

    /**
     * @param filter [CoordTagFilter.MODE] is optional,
     * [CoordTagFilter.MAP] and [CoordTagFilter.TAG] is not applicable.
     */
    private fun browseTags(playerData: PlayerData, filter: MutableMap<CoordTagFilter, String>, pageNum: Int) {
        val player = playerData.getPlayer()
        val registry = playerData.getGame().resource.tagRegistry
        val bodies = LinkedList<PageBody>()
        val elements = LinkedList<PageBody.Element>()
        val modeSel = try {
            TagMode.getByLabel(filter[CoordTagFilter.MODE] ?: "")
        } catch (e: NoSuchElementException) {
            filter.remove(CoordTagFilter.MODE)
            null
        }
        val tags = if (modeSel != null) {
            registry.getCoordTags().filter { it.mode == modeSel }
        } else {
            registry.getCoordTags()
        }

        require(filter[CoordTagFilter.MAP] == null) {
            "Map filter is not applicable!"
        }
        require(filter[CoordTagFilter.TAG] == null) {
            "Tag filter is not applicable!"
        }

        for (indexed in tags.withIndex()) {
            val tag = indexed.value
            val index = indexed.index
            val tagName = tag.name
            val tagButton = this.getTagButton(
                    playerData, null, tag, "\n* ",
                    "&eClick to view captures!",
                    "/ctag list -tag $tagName"
            )

            elements.add(PageBody.Element(tagButton))

            if (index == tags.lastIndex || index % 5 == 4) {
                val filterElement = PageBody.Element(
                        this.getFilterButton(filter, CoordTagFilter.MODE)
                )

                elements.addFirst(filterElement)
                bodies.add(PageBody(*elements.toTypedArray()))
                elements.clear()
            }
        }

        if (bodies.isEmpty()) {
            bodies.add(PageBody(PageBody.Element("&7No result found.")))
        }

        val pageCommand = appendFilterMappings("/ctag list", filter).plus(" -page")
        val page = Page("[CoordTag Browser]", pageCommand, *bodies.toTypedArray())
        page.display(player, pageNum)
    }

    /**
     * @param filter [CoordTagFilter.TAG] is required,
     * [CoordTagFilter.MAP] is optional, [CoordTagFilter.MODE] is not applicable.
     */
    private fun browseCaptures(playerData: PlayerData, filter: Map<CoordTagFilter, String>, pageNum: Int) {
        val player = playerData.getPlayer()
        val game = playerData.getGame()
        val registry = game.resource.tagRegistry
        val bodies = LinkedList<PageBody>()
        val elements = LinkedList<PageBody.Element>()
        val tagSel = requireNotNull(filter[CoordTagFilter.TAG]) {
            "Tag filter is required!"
        }
        require(filter[CoordTagFilter.MODE] == null) {
            "Mode filter is not applicable!"
        }
        val mapSel = filter[CoordTagFilter.MAP]
        val tag = registry.getCoordTag(tagSel)
        val captures = tag?.getCaptures(mapSel) ?: emptyList()

        for (indexed in captures.withIndex()) {
            val capture = indexed.value
            val index = indexed.index
            val ci = capture.index
            val mapID = capture.mapID
            val thisMapID = game.map.id
            val text: String

            when (capture) {
                is SpawnCapture -> {
                    val x = capture.x
                    val y = capture.y
                    val z = capture.z
                    text = "* Spawn $ci at $mapID ($x, $y, $z)"
                }
                is BlockCapture -> {
                    val x = capture.x
                    val y = capture.y
                    val z = capture.z
                    text = "* Block $ci at $mapID ($x, $y, $z)"
                }
                is AreaCapture -> {
                    val x1 = capture.x1
                    val x2 = capture.x2
                    val y1 = capture.y1
                    val y2 = capture.y2
                    val z1 = capture.z1
                    val z2 = capture.z2
                    text = "* Area $ci at $mapID ($x1~$x2, $y1~$y2, $z1~$z2)"
                }
                else -> error("Unknown TagMode.")
            }

            val element = if (mapID == thisMapID) {
                PageBody.Element(
                        "\n$text",
                        "&6Click here to teleport.",
                        "/ctag tp $tagSel $ci",
                        suggest = false
                )
            } else {
                PageBody.Element (
                        "\n\u00A77$text",
                        "&eThis is outside this map.",
                        null
                )
            }

            elements.add(element)

            if (index == captures.lastIndex || index % 5 == 4) {
                val tagButton = this.getTagButton(
                        playerData, mapID, checkNotNull(tag), "Tag: ",
                        "&eClick to browse all tags!",
                        "/ctag list"
                )
                val tagElement = PageBody.Element(tagButton)
                val filterElement = PageBody.Element(
                        this.getFilterButton(filter, CoordTagFilter.MAP)
                )

                elements.addFirst(filterElement) // second
                elements.addFirst(tagElement) // first
                bodies.add(PageBody(*elements.toTypedArray()))
                elements.clear()
            }
        }

        if (bodies.isEmpty()) {
            val text = if (tag == null) {
                "&eUnknown tag: $tagSel"
            } else {
                "&7No result found."
            }
            bodies.add(PageBody(PageBody.Element(text)))
        }

        val pageCommand = appendFilterMappings("/ctag list", filter).plus(" -page")
        val page = Page("[CoordCapture Browser]", pageCommand, *bodies.toTypedArray())
        page.display(player, pageNum)
    }

    @Deprecated("Filter is no longer cached.")
    private fun reset(playerData: PlayerData) {
        val player = playerData.getPlayer()

        mapSel.remove(player.uniqueId)
        modeSel.remove(player.uniqueId)
        tagSel.remove(player.uniqueId)
        player.sendMessage("$info Previous flags were reset.")
    }

    @Deprecated("Filter is no longer cached.")
    private fun selectMap(playerData: PlayerData, id: String) {
        val player = playerData.getPlayer()
        val game = playerData.getGame()

        if (game.resource.mapRegistry.getMapNames().contains(id)) {
            mapSel[player.uniqueId] = id
            player.sendMessage("$info Selected the map: $id")
        } else {
            player.sendMessage("$error Map $id is not defined in ${game.name}!")
        }
    }

    @Deprecated("Filter is no longer cached.")
    private fun selectTag(playerData: PlayerData, name: String) {
        val registry = playerData.getGame().resource.tagRegistry
        val player = playerData.getPlayer()

        if (registry.getCoordTag(name) != null) {
            tagSel[player.uniqueId] = name
            player.sendMessage("$info Selected the tag: $name")
        } else {
            player.sendMessage("$error Tag does not exist! You should create one: /ctag create <name>")
        }
    }

    @Deprecated("Filter is no longer cached.")
    private fun selectMode(playerData: PlayerData, label: String) {
        val player = playerData.getPlayer()

        try {
            modeSel[player.uniqueId] = TagMode.valueOf(label.toUpperCase())
            player.sendMessage("$info Selected the mode: ${label.toUpperCase()}")
        } catch (e: IllegalArgumentException) {
            player.sendMessage("$error Illegal flag: -select ${label.toUpperCase()}")
        }
    }
}