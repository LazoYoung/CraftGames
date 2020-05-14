package com.github.lazoyoung.craftgames.api

import com.github.lazoyoung.craftgames.api.module.Module
import io.github.jorelali.commandapi.api.exceptions.WrapperCommandSyntaxException
import org.bukkit.command.CommandSender

@FunctionalInterface
interface CommandHandler {

    /**
     * Function will run if the underlying command is executed with [args] by [sender].
     *
     * @param module Access point of game modules.
     * @param sender Who executed this function.
     * @param args Command arguments. Cast each element to appropriate type.
     * @throws WrapperCommandSyntaxException
     */
    fun run(module: Module, sender: CommandSender, args: Array<Any>)

}