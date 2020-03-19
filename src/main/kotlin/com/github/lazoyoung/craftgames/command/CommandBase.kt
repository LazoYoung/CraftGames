package com.github.lazoyoung.craftgames.command

import com.github.lazoyoung.craftgames.Main
import org.bukkit.command.TabExecutor

interface CommandBase : TabExecutor {

    fun getCompletions(query: String, vararg args: String) : MutableList<String> {
        return args
                .filter { it.isEmpty() || it.startsWith(query.toLowerCase()) }
                .toMutableList()
    }

    fun getGameTitles(query: String) : MutableList<String> {
        return getCompletions(
                query = query,
                args = *Main.config.getConfigurationSection("games")
                        ?.getKeys(false)
                        ?.toTypedArray()
                        ?: emptyArray()
        )
    }

}