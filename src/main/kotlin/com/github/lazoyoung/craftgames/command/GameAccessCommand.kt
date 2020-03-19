package com.github.lazoyoung.craftgames.command

import com.github.lazoyoung.craftgames.exception.FaultyConfiguration
import com.github.lazoyoung.craftgames.exception.GameNotFound
import com.github.lazoyoung.craftgames.game.GameFactory
import com.github.lazoyoung.craftgames.player.GamePlayer
import com.github.lazoyoung.craftgames.player.PlayerData
import net.md_5.bungee.api.ChatColor
import net.md_5.bungee.api.chat.TextComponent
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.util.function.Consumer

class GameAccessCommand : CommandBase {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("This cannot be done from console.")
            return true
        }

        when (command.label) {
            "join" -> {
                if (PlayerData.get(sender) != null) {
                    sender.sendMessage("You are already in a game.")
                    return true
                }

                if (args.isEmpty()) {
                    TODO("Auto matching is not implemented.")
                }


                val warn = TextComponent()
                warn.color = ChatColor.RED

                try {
                    var game = GameFactory.find(args[0], false).firstOrNull { it.canJoin }

                    if (game == null) {
                        game = GameFactory.openNew(args[0], true, Consumer{
                            game!!.join(sender)
                        })
                    }
                } catch (e: GameNotFound) {
                    warn.color = ChatColor.YELLOW
                    warn.text = e.message
                    sender.sendMessage(e.message!!)
                    return true
                } catch (e: FaultyConfiguration) {
                    warn.text = e.message
                    sender.sendMessage(e.message!!)
                    return true
                } catch (e: RuntimeException) {
                    warn.text = e.message
                    e.printStackTrace()
                    sender.sendMessage(e.message!!)
                    return true
                }
            }
            "leave" -> {
                val player = PlayerData.get(sender)

                if (player is GamePlayer) {
                    player.game.leave(sender)
                } else {
                    sender.sendMessage("You're not in game.")
                }
            }
        }
        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): MutableList<String> {
        val cmd = command.label

        if (args.isEmpty())
            return command.aliases

        if (cmd == "join" && args.size == 1)
            return getGameTitles(args[0])

        return mutableListOf()
    }

}