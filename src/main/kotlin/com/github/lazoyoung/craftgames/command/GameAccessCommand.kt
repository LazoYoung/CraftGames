package com.github.lazoyoung.craftgames.command

import com.github.lazoyoung.craftgames.game.Game
import com.github.lazoyoung.craftgames.game.player.GameEditor
import com.github.lazoyoung.craftgames.game.player.PlayerData
import net.md_5.bungee.api.ChatColor
import net.md_5.bungee.api.chat.ComponentBuilder
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
                    val game = Game.find(null, false).firstOrNull { it.canJoin() }

                    if (game != null) {
                        game.join(sender)
                    } else {
                        val gameReg = Game.getGameNames()

                        if (gameReg.isEmpty()) {
                            sender.sendMessage("There's no game available.")
                        } else try {
                            Game.openNew(gameReg.random(), editMode = false, mapID = null, consumer = Consumer {
                                it.join(sender)
                            })
                        } catch (e: Exception) {
                            e.printStackTrace()
                            sender.sendMessage(*ComponentBuilder(e.localizedMessage).color(ChatColor.RED).create())
                        }
                    }
                    return true
                }

                try {
                    val game = Game.find(args[0], false).firstOrNull { it.canJoin() }

                    if (game == null) {
                        Game.openNew(args[0], editMode = false, mapID = null, consumer = Consumer{
                            it.join(sender)
                        })
                    } else {
                        game.join(sender)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    sender.sendMessage(*ComponentBuilder("Error occurred.").color(ChatColor.RED).create())
                    return true
                }
            }
            "leave" -> {
                val player = PlayerData.get(sender)

                when {
                    player is GameEditor -> {
                        player.saveAndLeave()
                    }
                    player != null -> {
                        player.leaveGame()
                    }
                    else -> {
                        sender.sendMessage("You're not in game.")
                    }
                }
            }
        }
        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): MutableList<String> {
        if (args.isEmpty())
            return command.aliases

        if (command.label == "join" && args.size == 1)
            return getGameTitles(args[0])

        return mutableListOf()
    }

}