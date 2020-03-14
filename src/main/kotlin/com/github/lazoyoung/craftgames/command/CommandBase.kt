package com.github.lazoyoung.craftgames.command

import com.github.lazoyoung.craftgames.Main
import org.bukkit.block.Block
import org.bukkit.command.TabExecutor
import java.util.*
import java.util.function.Consumer
import kotlin.collections.HashMap

interface CommandBase : TabExecutor {

    companion object {
        // This is NOT thread-safe!
        val blockPrompt = HashMap<UUID, Consumer<Block>>()
    }

    fun getCompletions(query: String, vararg args: String) : MutableList<String> {
        return args.filter { it.isEmpty() || it.startsWith(query) }.toMutableList()
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