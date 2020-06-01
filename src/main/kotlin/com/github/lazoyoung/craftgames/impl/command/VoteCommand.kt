package com.github.lazoyoung.craftgames.impl.command

import com.github.lazoyoung.craftgames.api.ActionbarTask
import com.github.lazoyoung.craftgames.api.TimeUnit
import com.github.lazoyoung.craftgames.api.Timer
import com.github.lazoyoung.craftgames.impl.exception.MapNotFound
import com.github.lazoyoung.craftgames.impl.game.player.GamePlayer
import com.github.lazoyoung.craftgames.impl.game.player.PlayerData
import net.md_5.bungee.api.ChatColor
import net.md_5.bungee.api.chat.ComponentBuilder
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class VoteCommand : CommandBase("Vote") {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("$error This cannot be run from console.")
            return true
        }

        val pdata = PlayerData.get(sender)

        if (pdata !is GamePlayer) {
            sender.sendMessage(
                    *ComponentBuilder("$error You can't vote outside game lobby.")
                    .color(ChatColor.YELLOW).create()
            )
            return true
        }

        if (args.isEmpty())
            TODO("Vote menu is not implemented")

        try {
            val player = pdata.getPlayer()
            val lobbyService = pdata.getGame().getLobbyService()
            val text = if (lobbyService.voteMap(player, mapName = args[0])) {
                "&aYou voted for ${args[0]}!"
            } else {
                "&eYou have voted already."
            }

            ActionbarTask(player, Timer(TimeUnit.SECOND, 3), false, text)
                    .start()
        } catch (e: MapNotFound) {
            sender.sendActionBar("$error ${e.localizedMessage}")
        }
        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> {
        val pData = PlayerData.get(sender as Player)

        if (pData?.isOnline() == true && args.size == 1) {
            val registry = pData.getGame().resource.mapRegistry

            return getCompletions(
                    query = args[0],
                    options = registry.getMapNames(true)
            )
        }

        return listOf()
    }
}