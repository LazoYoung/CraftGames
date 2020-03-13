package com.github.lazoyoung.craftgames.game

import com.github.lazoyoung.craftgames.script.ScriptBase
import org.bukkit.Bukkit
import org.bukkit.World
import java.util.function.Consumer

class Game(
        val id: Int,
        val name: String,
        val scriptReg: Map<String, ScriptBase>
) {
    lateinit var map: GameMap

    fun canJoin() : Boolean {
        return true
    }

    fun start(mapID: String? = null, mapConsumer: Consumer<World?>) : Boolean {
        if (mapID != null) {
            map.generate(mapID, mapConsumer)
        }

        // TODO Load other stuff
        return true
    }

    fun stop() : Boolean {
        map.world?.players?.forEach {
            // TODO Use global lobby module
            it.teleport(Bukkit.getWorld("world")!!.spawnLocation)
            it.sendMessage("Returned back to world.")
        }

        if (map.destruct()) {
            GameFactory.purge(id)
            return true
        }
        return false
    }
}