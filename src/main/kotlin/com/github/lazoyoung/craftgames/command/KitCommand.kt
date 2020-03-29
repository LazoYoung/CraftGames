package com.github.lazoyoung.craftgames.command

import com.github.lazoyoung.craftgames.game.Game
import com.github.lazoyoung.craftgames.module.Module
import com.github.lazoyoung.craftgames.player.GamePlayer
import com.github.lazoyoung.craftgames.player.PlayerData
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class KitCommand : CommandBase {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("This cannot be done from console.")
            return true
        }

        if (args.isEmpty()) {
            sender.sendMessage("Provide the name of kit to select.")
            return false
        }

        when (val pdata = PlayerData.get(sender)) {
            is GamePlayer -> {
                val game = pdata.game

                if (game.phase != Game.Phase.LOBBY) {
                    sender.sendMessage("You can't select a kit now.")
                } else {
                    val name = args[0].toLowerCase()

                    try {
                        Module.getItemModule(game).fillKit(name, sender.inventory)
                        sender.sendMessage("Selected kit: $name")
                    } catch (e: IllegalArgumentException) {
                        sender.sendMessage("That kit does not exist!")
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
            // TODO List up all the kits.
        }

        return mutableListOf()
    }

}