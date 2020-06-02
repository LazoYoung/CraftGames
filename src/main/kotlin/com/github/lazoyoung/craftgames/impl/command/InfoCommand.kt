package com.github.lazoyoung.craftgames.impl.command

import com.github.lazoyoung.craftgames.impl.command.page.*
import net.md_5.bungee.api.ChatColor
import net.md_5.bungee.api.chat.ClickEvent
import net.md_5.bungee.api.chat.ComponentBuilder
import net.md_5.bungee.api.chat.HoverEvent
import org.bukkit.command.Command
import org.bukkit.command.CommandSender

class InfoCommand : CommandBase("CraftGames") {

    private val helpPage = Page(
            "CraftGames", "/cg help",
            PageBody(
                    PageBody.Element(
                            "\u25cb /join (game)",
                            "Join a game.",
                            "/join"
                    ),
                    PageBody.Element(
                            "\u25cb /leave",
                            "Leave current game.",
                            "/leave"
                    ),
                    PageBody.Element(
                            "\u25cb /mapvote (map)",
                            "Vote for a map.",
                            "/mapvote "
                    ),
                    PageBody.Element(
                            "\u25cb /kit (name)",
                            "Select a kit.",
                            "/kit "
                    )
            ),
            PageBody(
                    PageBody.Element(
                            "\u25cb /game",
                            "Manage/edit games.",
                            "/game help"
                    ),
                    PageBody.Element(
                            "\u25cb /ctag",
                            "Manage/edit coordinate tags.",
                            "/ctag help"
                    ),
                    PageBody.Element(
                            "\u25cb /itag",
                            "Manage/edit item tags.",
                            "/itag help"
                    )
            )
    )

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        when {
            args.isEmpty() -> {
                sender.sendMessage(
                        *ComponentBuilder()
                                .append(BORDER_STRING, RESET_FORMAT)
                                .append("\nCraftGames").color(ChatColor.GOLD)
                                .append(" - the core of minigames with ").color(ChatColor.RESET)
                                .append("infinite").bold(true)
                                .append(" scalability.", RESET_FORMAT)
                                .append("\nIt's developed by ")
                                .append("LazoYoung").color(ChatColor.AQUA).bold(true)
                                .append(", licensed under ", RESET_FORMAT)
                                .append("MIT License").color(ChatColor.RED)
                                .append(".", RESET_FORMAT)
                                .append("\nVisit Github to ")
                                .append("read wiki")
                                    .underlined(true)
                                    .event(HoverEvent(HOVER_TEXT, ComponentBuilder("Click to open website.").create()))
                                    .event(ClickEvent(OPEN_URL, "https://github.com/LazoYoung/CraftGames/wiki"))
                                .append(" or ", RESET_FORMAT)
                                .append("report issues.")
                                    .underlined(true)
                                    .event(HoverEvent(HOVER_TEXT, ComponentBuilder("Click to open website.").create()))
                                    .event(ClickEvent(OPEN_URL, "https://github.com/LazoYoung/CraftGames/issues"))
                                .append("\n\nType ", RESET_FORMAT)
                                .append("/craftgames help")
                                    .color(ChatColor.YELLOW)
                                    .underlined(true)
                                    .event(HoverEvent(HOVER_TEXT, ComponentBuilder("Click to auto-type.").create()))
                                    .event(ClickEvent(SUGGEST_CMD, "/craftgames help"))
                                .append(" to open command manual.", RESET_FORMAT)
                                .append(BORDER_STRING, RESET_FORMAT)
                                .create())
            }
            Page.isPrompted(args) -> {
                return helpPage.display(sender, args)
            }
            else -> {
                return false
            }
        }

        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<String>): List<String> {
        return when {
            args.isEmpty() -> {
                listOf("help")
            }
            args[0] == "help" && args.size == 2 -> {
                helpPage.range.map { it.toString() }
            }
            else -> {
                emptyList()
            }
        }
    }

}