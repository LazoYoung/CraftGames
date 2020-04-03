package com.github.lazoyoung.craftgames.command

import com.github.lazoyoung.craftgames.api.ActionbarTask
import com.github.lazoyoung.craftgames.game.module.Module
import com.github.lazoyoung.craftgames.game.player.GamePlayer
import com.github.lazoyoung.craftgames.game.player.PlayerData
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
                val game = pdata.game

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
                            val module = Module.getItemModule(game)
                            module.selectKit(name, sender)
                            ActionbarTask(sender, text = *arrayOf("&aSelected kit: $name"))
                        } catch (e: IllegalArgumentException) {
                            sender.sendMessage("That kit does not exist!")
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
            val kitList = playerData?.game?.resource?.kitData?.keys

            if (kitList?.isEmpty() == false) {
                return getCompletions(args[0], *kitList.toTypedArray())
            }
        }

        return mutableListOf()
    }

}