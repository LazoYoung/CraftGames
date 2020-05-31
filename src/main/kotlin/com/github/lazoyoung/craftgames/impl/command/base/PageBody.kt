package com.github.lazoyoung.craftgames.impl.command.base

import net.md_5.bungee.api.ChatColor
import net.md_5.bungee.api.chat.BaseComponent
import net.md_5.bungee.api.chat.ClickEvent
import net.md_5.bungee.api.chat.ComponentBuilder
import net.md_5.bungee.api.chat.HoverEvent
import org.bukkit.command.CommandSender
import java.util.*

data class PageBody constructor(val lazyElements: (CommandSender) -> LinkedList<Element>) {

    constructor(vararg elements: Element) : this({ LinkedList(elements.toList()) })

    data class Element(
            val element: String,
            val hoverText: String? = null,
            val command: String? = null
    )

    fun getBodyText(sender: CommandSender, lineBreak: Boolean = false): Array<BaseComponent> {
        val builder = ComponentBuilder()

        for (e in lazyElements(sender)) {
            val element = ChatColor.translateAlternateColorCodes('&', e.element)
            val eBuilder = ComponentBuilder().color(ChatColor.RESET).append(element)
            val trHoverText = ChatColor.translateAlternateColorCodes('&', e.hoverText)

            e.hoverText?.let { eBuilder.event(HoverEvent(HOVER_TEXT, ComponentBuilder(trHoverText).create())) }
            e.command?.let { eBuilder.event(ClickEvent(SUGGEST_CMD, e.command)) }

            val components = if (lineBreak) {
                eBuilder.append("\n").create()
            } else {
                eBuilder.create()
            }
            builder.append(components, RESET_FORMAT)
        }
        return builder.create()
    }

}