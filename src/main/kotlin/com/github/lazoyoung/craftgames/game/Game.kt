package com.github.lazoyoung.craftgames.game

import com.github.lazoyoung.craftgames.Main
import com.github.lazoyoung.craftgames.player.GamePlayer
import com.github.lazoyoung.craftgames.player.PlayerData
import com.github.lazoyoung.craftgames.player.Spectator
import com.github.lazoyoung.craftgames.script.ScriptBase
import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerTeleportEvent
import java.io.File
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.function.Consumer

class Game(
        internal var id: Int,

        /** Is this game in Edit Mode? **/
        internal var editMode: Boolean,

        /* File pathname of tagConfig */
        private val tagFile: File,

        val name: String,

        val scriptReg: Map<String, ScriptBase>,

        mapReg: MutableList<Map<*, *>>
) {
    enum class State { LOBBY, PLAYING, FINISH }

    /** The state of game progress **/
    var state = State.LOBBY

    /** Can players can join at this moment? **/
    var canJoin = true

    /** CoordTags configuration across all maps. **/
    internal var tagConfig = YamlConfiguration.loadConfiguration(tagFile)

    /** Map Handler **/
    val map = GameMap(this, mapReg)

    /** List of players in any PlayerState **/
    private val players = ArrayList<UUID>()

    /**
     * Start the game.
     *
     * @param mapConsumer Consume the generated world.
     */
    fun start(mapID: String? = null, mapConsumer: Consumer<World>? = null) {
        if (id < 0 || mapID == null)
            throw RuntimeException("Illegal state of Game attributes.")

        state = State.PLAYING
        canJoin = false
        map.generate(mapID, mapConsumer)
        // TODO Load other stuff
    }

    fun finish() {

    }

    fun stop() {
        for (player in players.mapNotNull { Bukkit.getPlayer(it) }) {
            player.teleport(Bukkit.getWorld("world")!!.spawnLocation)
        }

        try {
            map.destruct()
        } catch (e: RuntimeException) {
            e.printStackTrace()
        } catch (e: RuntimeException) {
            // We can disregard this
        }
        GameFactory.purge(this)
    }

    fun join(player: Player) {
        // TODO Module: player spawnpoint
        val plugin = Main.instance
        val scheduler = Bukkit.getScheduler()
        val future = player.teleportAsync(map.world!!.spawnLocation, PlayerTeleportEvent.TeleportCause.PLUGIN)

        scheduler.runTaskAsynchronously(plugin, Runnable {
            val succeed = future.get(10L, TimeUnit.SECONDS)

            scheduler.runTask(plugin, Runnable {
                if (succeed) {
                    players.add(player.uniqueId)
                    GamePlayer.register(player, this)
                    player.sendMessage("You joined $name.")
                } else {
                    player.sendMessage("Failed to load world! Please try again later.")
                }
            })
        })
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