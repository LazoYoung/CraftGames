package com.github.lazoyoung.craftgames.impl.command.page

import net.md_5.bungee.api.ChatColor
import net.md_5.bungee.api.chat.BaseComponent
import net.md_5.bungee.api.chat.ClickEvent
import net.md_5.bungee.api.chat.ComponentBuilder
import net.md_5.bungee.api.chat.HoverEvent
import org.bukkit.command.CommandSender
import java.util.*

class PageBody constructor(
        val lazyElements: (CommandSender) -> LinkedList<Element>
) {
    constructor(vararg elements: Element) : this({ LinkedList(elements.toList()) })

    fun getBodyText(sender: CommandSender, lineBreak: Boolean = false): Array<BaseComponent> {
        val builder = ComponentBuilder()

        for (element in lazyElements(sender)) {
            builder.append(element.text, RESET_FORMAT)
                    .append("")
                    .color(ChatColor.RESET)
                    .event(ClickEvent(SUGGEST_CMD, ""))
                    .event(HoverEvent(HOVER_TEXT, ComponentBuilder("").create()))
                    .underlined(false)
                    .bold(false)
                    .italic(false)
                    .obfuscated(false)
                    .strikethrough(false)

            if (lineBreak) {
                builder.append("\n")
            }
        }
        return builder.create()
    }

    class Element {
        val text: Array<BaseComponent>

        constructor(text: Array<BaseComponent>) {
            this.text = text
        }

        constructor(text: String, hoverText: String? = null, command: String? = null, suggest: Boolean = true) {
            val trText = ChatColor.translateAlternateColorCodes('&', text)
            val builder = ComponentBuilder(trText)

            if (hoverText != null) {
                val trHoverText = ChatColor.translateAlternateColorCodes('&', hoverText)
                builder.event(HoverEvent(HOVER_TEXT, ComponentBuilder(trHoverText).create()))
            }
            if (command != null) {
                if (suggest) {
                    builder.event(ClickEvent(SUGGEST_CMD, command))
                } else {
                    builder.event(ClickEvent(RUN_CMD, command))
                }
            }

            this.text = builder.create()
        }
    }
}