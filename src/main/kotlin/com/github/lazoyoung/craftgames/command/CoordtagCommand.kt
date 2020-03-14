package com.github.lazoyoung.craftgames.command

import com.github.lazoyoung.craftgames.game.CoordinateTag
import com.github.lazoyoung.craftgames.game.Game
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import java.util.*
import kotlin.collections.HashMap

class CoordtagCommand : CommandBase {
    private enum class Flag { GAME, TYPE, TAG, MAP, RESET }
    private val flagState = HashMap<String, EnumMap<Flag, Any>>()

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (args.isEmpty() || args[0].equals("help", true)) {
            if (args.size < 2 || args[1] == "1") {
                sender.sendMessage(arrayOf(
                        "CoordTag Command Manual (Page 1/3)",
                        "----------------------------",
                        "/ctag select -game <title>",
                        "/ctag select -type <block/entity>",
                        "/ctag select -tag <name>",
                        "/ctag select -map <mapID>",
                        "/ctag select -reset",
                        "----------------------------",
                        "□ Move page: /ctag help <page>"
                ))
            } else if (args[1] == "2") {
                sender.sendMessage(arrayOf(
                        "CoordTag Command Manual (Page 2/3)",
                        "Select flag prior to using this command.", // TODO Clickable hyperlink text
                        "----------------------------",
                        "/ctag create <name> : Create a new tag based on flags.",
                        "/ctag capture : Capture a coordinate into the selected tag.",
                        "/ctag remove : Remove the selected tag.",
                        "----------------------------",
                        "□ Move page: /ctag help <page>"
                ))
            } else if (args[1] == "3") {
                sender.sendMessage(arrayOf(
                        "CoordTag Command Manual (Page 3/3)",
                        "Select flag prior to using this command.",
                        "----------------------------",
                        "/ctag list : Show all captures matching the flags.",
                        "/ctag tp : Teleport to the selected tag.",
                        "----------------------------",
                        "□ Move page: /ctag help <page>"
                ))
            }
            return true
        }
        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): MutableList<String>? {
        if (args.isEmpty())
            return command.aliases

        if (args.size == 1)
            return getCompletions(args[0], "help", "select", "create", "capture", "remove", "list")

        return when (args[0].toLowerCase()) {
            "help" -> getCompletions(args[1], "1", "2", "3")
            "select" -> {
                when (args.size % 2) {
                    0 -> getCompletions(args[1], "-game", "-tag", "-map", "-index")
                    1 -> when (args[args.size - 2].toLowerCase()) {
                        "-game" -> getGameTitles(args[args.size - 1])
                        "-type" -> getCompletions("block", "entity")
                        "-tag" -> { // Query available tags under a map
                            val game = getFlag(sender, Flag.GAME) as Game?
                            game?.let { CoordinateTag(it.map, ) }

                            TODO()
                        }
                    }
                }
            }
            else -> TODO()
        }
    }

    private fun getFlag(sender: CommandSender, flag: Flag) : Any? {
        val map = flagState[sender.name] ?: EnumMap(Flag::class.java)
        return map[flag]
    }

    private fun setFlag(sender: CommandSender, flag: Flag, value: Any) {
        val map = flagState[sender.name] ?: EnumMap(Flag::class.java)
        map[flag] = value
        flagState[sender.name] = map
    }
}