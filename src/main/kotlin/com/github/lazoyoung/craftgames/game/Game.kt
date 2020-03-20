package com.github.lazoyoung.craftgames.game

import com.github.lazoyoung.craftgames.Main
import com.github.lazoyoung.craftgames.exception.FaultyConfiguration
import com.github.lazoyoung.craftgames.exception.GameNotFound
import com.github.lazoyoung.craftgames.module.Module
import com.github.lazoyoung.craftgames.player.GamePlayer
import com.github.lazoyoung.craftgames.player.PlayerData
import com.github.lazoyoung.craftgames.player.Spectator
import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerTeleportEvent
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.function.Consumer

class Game(
        val name: String,

        internal var id: Int,

        /** Is this game in Edit Mode? **/
        internal var editMode: Boolean,

        /** Configurable resources **/
        internal val resource: GameResource
) {
    enum class State { LOBBY, PLAYING, FINISH }

    /** The state of game progress **/
    var state = State.LOBBY

    /** Can players can join at this moment? **/
    var canJoin = true

    /** All kind of modules **/
    val module = Module(this)

    /** Map Handler **/
    var map = resource.lobbyMap

    /** List of players in any PlayerState **/
    private val players = ArrayList<UUID>()

    companion object {

        /** Games Registry. (Key: ID of the game) **/
        private val gameRegistry: MutableMap<Int, Game> = HashMap()

        /** Next ID for new game **/
        private var nextID = 0

        /**
         * Find live games with the given filters.
         *
         * @param name The name of the game to find. (Pass null to search everything)
         * @param isEditMode Find the games that are in edit mode. Defaults to false.
         * @return A list of games found by given arguments.
         */
        fun find(name: String? = null, isEditMode: Boolean? = null) : List<Game> {
            return gameRegistry.values.filter {
                (name == null || it.name == name)
                        && (isEditMode == null || it.editMode == isEditMode)
            }
        }

        /**
         * Find the exact live game by id. (Every game has unique id)
         *
         * @param id Instance ID
         */
        fun findByID(id: Int) : Game? {
            return gameRegistry[id]
        }

        fun getMapList(gameName: String): Set<String> {
            return GameResource(gameName).mapRegistry.keys
        }

        /**
         * Make a new game instance with given name.
         *
         * @param name Classifies the type of game
         * @param editMode The game is in editor mode, if true.
         * @param genLobby Determines if lobby must be generated.
         * @param consumer is called when lobby is generated. (Unnecessary if genLobby is false)
         * @return The new game instance.
         * @throws GameNotFound No such game exists with given id.
         * @throws FaultyConfiguration Configuration is not complete.
         * @throws RuntimeException Unexpected issue has arrised.
         */
        fun openNew(name: String, editMode: Boolean, genLobby: Boolean, consumer: Consumer<World>? = null) : Game {
            val resource = GameResource(name)
            val game = Game(name, -1, editMode, resource)

            assignID(game)

            if (genLobby) {
                val map = game.resource.lobbyMap
                map.generate(game, consumer)
            }
            return game
        }

        internal fun purge(game: Game) {
            gameRegistry.remove(game.id)
        }

        internal fun reassignID(game: Game) {
            gameRegistry.remove(game.id)
            assignID(game)
        }

        private fun assignID(game: Game) {
            val label = Main.config.getString("worlds.directory-label")!!

            Bukkit.getWorldContainer().listFiles()?.forEach {
                if (it.isDirectory && it.name.startsWith(label.plus('_'))) {
                    val id = Regex("(_\\d+)").findAll(it.name).last().value.drop(1).toInt()

                    // Prevents possible conflict with an existing world
                    if (id >= nextID) {
                        nextID = id + 1
                    }
                }
            }

            game.id = nextID
            gameRegistry[nextID++] = game
        }
    }

    /**
     * Leave the lobby (if present) and start the game.
     *
     * @param mapConsumer Consume the generated world.
     */
    fun start(mapID: String, mapConsumer: Consumer<World>? = null) {
        if (id < 0 || editMode)
            throw RuntimeException("Illegal state of Game.")

        val plugin = Main.instance
        val scheduler = Bukkit.getScheduler()
        state = State.PLAYING
        canJoin = false

        try {
            val thisMap = resource.mapRegistry[mapID] ?: map
            thisMap.generate(this, Consumer { world ->
                for (uid in players) {
                    val player = Bukkit.getPlayer(uid)!!

                    scheduler.runTaskAsynchronously(plugin, Runnable {
                        val future
                                = player.teleportAsync(thisMap.world!!.spawnLocation, PlayerTeleportEvent.TeleportCause.PLUGIN)
                        val succeed
                                = future.get(10L, TimeUnit.SECONDS)

                        scheduler.runTask(plugin, Runnable {
                            if (succeed) {
                                player.sendMessage("Moved to ${thisMap.alias}")
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
        resource.saveToDisk()

        if (map.world != null) {
            map.destruct()
        }
        purge(this)
    }

    fun join(player: Player) {
        val uid = player.uniqueId
        val playerData = GamePlayer.register(player, this)

        // TODO Config fails to parse location if World is not present.
        resource.restoreConfig.set(uid.toString().plus(".location"), player.location)
        module.spawn.spawnPlayer(map.world!!, playerData)
        players.add(uid)
        player.sendMessage("You joined $name.")
    }

    fun spectate(player: Player) {
        val uid = player.uniqueId
        val playerData = Spectator.register(player, this)

        resource.restoreConfig.set(uid.toString().plus(".location"), player.location)
        module.spawn.spawnPlayer(map.world!!, playerData)
        players.add(uid)
        Spectator.register(player, this)
        player.sendMessage("You are spectating $name.")
    }

    fun edit(playerData: PlayerData) {
        val player = playerData.player
        val uid = player.uniqueId

        resource.restoreConfig.set(uid.toString().plus(".location"), player.location)
        module.spawn.spawnPlayer(map.world!!, playerData)
        players.add(uid)
        player.sendMessage("You are in editor mode on $name-${map.mapID}.")
    }

    fun leave(player: Player) {
        val uid = player.uniqueId

        resource.restoreConfig.getLocation(uid.toString().plus(".location"))?.let {
            player.teleport(it, PlayerTeleportEvent.TeleportCause.PLUGIN)
        }
        players.remove(uid)
        PlayerData.get(player)?.unregister() ?: Main.logger.fine("PlayerData is lost unexpectedly.")
        player.sendMessage("You left $name.")
    }
}