package com.github.lazoyoung.craftgames

import com.github.lazoyoung.craftgames.script.ScriptBase
import org.bukkit.Bukkit

class Game(
        val id: Int,
        val name: String,
        internal val worldName: String,
        internal val scriptRegistry: Map<String, ScriptBase>,
        internal val mapRegistry: MutableList<Map<*, *>>
) {
    val map = GameMap(this)

    fun canJoin() : Boolean {
        return true
    }

    fun stop() : Boolean {
        map.world?.players?.forEach {
            // TODO Use lobby module
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