package com.github.lazoyoung.craftgames.impl.command.base

import net.md_5.bungee.api.ChatColor
import net.md_5.bungee.api.chat.BaseComponent
import net.md_5.bungee.api.chat.ClickEvent
import net.md_5.bungee.api.chat.ComponentBuilder
import org.bukkit.command.CommandSender

class CommandHelp(
        val name: String,
        val command: String,
        private vararg val pageBodies: PageBody
) {

    val pageRange: IntRange = 1..pageBodies.size

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

    private fun getPageText(sender: CommandSender, page: Int): Array<BaseComponent> {
        return if (page in pageRange) {
            val index = page - 1
            val builder = ComponentBuilder()
                    .append(BORDER_STRING, RESET_FORMAT)
                    .append("\n$name Command Manual (Page $page/${pageRange.last})\n", RESET_FORMAT)
                    .append(pageBodies[index].getBodyText(sender))

            if (page == 1) {
                builder.append(PREV_NAV_END, RESET_FORMAT)
                builder.append(PAGE_NAV, RESET_FORMAT)
            } else {
                builder.append(PREV_NAV, RESET_FORMAT)
                        .event(ClickEvent(RUN_CMD, "$command help ${page - 1}"))
                builder.append(PAGE_NAV, RESET_FORMAT)
            }

            if (page == pageRange.last) {
                builder.append(NEXT_NAV_END, RESET_FORMAT)
                builder.append(BORDER_STRING, RESET_FORMAT)
            } else {
                builder.append(NEXT_NAV, RESET_FORMAT)
                        .event(ClickEvent(RUN_CMD, "$command help ${page + 1}"))
                builder.append(BORDER_STRING, RESET_FORMAT)
            }

            builder.create()
        } else {
            ComponentBuilder()
                    .color(ChatColor.RED)
                    .append("Undefined page: $page")
                    .create()
        }
    }

}