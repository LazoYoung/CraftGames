package com.github.lazoyoung.craftgames.command

import com.github.lazoyoung.craftgames.Main
import net.md_5.bungee.api.ChatColor
import net.md_5.bungee.api.chat.BaseComponent
import net.md_5.bungee.api.chat.ClickEvent
import net.md_5.bungee.api.chat.ComponentBuilder
import net.md_5.bungee.api.chat.HoverEvent
import org.bukkit.command.TabExecutor

val RESET_FORMAT = ComponentBuilder.FormatRetention.NONE
val OPEN_URL = ClickEvent.Action.OPEN_URL
val SUGGEST_CMD = ClickEvent.Action.SUGGEST_COMMAND
val HOVER_TEXT = HoverEvent.Action.SHOW_TEXT
val RUN_CMD = ClickEvent.Action.RUN_COMMAND
const val BORDER_STRING = "----------------------------------------"
val PREV_NAV: Array<BaseComponent> = ComponentBuilder("\n< PREV ")
        .bold(true).color(ChatColor.GOLD)
        .event(HoverEvent(HOVER_TEXT, ComponentBuilder("Click here to navigate.").create()))
        .create()
val PREV_NAV_END: Array<BaseComponent> = ComponentBuilder("\n< PREV ")
        .bold(true).color(ChatColor.GRAY)
        .create()
val NEXT_NAV: Array<BaseComponent> = ComponentBuilder(" NEXT >\n")
        .bold(true).color(ChatColor.GOLD)
        .event(HoverEvent(HOVER_TEXT, ComponentBuilder("Click here to navigate.").create()))
        .create()
val NEXT_NAV_END: Array<BaseComponent> = ComponentBuilder(" NEXT >\n")
        .bold(true).color(ChatColor.GRAY)
        .create()

interface CommandBase : TabExecutor {

    fun getCompletions(query: String, vararg args: String) : MutableList<String> {
        return args
                .filter { it.isEmpty() || it.startsWith(query.toLowerCase()) }
                .toMutableList()
    }

    fun getGameTitles(query: String) : MutableList<String> {
        return getCompletions(
                query = query,
                args = *Main.getConfig()?.getConfigurationSection("games")
                        ?.getKeys(false)
                        ?.toTypedArray()
                        ?: emptyArray()
        )
    }

}