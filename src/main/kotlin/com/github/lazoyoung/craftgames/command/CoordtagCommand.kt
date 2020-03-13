package com.github.lazoyoung.craftgames.command

import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.command.TabExecutor

class CoordtagCommand : TabExecutor {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (args.isEmpty() || args[0].equals("help", true)) {
            if (args.size < 2 || args[1] == "1") {
                sender.sendMessage(arrayOf(
                        "Coordinate Tags Command (Page 1/2)",
                        "/ctag select -game <title>",
                        "/ctag select -tag <name>",
                        "/ctag select -map <mapID>",
                        "/ctag select -index <index>",
                        "> Next page: /ctag help 2"
                ))
            } else if (args[1] == "2") {
                sender.sendMessage(arrayOf(
                        "Coordinate Tags Command (Page 2/2)",
                        "You must select flag prior to using these commands.",
                        "/ctag create - Create selected tag or index",
                        "/ctag remove - Removes selected tag or index",
                        "/ctag list - List up the indexes based on the flags.",
                        "< Previous page: /ctag help 1"
                ))
            }
            return true
        }


        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): MutableList<String>? {
        if (args.isEmpty())
            return null

        if (args.size == 1)
            return listOf("help", "select", "create", "remove", "list").filter { args[0].isEmpty() || it.startsWith(args[0]) }.toMutableList()

        if (args[0].equals("add", true)) {
            TODO()
        }
        TODO("Not implemented.")
    }

}