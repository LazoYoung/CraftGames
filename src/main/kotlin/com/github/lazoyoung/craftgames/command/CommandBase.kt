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
val BORDER_STRING: Array<BaseComponent> = ComponentBuilder()
        .append("--------------------------------------------------")
        .create()
val PREV_NAV: Array<BaseComponent> = ComponentBuilder()
        .append("\n< PREV ").bold(true).color(ChatColor.GOLD)
        .event(HoverEvent(HOVER_TEXT, ComponentBuilder("Click here to navigate.").create()))
        .create()
val PREV_NAV_END: Array<BaseComponent> = ComponentBuilder()
        .append("\n< PREV ").bold(true).color(ChatColor.DARK_GRAY)
        .create()
val PAGE_NAV: Array<BaseComponent> = ComponentBuilder()
        .append("- ").color(ChatColor.GRAY)
        .append("PAGE NAVIGATION")
        .append(" -")
        .create()
val NEXT_NAV: Array<BaseComponent> = ComponentBuilder()
        .append(" NEXT >\n").bold(true).color(ChatColor.GOLD)
        .event(HoverEvent(HOVER_TEXT, ComponentBuilder("Click here to navigate.").create()))
        .create()
val NEXT_NAV_END: Array<BaseComponent> = ComponentBuilder()
        .append(" NEXT >\n").bold(true).color(ChatColor.DARK_GRAY)
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