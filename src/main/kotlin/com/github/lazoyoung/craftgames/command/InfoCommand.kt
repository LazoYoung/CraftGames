package com.github.lazoyoung.craftgames.command

import net.md_5.bungee.api.ChatColor
import net.md_5.bungee.api.chat.ClickEvent
import net.md_5.bungee.api.chat.ComponentBuilder
import net.md_5.bungee.api.chat.HoverEvent
import org.bukkit.command.Command
import org.bukkit.command.CommandSender

class InfoCommand : CommandBase {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        val components = PageElement.getPageComponents(
                PageElement("◎ /join (game)", "Join a game.", "/join"),
                PageElement("◎ /leave", "Leave current game.", "/leave"),
                PageElement("◎ /mapvote (map)", "Vote for a map.", "/mapvote "),
                PageElement("◎ /kit (name)", "Select a kit.", "/kit "),
                PageElement("◎ /game", "Manage games.", "/game help"),
                PageElement("◎ /ctag", "Manage coordinate tags.", "/ctag help"))

        when {
            args.isEmpty() -> {
                sender.sendMessage(
                        *ComponentBuilder(BORDER_STRING)
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
                                .append(BORDER_STRING)
                                .create())
            }
            args[0].equals("help", true) -> {
                sender.sendMessage(
                        *ComponentBuilder(BORDER_STRING)
                                .append("\nCraftGames Command Manual (Page 1/1)\n", RESET_FORMAT)
                                .append(components)
                                .append(PREV_NAV_END, RESET_FORMAT)
                                .append("- PAGE NAVIGATION -")
                                .append(NEXT_NAV_END, RESET_FORMAT)
                                .append(BORDER_STRING)
                                .create())
            }
            else -> {
                return false
            }
        }

        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): MutableList<String> {
        if (args.isEmpty())
            return command.aliases

        return mutableListOf()
    }

}