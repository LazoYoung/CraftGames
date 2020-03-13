package com.github.lazoyoung.craftgames.game

import com.github.lazoyoung.craftgames.script.ScriptBase
import org.bukkit.Bukkit
import org.bukkit.World
import java.io.File
import java.util.function.Consumer

class Game(
        val id: Int,
        val name: String,
        val scriptReg: Map<String, ScriptBase>,
        tagFile: File,
        mapReg: MutableList<Map<*, *>>
) {
    val map = GameMap(this, tagFile, mapReg)

    fun canJoin() : Boolean {
        return true
    }

    fun start(mapID: String? = null, mapConsumer: Consumer<World?>? = null) : Boolean {
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

    fun reloadConfig() {
        map.reloadConfig()
    }

    fun saveConfig() {
        map.saveConfig()
    }
}