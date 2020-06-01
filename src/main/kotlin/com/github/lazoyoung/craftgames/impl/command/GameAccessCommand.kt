package com.github.lazoyoung.craftgames.impl.command

import com.github.lazoyoung.craftgames.api.ActionbarTask
import com.github.lazoyoung.craftgames.impl.exception.GameJoinRejectedException
import com.github.lazoyoung.craftgames.impl.exception.GameNotFound
import com.github.lazoyoung.craftgames.impl.game.Game
import com.github.lazoyoung.craftgames.impl.game.player.GameEditor
import com.github.lazoyoung.craftgames.impl.game.player.PlayerData
import com.github.lazoyoung.craftgames.impl.util.DependencyUtil
import net.md_5.bungee.api.ChatColor
import net.md_5.bungee.api.chat.ComponentBuilder
import net.milkbowl.vault.permission.Permission
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.util.*

class GameAccessCommand : CommandBase("CraftGames") {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("$error This cannot be done from console.")
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
                            sender.sendMessage("$error Please specify the game.")
                            false
                        }
                    }
                    2 -> {
                        val arg = args.last()
                        val id = arg.toIntOrNull()
                        var game = if (id != null) {
                            Game.getByID(id)
                        } else {
                            Game.find(arg, false).firstOrNull()
                        }

                        if (game != null) {
                            forceJoin(sender, selector, game)
                        } else try {
                            game = Game.openNew(arg, false)
                            forceJoin(sender, selector, game)
                            return true
                        } catch (e: GameNotFound) {
                            sender.sendMessage("$error No such game exist: $arg")
                        } catch (e: Exception) {
                            e.printStackTrace()
                            sender.sendMessage("$error Error occurred, see console for details.")
                        }
                    }
                    else -> return false
                }
            }
            "join" -> {
                if (PlayerData.get(sender) != null) {
                    sender.sendMessage("$error You're already in game.")
                    return true
                }

                if (args.isEmpty()) {
                    val game = Game.find(null, false).firstOrNull { it.canJoin(sender) }

                    if (game != null) {
                        game.joinPlayer(sender)
                    } else {
                        val gameReg = Game.getGameNames()

                        if (gameReg.isEmpty()) {
                            sender.sendMessage("$error There's no game available.")
                        } else try {
                            Game.openNew(gameReg.random(), editMode = false, mapID = null)
                                    .joinPlayer(sender)
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
                        Game.openNew(args[0], editMode = false, mapID = null)
                                .joinPlayer(sender)
                    } else {
                        game.joinPlayer(sender)
                    }
                } catch (e: GameNotFound) {
                    sender.sendMessage("$error No such game exist: ${args[0]}")
                } catch (e: Exception) {
                    e.printStackTrace()
                    sender.sendMessage("$error Error occurred, see console for details.")
                    return true
                }
            }
            "leave" -> {
                val player = PlayerData.get(sender)

                when {
                    player is GameEditor -> {
                        val game = player.getGame()

                        if (game.players.size <= 1) {
                            player.saveAndClose()
                        } else {
                            player.leaveGame()
                        }
                    }
                    player != null -> {
                        player.leaveGame()
                    }
                    else -> {
                        sender.sendMessage("$error You're not in game.")
                    }
                }
            }
        }
        return true
    }

    private fun forceJoin(operator: Player, selector: String, game: Game) {
        val players = LinkedList<Player>()

        if (!selector.contains("@")) {
            val p = Bukkit.getPlayer(selector)

            if (p != null) {
                players.add(p)
            } else {
                operator.sendMessage("$warn Player is not online: $selector")
            }
        } else {
            val comp = selector.split("@", limit = 2)
            val tag = comp[0]
            val tagValue = comp[1]

            when (tag) {
                "group" -> {
                    if (!DependencyUtil.VAULT_PERMISSION.isLoaded()) {
                        operator.sendMessage("$error Vault is required to use group tag.")
                    } else {
                        val permission = DependencyUtil.VAULT_PERMISSION.getService() as Permission

                        if (!permission.hasGroupSupport()) {
                            operator.sendMessage("$error Permission plugin is required to use group tag.")
                        } else {
                            val groups = if (tagValue == "this") {
                                permission.getPlayerGroups(null, operator)
                            } else {
                                arrayOf(tagValue)
                            }

                            for (p in Bukkit.getOnlinePlayers()) {
                                for (group in groups) {
                                    if (permission.playerInGroup(null, p, group)) {
                                        players.add(p)
                                        break
                                    }
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
                        operator.sendMessage("$warn World does not exist: $tagValue")
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

                if (cause != GameJoinRejectedException.Cause.PLAYING_THIS) {
                    blocked[p.name] = cause.name
                }
            }
        }

        for (e in blocked.entries) {
            operator.sendMessage("$warn ${e.key} was unable to join: ${e.value}")
        }

        ActionbarTask(operator, "&aForced &f$counter &aplayers to join &f${game.name}&a.").start()
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> {
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

                        if (DependencyUtil.VAULT_PERMISSION.isLoaded()) {
                            val permission = DependencyUtil.VAULT_PERMISSION.getService() as Permission

                            if (permission.hasGroupSupport()) {
                                options.add("group@this")

                                for (group in permission.groups) {
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
                        }

                        for (p in Bukkit.getOnlinePlayers()) {
                            if (lastArg.isBlank() || p.name.startsWith(lastArg, true)) {
                                options.add(p.name)

                                if (++counter > 10) {
                                    break
                                }
                            }
                        }

                        return getCompletions(lastArg, options)
                    }

                    2 -> {
                        val options = ArrayList<String>()

                        Game.find(isEditMode = false).forEach {
                            options.add(it.id.toString())
                        }

                        options.addAll(Game.getGameNames())
                        return getCompletions(args.last(), options)
                    }
                }
            }
        }

        return listOf()
    }

}