package com.github.lazoyoung.craftgames.command

import com.github.lazoyoung.craftgames.exception.*
import com.github.lazoyoung.craftgames.game.Game
import com.github.lazoyoung.craftgames.game.GameFactory
import com.github.lazoyoung.craftgames.player.GameEditor
import com.github.lazoyoung.craftgames.player.GamePlayer
import com.github.lazoyoung.craftgames.player.PlayerData
import com.github.lazoyoung.craftgames.player.Spectator
import com.github.lazoyoung.craftgames.script.ScriptBase
import groovy.lang.GroovyRuntimeException
import org.bukkit.World
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.util.function.Consumer

class GameCommand : CommandBase {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (args.isEmpty()) {
            sender.sendMessage(arrayOf(
                    "/game start <title> <map>",
                    "/game stop <id>",
                    "/game edit <title> <map> : Enter the edit mode.",
                    "/game save : Save the changes and leave edit mode.",
                    "/game script <script> execute"
            ))
            return true
        }

        when (args[0].toLowerCase()) {
            "start" -> {
                if (args.size < 3)
                    return false

                if (sender !is Player) {
                    sender.sendMessage("This cannot be done from console.")
                    return true
                }

                val game = getGame(args[1], sender) ?: return true
                generateMap(sender, game, args[2], Consumer{
                    if (it == null)
                        return@Consumer

                    // TODO Future redundant: Players are moved into the map by Game#start()
                    sender.teleport(it.spawnLocation)
                    sender.sendMessage("Started ${game.name} with map: ${game.map.mapID}")
                })
            }
            "stop" -> {
                val id = args[1].toIntOrNull()

                if (args.size < 2 || id == null)
                    return false

                try {
                    if (GameFactory.findByID(id)?.stop() == true) {
                        sender.sendMessage("The game has been stopped.")
                    } else {
                        sender.sendMessage("That game does not exist.")
                    }
                } catch (e: NullPointerException) {
                    sender.sendMessage("No game is running with id $id.")
                    return true
                }
            }
            "edit" -> {
                if (args.size < 3)
                    return false

                if (sender !is Player) {
                    sender.sendMessage("This cannot be done from console.")
                    return true
                }

                when (PlayerData.get(sender)) {
                    is Spectator, is GamePlayer -> {
                        sender.sendMessage("You have to leave the game you're at.")
                        return true
                    }
                    is GameEditor -> {
                        sender.sendMessage("You're already in editor mode.")
                        return true
                    }
                    null -> {
                        try {
                            GameEditor.start(sender, args[1], args[2], Consumer{
                                if (it == null) {
                                    sender.sendMessage("Error occurred.")
                                } else {
                                    val map = it.game.map.mapID
                                    sender.sendMessage("You started editing $map.")
                                }
                            })
                        } catch (e: NumberFormatException) {
                            return false
                        } catch (e: ConcurrentPlayerState) {
                            sender.sendMessage("Unexpected error! See console for details.")
                            e.printStackTrace()
                        } catch (e: MapNotFound) {
                            sender.sendMessage(e.message!!)
                        } catch (e: GameNotFound) {
                            sender.sendMessage(e.message!!)
                        } catch (e: FaultyConfiguration) {
                            sender.sendMessage(e.message!!)
                        } catch (e: RuntimeException) {
                            sender.sendMessage("Unexpected error! See console for details.")
                            e.printStackTrace()
                        }
                    }
                }
            }
            "save" -> {
                if (sender !is Player) {
                    sender.sendMessage("This cannot be done from console.")
                    return true
                }

                val playerData = PlayerData.get(sender)

                if (playerData !is GameEditor) {
                    sender.sendMessage("You must be in editor mode.")
                } else {
                    playerData.saveAndLeave()
                }
            }
            "script" -> {
                if (args.size < 4)
                    return false

                if (sender !is Player) {
                    sender.sendMessage("This cannot be done from console.")
                    return true
                }

                val playerData = PlayerData.get(sender)
                val scriptID = args[2]
                val script: ScriptBase?

                if (playerData !is GameEditor) {
                    sender.sendMessage("You must be in editor mode.")
                    return true
                }

                script = playerData.game.scriptReg[scriptID]

                if (script == null) {
                    sender.sendMessage("That script ($scriptID) does not exist.")
                    return true
                }

                if (args[3] == "execute") {
                    try {
                        script.parse()
                        script.execute()
                        sender.sendMessage("Script $scriptID has been executed.")
                    } catch (e: GroovyRuntimeException) {
                        sender.sendMessage("Compilation error: ${e.message}")
                        e.printStackTrace()
                    } catch (e: ScriptEngineNotFound) {
                        sender.sendMessage(e.message)
                    }
                }
            }
            else -> return false
        }
        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>)
            : MutableList<String>? {
        if (args.isEmpty())
            return command.aliases

        if (args.size == 1)
            return getCompletions(args[0], "start", "stop", "edit", "save", "script")

        when (args[0].toLowerCase()) {
            "start", "edit" -> {
                return when (args.size) {
                    2 -> getGameTitles(args[1])
                    3 -> {
                        try {
                            getCompletions(args[2], *GameFactory.getDummy(args[1]).map.getMapList())
                        } catch (e: Exception) { return mutableListOf() }
                    }
                    else -> mutableListOf()
                }
            }
            "stop" -> {
                return when (args.size) {
                    2 -> getCompletions(args[1], *GameFactory.find().map { it.id.toString() }.toTypedArray())
                    else -> mutableListOf()
                }
            }
            "script" -> {
                return when (args.size) {
                    2 -> getGameTitles(args[1])
                    3 -> {
                        try {
                            getCompletions(
                                    query = args[2],
                                    args = *GameFactory.getDummy(args[1]).scriptReg.keys.toList().toTypedArray()
                            )
                        } catch (e: Exception) { return mutableListOf() }
                    }
                    4 -> getCompletions(args[3], "execute")
                    else -> mutableListOf()
                }
            }
        }
        return mutableListOf()
    }

    private fun getGame(name: String, sender: CommandSender, dummy: Boolean = false): Game? {
        try {
            return if (dummy) {
                GameFactory.getDummy(name)
            } else {
                GameFactory.openNew(name)
            }
        } catch (e: GameNotFound) {
            sender.sendMessage(e.message!!)
        } catch (e: FaultyConfiguration) {
            e.printStackTrace()
            sender.sendMessage(e.message!!)
        } catch (e: RuntimeException) {
            e.printStackTrace()
            sender.sendMessage(e.message!!)
        } catch (e: ScriptEngineNotFound) {
            e.printStackTrace()
            sender.sendMessage(e.message)
        }
        return null
    }

    private fun generateMap(sender: CommandSender, game: Game, mapID: String, callback: Consumer<World?>) {
        try {
            game.map.generate(mapID, callback)
        } catch (e: RuntimeException) {
            e.printStackTrace()
            sender.sendMessage("Unable to load map.")
        } catch (e: FaultyConfiguration) {
            e.printStackTrace()
            sender.sendMessage("Unable to load map.")
        } catch (e: MapNotFound) {
            sender.sendMessage(e.message!!)
        }
    }

}