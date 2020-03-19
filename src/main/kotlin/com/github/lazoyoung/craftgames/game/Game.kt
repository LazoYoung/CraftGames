package com.github.lazoyoung.craftgames.game

import com.github.lazoyoung.craftgames.Main
import com.github.lazoyoung.craftgames.module.Module
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
import java.nio.file.Path
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.function.Consumer

class Game(
        internal var id: Int,

        /** Is this game in Edit Mode? **/
        internal var editMode: Boolean,

        /** Pathname of layout.yml **/
        internal val contentPath: Path,

        /* Pathname of coordinate-tags.yml */
        private val tagFile: File,

        /* Pathname of players.yml */
        private val restoreFile: File,

        val name: String,

        val scriptReg: Map<String, ScriptBase>,

        mapReg: MutableList<Map<*, *>>,

        lobbyID: String
) {
    enum class State { LOBBY, PLAYING, FINISH }

    /** The state of game progress **/
    var state = State.LOBBY

    /** Can players can join at this moment? **/
    var canJoin = true

    /** Map Handler **/
    val map = GameMap(this, mapReg, lobbyID)

    /** All kind of modules **/
    val module = Module(this)

    /** CoordTags configuration across all maps. **/
    internal var tagConfig = YamlConfiguration.loadConfiguration(tagFile)

    /** Storage config for player inventory and spawnpoint. **/
    internal var restoreConfig = YamlConfiguration.loadConfiguration(restoreFile)

    /** List of players in any PlayerState **/
    private val players = ArrayList<UUID>()

    /**
     * Leave the lobby (if present) and start the game.
     *
     * @param mapConsumer Consume the generated world.
     */
    fun start(mapID: String? = null, mapConsumer: Consumer<World>? = null) {
        if (id < 0 || mapID == null || editMode)
            throw RuntimeException("Illegal state of Game.")

        val plugin = Main.instance
        val scheduler = Bukkit.getScheduler()
        state = State.PLAYING
        canJoin = false

        try {
            val regen = map.world != null

            map.generate(mapID, regen, Consumer { world ->
                for (uid in players) {
                    val player = Bukkit.getPlayer(uid)!!

                    scheduler.runTaskAsynchronously(plugin, Runnable {
                        val future = player.teleportAsync(map.world!!.spawnLocation, PlayerTeleportEvent.TeleportCause.PLUGIN)
                        val succeed = future.get(10L, TimeUnit.SECONDS)

                        scheduler.runTask(plugin, Runnable {
                            if (succeed) {
                                player.sendMessage("Moved to ${map.alias}")
                            } else {
                                player.sendMessage("Failed to teleport in async!")
                            }
                        })
                    })
                }
                mapConsumer?.accept(world)
            })
        } catch (e: RuntimeException) {
            e.printStackTrace()
            plugin.logger.severe("Failed to regenerate map for game: $name")
        }
    }

    fun finish() {
        if (!editMode) {
            state = State.FINISH
            /* --- Ceremony period --- */
        }

        stop()
    }

    fun stop() {
        players.mapNotNull { id -> Bukkit.getPlayer(id) }.forEach { leave(it) }
        saveConfig()

        if (map.world != null) {
            map.destruct()
        }
        GameFactory.purge(this)
    }

    fun join(player: Player) {
        val uid = player.uniqueId

        // TODO Config fails to parse location if World is not present.
        restoreConfig.set(uid.toString().plus(".location"), player.location)
        module.spawn.spawnPersonal(player)
        players.add(uid)
        GamePlayer.register(player, this)
        player.sendMessage("You joined $name.")
    }

    fun spectate(player: Player) {
        val uid = player.uniqueId

        restoreConfig.set(uid.toString().plus(".location"), player.location)
        module.spawn.spawnSpectator(player)
        players.add(uid)
        Spectator.register(player, this)
        player.sendMessage("You are spectating $name.")
    }

    fun edit(player: Player) {
        val uid = player.uniqueId

        restoreConfig.set(uid.toString().plus(".location"), player.location)
        module.spawn.spawnEditor(player)
        players.add(uid)
        player.sendMessage("You are in editor mode on $name-${map.mapID}.")
    }

    fun leave(player: Player) {
        val uid = player.uniqueId

        restoreConfig.getLocation(uid.toString().plus(".location"))?.let {
            player.teleport(it, PlayerTeleportEvent.TeleportCause.PLUGIN)
        }
        players.remove(uid)
        PlayerData.get(player)!!.unregister()
        player.sendMessage("You left $name.")
    }

    fun saveConfig() {
        restoreConfig.save(restoreFile)
        restoreConfig.load(restoreFile)
        tagConfig.save(tagFile)
        tagConfig.load(tagFile)
    }
}