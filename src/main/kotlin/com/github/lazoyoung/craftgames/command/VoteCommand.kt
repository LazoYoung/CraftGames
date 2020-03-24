package com.github.lazoyoung.craftgames.command

import com.github.lazoyoung.craftgames.exception.MapNotFound
import com.github.lazoyoung.craftgames.game.Game
import com.github.lazoyoung.craftgames.module.Module
import com.github.lazoyoung.craftgames.player.GamePlayer
import com.github.lazoyoung.craftgames.player.PlayerData
import net.md_5.bungee.api.ChatColor
import net.md_5.bungee.api.chat.ComponentBuilder
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class VoteCommand : CommandBase {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("This command is not available in console.")
            return true
        }

        val pdata = PlayerData.get(sender)

        if (pdata !is GamePlayer) {
            sender.sendMessage(
                    *ComponentBuilder("You can't vote outside a game lobby.")
                    .color(ChatColor.YELLOW).create()
            )
            return true
        }

        if (args.isEmpty())
            TODO("Vote menu is not implemented")

        try {
            if (Module.getLobbyModule(pdata.game).voteMap(pdata.player, mapName = args[0])) {
                // TODO UI Module: display duration is too short.
                sender.sendActionBar('&', "&aYou voted for ${args[0]}!")
            } else {
                sender.sendActionBar('&', "&eYou have already voted.")
            }
        } catch (e: MapNotFound) {
            sender.sendActionBar('&', e.localizedMessage)
        }
        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): MutableList<String> {
        val pData = PlayerData.get(sender as Player)

        if (pData != null && args.size == 1)
            return getCompletions(
                    query = args[0],
                    args = *Game.getMapNames(pData.game.name, false).toTypedArray()
            )

        return mutableListOf()
    }
}