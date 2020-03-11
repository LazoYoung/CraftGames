package com.github.lazoyoung.craftgames.command

import com.github.lazoyoung.craftgames.Game
import com.github.lazoyoung.craftgames.GameFactory
import com.github.lazoyoung.craftgames.exception.FaultyConfiguration
import com.github.lazoyoung.craftgames.exception.GameNotFound
import com.github.lazoyoung.craftgames.exception.MapNotFound
import com.github.lazoyoung.craftgames.exception.ScriptEngineNotFound
import com.github.lazoyoung.craftgames.script.ScriptBase
import groovy.lang.GroovyRuntimeException
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class GameCommand : CommandExecutor {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        val gameID: String
        val game: Game

        if (command.name != "game")
            return true

        if (args.isEmpty()) {
            sender.sendMessage(arrayOf(
                    "/game <gameID> start <mapID>",
                    "/game <gameID> script <scriptID> execute"
            ))
            return true
        }

        try {
            gameID = args[0]
            game = GameFactory.openNew(gameID)
        } catch (e: GameNotFound) {
            sender.sendMessage(e.message!!)
            return true
        } catch (e: FaultyConfiguration) {
            sender.sendMessage(e.message!!)
            return true
        } catch (e: RuntimeException) {
            e.printStackTrace()
            sender.sendMessage(e.message!!)
            return true
        } catch (e: ScriptEngineNotFound) {
            e.printStackTrace()
            sender.sendMessage(e.message)
            return true
        }

        if (args.size == 1)
            return false

        if (args[1] == "start") {
            if (args.size < 3)
                return false

            try {
                val world = game.loadMap(args[2])
                if (world == null) {
                    sender.sendMessage("That map is already loaded.")
                    return true
                }
                if (sender is Player) {
                    sender.teleport(world.spawnLocation)
                }
                sender.sendMessage("Loaded map: ${game.mapID}")
            } catch (e: RuntimeException) {
                e.printStackTrace()
                sender.sendMessage("Unable to load map.")
            } catch (e: FaultyConfiguration) {
                e.printStackTrace()
                sender.sendMessage("Unable to load map.")
            } catch (e: MapNotFound) {
                sender.sendMessage(e.message!!)
            }
        } else if (args[1] == "script") {
            if (args.size < 4)
                return false

            val scriptID: String = args[2]
            val script: ScriptBase? = game.scriptRegistry[scriptID]

            if (script == null) {
                sender.sendMessage("Script $scriptID does not exist.")
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
        } else return false

        return true
    }

}