package com.github.lazoyoung.craftgames.command

import com.github.lazoyoung.craftgames.exception.FaultyConfiguration
import com.github.lazoyoung.craftgames.exception.GameNotFound
import com.github.lazoyoung.craftgames.game.Game
import com.github.lazoyoung.craftgames.player.GameEditor
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
                    val game = Game.find(null, false).firstOrNull { it.canJoin }

                    if (game != null) {
                        game.join(sender)
                    } else {
                        val gameReg = Game.getGameList()

                        if (gameReg.isEmpty()) {
                            sender.sendMessage("There's no game available.")
                        } else {
                            Game.openNew(gameReg.random(), editMode = false, mapID = null, consumer = Consumer {
                                it.join(sender)
                            })
                        }
                    }
                    return true
                }

                val warn = TextComponent()
                warn.color = ChatColor.RED

                try {
                    val game = Game.find(args[0], false).firstOrNull { it.canJoin }

                    if (game == null) {
                        Game.openNew(args[0], editMode = false, mapID = null, consumer = Consumer{
                            it.join(sender)
                        })
                    } else {
                        game.join(sender)
                    }
                } catch (e: GameNotFound) {
                    warn.color = ChatColor.YELLOW
                    warn.text = e.message
                    sender.sendMessage(warn)
                    return true
                } catch (e: FaultyConfiguration) {
                    warn.text = e.message
                    sender.sendMessage(warn)
                    return true
                } catch (e: RuntimeException) {
                    warn.text = e.message
                    e.printStackTrace()
                    sender.sendMessage(warn)
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
                        player.game.leave(sender)
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
        val cmd = command.label

        if (args.isEmpty())
            return command.aliases

        if (cmd == "join" && args.size == 1)
            return getGameTitles(args[0])

        return mutableListOf()
    }

}