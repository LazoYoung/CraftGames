package com.github.lazoyoung.craftgames.command

import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.command.TabExecutor

class CoordtagCommand : TabExecutor {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (args.isEmpty()) {
            sender.sendMessage(arrayOf(
                    "/ctag select -game <title>",
                    "/ctag select -tag <name>",
                    "/ctag select -map <mapID>",
                    "/ctag select -index <index>",
                    "/ctag create",
                    "/ctag remove",
                    "/ctag list"
            ))
            return true
        }


        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): MutableList<String>? {
        if (args.isEmpty())
            return null

        if (args.size == 1)
            return arrayListOf("add", "remove", "list")

        if (args[0].equals("add", true)) {
        }
        TODO()
    }

}