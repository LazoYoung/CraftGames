package com.github.lazoyoung.craftgames.command

import net.md_5.bungee.api.ChatColor
import net.md_5.bungee.api.chat.BaseComponent
import net.md_5.bungee.api.chat.ClickEvent
import net.md_5.bungee.api.chat.ComponentBuilder
import net.md_5.bungee.api.chat.HoverEvent

data class PageElement(
        val element: String,
        val hoverText: String? = null,
        val command: String? = null
) {
    companion object {
        fun getPageComponents(vararg elements: PageElement): Array<BaseComponent> {
            val builder = ComponentBuilder()

            for (e in elements) {
                builder.append(e.compile(), RESET_FORMAT)
            }
            return builder.create()
        }
    }

    private fun compile(): Array<BaseComponent> {
        val builder = ComponentBuilder().color(ChatColor.RESET).append(element)

        hoverText?.let { builder.event(HoverEvent(HOVER_TEXT, ComponentBuilder(hoverText).create())) }
        command?.let { builder.event(ClickEvent(SUGGEST_CMD, command)) }
        return builder.append("\n").create()
    }
}