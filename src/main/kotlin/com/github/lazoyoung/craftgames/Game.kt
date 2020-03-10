package com.github.lazoyoung.craftgames

import com.github.lazoyoung.craftgames.script.ScriptBase
import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.WorldCreator

class Game(
        val id: String,
        val scriptList: List<ScriptBase>
) {
    var mapLabel: String? = null
        private set
    var map: World? = null
        private set

    fun loadMap(name: String) {
        map = WorldCreator(name).createWorld()
        map.let { it?.isAutoSave = false }
    }

    fun unloadMap() {
        map.let {
            if (it != null) {
                Bukkit.unloadWorld(it, false)
            }
        }
        map = null
        mapLabel = null
    }

    fun canJoin() : Boolean {
        return true
    }

}