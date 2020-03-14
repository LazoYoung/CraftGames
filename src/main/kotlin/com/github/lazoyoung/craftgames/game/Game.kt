package com.github.lazoyoung.craftgames.game

import com.github.lazoyoung.craftgames.script.ScriptBase
import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.util.function.Consumer

class Game(
        internal var id: Int,
        val name: String,
        val scriptReg: Map<String, ScriptBase>,

        /* File pathname of tagConfig */
        internal val tagFile: File,

        mapReg: MutableList<Map<*, *>>
) {

    /** Serialization data of BlockTags **/
    internal var tagConfig: FileConfiguration = YamlConfiguration.loadConfiguration(tagFile)

    /** Map Handler **/
    val map = GameMap(this, mapReg)

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
        tagConfig.load(tagFile)
    }

    fun saveConfig() {
        tagConfig.save(tagFile)
    }
}