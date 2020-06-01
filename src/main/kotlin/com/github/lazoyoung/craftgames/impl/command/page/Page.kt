package com.github.lazoyoung.craftgames.impl.command.page

import net.md_5.bungee.api.ChatColor
import net.md_5.bungee.api.chat.BaseComponent
import net.md_5.bungee.api.chat.ClickEvent
import net.md_5.bungee.api.chat.ComponentBuilder
import org.bukkit.command.CommandSender

class Page(
        private val title: String,
        val command: String,
        private vararg val bodies: PageBody
) {

    val range: IntRange = 1..bodies.size

    companion object {
        fun isPrompted(args: Array<String>): Boolean {
            return args.isEmpty() || args[0].equals("help", true)
        }
    }

    fun display(sender: CommandSender, args: Array<String>): Boolean {
        val index = if (args.size < 2) {
            1
        } else {
            args[1].toIntOrNull() ?: return false
        }

        sender.sendMessage(*getPageText(sender, index))
        return true
    }

    private fun getPageText(sender: CommandSender, pageNum: Int): Array<BaseComponent> {
        return if (pageNum in range) {
            val builder = ComponentBuilder()
                    .append(BORDER_STRING, RESET_FORMAT)
                    .append("\n$title (Page $pageNum/${range.last})\n", RESET_FORMAT)
                    .append(bodies[pageNum - 1].getBodyText(sender, true))
            val pageInfo = ComponentBuilder()
                    .color(ChatColor.GRAY)
                    .append("--- $pageNum/${range.last} ---", RESET_FORMAT)
                    .create()

            if (pageNum == 1) {
                builder.append(PREV_NAV_END, RESET_FORMAT)
                builder.append(pageInfo, RESET_FORMAT)
            } else {
                builder.append(PREV_NAV, RESET_FORMAT)
                        .event(ClickEvent(RUN_CMD, "$command ${pageNum - 1}"))
                builder.append(pageInfo, RESET_FORMAT)
            }

            if (pageNum == range.last) {
                builder.append(NEXT_NAV_END, RESET_FORMAT)
                builder.append(BORDER_STRING, RESET_FORMAT)
            } else {
                builder.append(NEXT_NAV, RESET_FORMAT)
                        .event(ClickEvent(RUN_CMD, "$command ${pageNum + 1}"))
                builder.append(BORDER_STRING, RESET_FORMAT)
            }

            builder.create()
        } else {
            ComponentBuilder()
                    .color(ChatColor.RED)
                    .append("Undefined page: $pageNum")
                    .create()
        }
    }

}