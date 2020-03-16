package com.github.lazoyoung.craftgames.game

import com.github.lazoyoung.craftgames.player.GamePlayer
import com.github.lazoyoung.craftgames.player.PlayerData
import com.github.lazoyoung.craftgames.player.Spectator
import com.github.lazoyoung.craftgames.script.ScriptBase
import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import java.io.File
import java.util.*
import java.util.function.Consumer

class Game(
        internal var id: Int,

        /* File pathname of tagConfig */
        internal val tagFile: File,

        /** Is this game in Edit Mode? **/
        internal var editMode: Boolean,

        val name: String,

        val scriptReg: Map<String, ScriptBase>,

        mapReg: MutableList<Map<*, *>>
) {
    /** CoordTags configuration across all maps. **/
    internal var tagConfig = YamlConfiguration.loadConfiguration(tagFile)

    /** Map Handler **/
    val map = GameMap(this, mapReg)

    /** List of players in any PlayerState **/
    private val players = ArrayList<UUID>()

    /**
     * Start the game.
     *
     * @param mapConsumer Consumes the generated world. (Null if the other map is in use.
     */
    fun start(mapID: String? = null, mapConsumer: Consumer<World?>? = null) : Boolean {
        if (id < 0 || mapID == null)
            throw RuntimeException("Illegal state of Game attributes.")

        map.generate(mapID, mapConsumer)
        // TODO Load other stuff
        return true
    }

    fun stop() : Boolean {
        map.world?.players?.forEach {
            // TODO Module: global lobby spawnpoint
            it.teleport(Bukkit.getWorld("world")!!.spawnLocation)
            it.sendMessage("Returned back to world.")
        }

        if (map.destruct()) {
            GameFactory.purge(this)
            return true
        }
        return false
    }

    fun join(player: Player) {
        players.add(player.uniqueId)
        GamePlayer.register(player, this)
    }

    fun spectate(player: Player) {
        players.add(player.uniqueId)
        Spectator.register(player, this)
    }

    fun leave(player: Player) {
        players.remove(player.uniqueId)
        PlayerData.get(player)!!.unregister()
    }

    fun reloadConfig() {
        tagConfig.load(tagFile)
    }

    fun saveConfig() {
        tagConfig.save(tagFile)
    }
}