package com.github.lazoyoung.craftgames.command

import com.github.lazoyoung.craftgames.game.Game
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.util.concurrent.ConcurrentHashMap
import java.util.function.BiConsumer

@Deprecated("Replaced by CommandModule integrated with CommandAPI.")
class CustomCommand(name: String) : Command(name) {

    companion object Registry {
        private val map = ConcurrentHashMap<String, CustomCommand>()

        fun get(alias: String): CustomCommand? {
            return map[alias]
        }
    }

    private val handlers = HashMap<Game, BiConsumer<Player, Array<String>>>()
    private var registered = false
    private var knownLabels = ArrayList<String>()

    init {
        check(get(name) == null) {
            "Command $name is already registered."
        }

        val commandMap = Bukkit.getServer().commandMap
        map[name] = this

        if (commandMap.getCommand(name) == null) {
            val fallback = "craftgames"
            val conflict = !commandMap.register(fallback, this)
            registered = true

            if (conflict) {
                knownLabels.add(name)
            }

            knownLabels.add("$fallback:$name")
        }

        Bukkit.getServer().reload()
    }

    // TODO This is not working for players.
    override fun tabComplete(sender: CommandSender, alias: String, args: Array<out String>): List<String> {
        if (sender !is Player || args.isNotEmpty()) {
            return emptyList()
        }

        return listOf(name)
    }

    override fun execute(sender: CommandSender, commandLabel: String, args: Array<out String>): Boolean {
        return true
    }

    internal fun getHandler(game: Game): BiConsumer<Player, Array<String>>? {
        return handlers[game]
    }

    internal fun addHandler(game: Game, handler: BiConsumer<Player, Array<String>>) {
        check(!handlers.containsKey(game)) {
            "Command $name's handler is already registered from game-${game.id}."
        }

        handlers[game] = handler
    }

    internal fun removeHandler(game: Game) {
        check(handlers.containsKey(game)) {
            "Command $name's handler is not registered from game-${game.id}."
        }

        handlers.remove(game)

        if (handlers.isEmpty()) {
            map.remove(name)

            if (registered) {
                unregister()
            }
        }
    }

    private fun unregister() {
        val commandMap = Bukkit.getServer().commandMap

        super.unregister(commandMap)
        knownLabels.forEach {
            commandMap.knownCommands.remove(it)
        }
    }

}