package com.github.lazoyoung.craftgames.command

import com.github.lazoyoung.craftgames.Main
import net.md_5.bungee.api.ChatColor
import net.md_5.bungee.api.chat.BaseComponent
import net.md_5.bungee.api.chat.ClickEvent
import net.md_5.bungee.api.chat.ComponentBuilder
import net.md_5.bungee.api.chat.HoverEvent
import org.bukkit.command.TabExecutor
import java.util.*
import kotlin.collections.ArrayList

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

    fun getCompletions(query: String, vararg options: String) : List<String> {
        return options
                .filter { it.isEmpty() || it.startsWith(query, true) }
                .toMutableList()
    }

    fun getGameTitles(query: String) : List<String> {
        return getCompletions(
                query = query,
                options = *Main.getConfig()?.getConfigurationSection("games")
                        ?.getKeys(false)
                        ?.toTypedArray()
                        ?: emptyArray()
        )
    }

    /**
     * Join string literal from command arguments.
     * This allows users to input string with spacing characters.
     *
     * For instance, let's suppose that a user dispatched a command like this:
     * /test mood:"I love pizza." '39 music' apple pie
     *
     * These arguments will be translated into a [String] array:
     * "mood:I love pizza.", "39 music", "apple", "pie".
     *
     * Note that quotation marks will be trimmed in result.
     *
     * @throws IllegalArgumentException is thrown syntax error is found.
     */
    fun joinStringFromArguments(args: Array<String>): Array<String> {
        val joinedList = ArrayList<String>()
        var openQuote: String? = null

        loop@ for (i in args.indices) {
            val argument = args[i]
            val quotes = TreeMap<Int, Char>()
            var lastIndex = -1

            while (true) {
                lastIndex = argument.indexOfAny(charArrayOf('\'', '\"'), ++lastIndex)

                if (lastIndex !in 0..argument.lastIndex) {
                    break
                }

                quotes[lastIndex] = argument[lastIndex]
            }

            if (openQuote != null) {
                when (quotes.size) {
                    0 -> openQuote = openQuote.plus(" ").plus(argument)
                    1 -> {
                        openQuote = openQuote
                                .plus(" ").plus(argument)
                                .replace("\'", "").replace("\"", "")
                        joinedList.add(openQuote)
                        openQuote = null
                    }
                    else -> {
                        throw IllegalArgumentException("Quotation marks are too many: $argument")
                    }
                }
            } else {
                when (quotes.size) {
                    0 -> joinedList.add(argument)
                    1 -> openQuote = argument
                    2 -> {
                        val first = quotes.pollFirstEntry()
                        val last = quotes.pollFirstEntry()

                        require(first.value == last.value) {
                            "Conflict of quotation marks: $argument"
                        }
                        joinedList.add(argument.replace("\'", "").replace("\"", ""))
                    }
                    else -> {
                        throw IllegalArgumentException("Quotation marks are too many: $argument")
                    }
                }
            }
        }

        require(openQuote == null) {
            "Quotation mark is not closing: $openQuote"
        }

        return joinedList.toTypedArray()
    }

    /**
     * Translate primitive types in command arguments.
     */
    fun translateStringToPrimitive(string: String): Any {
        val double = string.toDoubleOrNull()
        val boolean = string.toBoolean()

        return if (double != null) {
            val integer = double.toInt().toDouble()

            if (double.coerceAtLeast(integer) == integer) {
                double.toInt()
            } else {
                double
            }
        } else if (boolean) {
            true
        } else if (string.equals("false", true)) {
            false
        } else {
            string
        }
    }

}