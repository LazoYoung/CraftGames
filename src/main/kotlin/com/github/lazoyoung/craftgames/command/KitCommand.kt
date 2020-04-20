package com.github.lazoyoung.craftgames.command

import com.github.lazoyoung.craftgames.api.ActionbarTask
import com.github.lazoyoung.craftgames.game.module.Module
import com.github.lazoyoung.craftgames.game.player.GamePlayer
import com.github.lazoyoung.craftgames.game.player.PlayerData
import org.bukkit.ChatColor
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class KitCommand : CommandBase {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("This cannot be done from console.")
            return true
        }

        when (val pdata = PlayerData.get(sender)) {
            is GamePlayer -> {
                val game = pdata.getGame()

                when {
                    !(Module.getItemModule(game).canSelectKit(sender)) -> {
                        sender.sendMessage("You can't do this now.")
                    }
                    args.isEmpty() -> {
                        sender.sendMessage("Provide the name of kit to select.")
                        return false
                    }
                    else -> {
                        val name = args[0].toLowerCase()

                        try {
                            Module.getItemModule(game).selectKit(name, sender)
                            ActionbarTask(sender, "&aSelected kit: &f$name").start()
                        } catch (e: IllegalArgumentException) {
                            sender.sendMessage(ChatColor.RED.toString().plus(e.localizedMessage))
                        }
                    }
                }
            }
            else -> {
                sender.sendMessage("You're not playing a game.")
            }
        }

        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): MutableList<String> {
        if (args.isEmpty())
            return command.aliases

        if (args.size == 1) {
            val playerData = PlayerData.get(sender as Player)

            if (playerData?.isOnline() == true) {
                val kitList = playerData.getGame().resource.kitData.keys

                if (kitList.isNotEmpty()) {
                    return getCompletions(args[0], *kitList.toTypedArray())
                }
            }
        }

        return mutableListOf()
    }

}