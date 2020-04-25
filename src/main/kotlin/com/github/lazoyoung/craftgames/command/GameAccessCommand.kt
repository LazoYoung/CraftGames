package com.github.lazoyoung.craftgames.command

import com.github.lazoyoung.craftgames.Main
import com.github.lazoyoung.craftgames.api.ActionbarTask
import com.github.lazoyoung.craftgames.game.Game
import com.github.lazoyoung.craftgames.game.player.GameEditor
import com.github.lazoyoung.craftgames.game.player.PlayerData
import com.github.lazoyoung.craftgames.internal.exception.GameNotFound
import net.md_5.bungee.api.ChatColor
import net.md_5.bungee.api.chat.ComponentBuilder
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.util.*
import java.util.function.Consumer

class GameAccessCommand : CommandBase {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("This cannot be done from console.")
            return true
        }

        when (command.label) {
            "forcejoin" -> {
                if (args.isEmpty()) {
                    return false
                }

                val selector = args[0]

                when (args.size) {
                    1 -> {
                        val game = PlayerData.get(sender)?.getGame()

                        return if (game != null) {
                            forceJoin(sender, selector, game)
                            true
                        } else {
                            sender.sendMessage("\u00A7ePlease specify the game.")
                            false
                        }
                    }
                    2 -> {
                        val arg = args.last()
                        val id = arg.toIntOrNull()
                        val game = if (id != null) {
                            Game.getByID(id)
                        } else {
                            Game.find(arg, false).firstOrNull()
                        }

                        if (game != null) {
                            forceJoin(sender, selector, game)
                        } else try {
                            Game.openNew(arg, false, consumer = Consumer {
                                forceJoin(sender, selector, it)
                            })
                            return true
                        } catch (e: GameNotFound) {
                            sender.sendMessage("\u00A7eNo such game exist: $arg")
                        } catch (e: Exception) {
                            e.printStackTrace()
                            sender.sendMessage("\u00A7cError occurred, see console for details.")
                        }
                    }
                    else -> return false
                }
            }
            "join" -> {
                if (PlayerData.get(sender) != null) {
                    sender.sendMessage("\u00A7eYou're already in game.")
                    return true
                }

                if (args.isEmpty()) {
                    val game = Game.find(null, false).firstOrNull { it.canJoin(sender) }

                    if (game != null) {
                        game.joinPlayer(sender)
                    } else {
                        val gameReg = Game.getGameNames()

                        if (gameReg.isEmpty()) {
                            sender.sendMessage("There's no game available.")
                        } else try {
                            Game.openNew(gameReg.random(), editMode = false, mapID = null, consumer = Consumer {
                                it.joinPlayer(sender)
                            })
                        } catch (e: Exception) {
                            e.printStackTrace()
                            sender.sendMessage(*ComponentBuilder(e.localizedMessage).color(ChatColor.RED).create())
                        }
                    }
                    return true
                }

                try {
                    val game = Game.find(args[0], false).firstOrNull { it.canJoin(sender) }

                    if (game == null) {
                        Game.openNew(args[0], editMode = false, mapID = null, consumer = Consumer{
                            it.joinPlayer(sender)
                        })
                    } else {
                        game.joinPlayer(sender)
                    }
                } catch (e: GameNotFound) {
                    sender.sendMessage("\u00A7eNo such game exist: ${args[0]}")
                } catch (e: Exception) {
                    e.printStackTrace()
                    sender.sendMessage("\u00A7cError occurred, see console for details.")
                    return true
                }
            }
            "leave" -> {
                val player = PlayerData.get(sender)

                when {
                    player is GameEditor -> {
                        player.saveAndClose()
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

    private fun forceJoin(operator: Player, selector: String, game: Game) {
        if (!game.canJoin(operator)) {
            operator.sendMessage("\u00A7e".plus(game.getRejectCause(operator)!!.message))
            return
        }

        val players = LinkedList<Player>()

        if (!selector.contains("@")) {
            val p = Bukkit.getPlayer(selector)

            if (p != null) {
                players.add(p)
            } else {
                operator.sendMessage("\u00A7ePlayer is not online: $selector")
            }
        } else {
            val comp = selector.split("@", limit = 2)
            val tag = comp[0]
            val tagValue = comp[1]

            when (tag) {
                "group" -> {
                    if (Main.vaultPerm?.isEnabled != true) {
                        operator.sendMessage("\u00A7eVault is required to use group tag.")
                    } else if (!Main.vaultPerm!!.hasGroupSupport()) {
                        operator.sendMessage("\u00A7ePermission plugin is required to use group tag.")
                    } else {
                        val perm = Main.vaultPerm!!
                        val groups = if (tagValue == "this") {
                            perm.getPlayerGroups(null, operator)
                        } else {
                            arrayOf(tagValue)
                        }

                        for (p in Bukkit.getOnlinePlayers()) {
                            for (group in groups) {
                                if (perm.playerInGroup(null, p, group)) {
                                    players.add(p)
                                    break
                                }
                            }
                        }
                    }
                }
                "world" -> {
                    val world = if (tagValue == "this") {
                        operator.world
                    } else {
                        Bukkit.getWorld(tagValue)
                    }

                    if (world != null) {
                        players.addAll(world.players)
                    } else {
                        operator.sendMessage("\u00A7eWorld does not exist: $tagValue")
                    }
                }
            }
        }

        val blocked = HashMap<String, String>()
        var counter = 0

        for (p in players) {
            if (game.canJoin(p)) {
                game.joinPlayer(p)
                counter++
            } else {
                val cause = game.getRejectCause(p)!!

                if (cause != Game.JoinRejection.PLAYING) {
                    blocked[p.name] = cause.name
                }
            }
        }

        for (e in blocked.entries) {
            operator.sendMessage("&f${e.key} &eis unable to join: ${e.value}")
        }

        ActionbarTask(operator, "&aForced &f$counter &aplayers to join &f${game.name}&a.").start()
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): MutableList<String> {
        if (args.isEmpty())
            return command.aliases

        when (command.label) {
            "join" -> {
                if (args.size == 1) {
                    return getGameTitles(args[0])
                }
            }
            "forcejoin" -> {
                when (args.size) {
                    1 -> {
                        val options = ArrayList<String>()
                        val perm = Main.vaultPerm
                        val lastArg = args.last()
                        var counter = 0

                        options.add("world@this")

                        for (world in Bukkit.getWorlds()) {
                            val option = "world@${world.name}"

                            if (lastArg.isBlank() || option.startsWith(lastArg, true)) {
                                options.add(option)

                                if (++counter > 10) {
                                    break
                                }
                            }
                        }

                        counter = 0

                        if (perm?.isEnabled == true && perm.hasGroupSupport()) {
                            options.add("group@this")

                            for (group in perm.groups) {
                                val option = "group@$group"

                                if (lastArg.isBlank() || option.startsWith(lastArg, true)) {
                                    options.add(option)

                                    if (++counter > 10) {
                                        break
                                    }
                                }
                            }

                            counter = 0
                        }

                        for (p in Bukkit.getOnlinePlayers()) {
                            if (lastArg.isBlank() || p.name.startsWith(lastArg, true)) {
                                options.add(p.name)

                                if (++counter > 10) {
                                    break
                                }
                            }
                        }

                        return getCompletions(lastArg, *options.toTypedArray())
                    }

                    2 -> {
                        val options = ArrayList<String>()

                        Game.find(isEditMode = false).forEach {
                            options.add(it.id.toString())
                        }

                        options.addAll(Game.getGameNames())
                        return getCompletions(args.last(), *options.toTypedArray())
                    }
                }
            }
        }

        return mutableListOf()
    }

}