package com.github.lazoyoung.craftgames.command

import org.bukkit.command.TabExecutor

interface CommandBase : TabExecutor {

    fun getCompletions(query: String, vararg args: String) : MutableList<String> {
        return getCompletions(query, args.toList())
    }

    fun getCompletions(query: String, args: List<String>) : MutableList<String> {
        return args.filter { it.isEmpty() || it.startsWith(query) }.toMutableList()
    }

}