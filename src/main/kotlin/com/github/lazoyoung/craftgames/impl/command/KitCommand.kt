package com.github.lazoyoung.craftgames.impl.command

import com.github.lazoyoung.craftgames.api.ActionbarTask
import com.github.lazoyoung.craftgames.impl.command.page.PageBody
import com.github.lazoyoung.craftgames.impl.game.player.GamePlayer
import com.github.lazoyoung.craftgames.impl.game.player.PlayerData
import org.bukkit.ChatColor
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class KitCommand : CommandBase("Kit") {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("$error This cannot be run from console.")
            return true
        }

        when (val pdata = PlayerData.get(sender)) {
            is GamePlayer -> {
                val game = pdata.getGame()
                val itemService = game.getItemService()

                when {
                    !(itemService.canSelectKit(sender)) -> {
                        sender.sendMessage("$error You can't do this now.")
                    }
                    args.isEmpty() -> {
                        sender.sendMessage("$error Please provide the name.")
                        return false
                    }
                    else -> {
                        val name = args[0].toLowerCase()

                        try {
                            itemService.selectKit(name, sender)
                            ActionbarTask(sender, "&aSelected kit: &f$name").start()
                        } catch (e: IllegalArgumentException) {
                            sender.sendMessage(ChatColor.RED.toString().plus(e.localizedMessage))
                        }
                    }
                }
            }
            else -> {
                val text = PageBody(
                        PageBody.Element(
                                "$error You must be in a game.",
                                "&6Click here to play game.",
                                "/join "
                        )
                ).getBodyText(sender)
                sender.sendMessage(*text)
            }
        }

        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> {
        if (args.isEmpty())
            return command.aliases

        if (args.size == 1) {
            val playerData = PlayerData.get(sender as Player)

            if (playerData?.isOnline() == true) {
                val kitList = playerData.getGame().resource.kitData.keys

                if (kitList.isNotEmpty()) {
                    return getCompletions(args[0], kitList.toList())
                }
            }
        }

        return listOf()
    }

}