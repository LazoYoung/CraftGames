package com.github.lazoyoung.craftgames.command

import com.github.lazoyoung.craftgames.api.ActionbarTask
import com.github.lazoyoung.craftgames.api.TimeUnit
import com.github.lazoyoung.craftgames.api.Timer
import com.github.lazoyoung.craftgames.game.Game
import com.github.lazoyoung.craftgames.game.module.Module
import com.github.lazoyoung.craftgames.game.player.GamePlayer
import com.github.lazoyoung.craftgames.game.player.PlayerData
import com.github.lazoyoung.craftgames.internal.exception.MapNotFound
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
            val player = pdata.player
            val lobby = Module.getLobbyModule(pdata.game)
            val text = if (lobby.voteMap(player, mapName = args[0])) {
                "&aYou voted for ${args[0]}!"
            } else {
                "&eYou have voted already."
            }

            ActionbarTask(player, Timer(TimeUnit.SECOND, 3), false, text)
                    .start()
        } catch (e: MapNotFound) {
            sender.sendActionBar(ChatColor.YELLOW.toString() + e.localizedMessage)
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