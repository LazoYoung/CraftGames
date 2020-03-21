package com.github.lazoyoung.craftgames.game

import com.github.lazoyoung.craftgames.Main
import com.github.lazoyoung.craftgames.coordtag.CoordTag
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
import java.util.function.Consumer

class Game(
        val name: String,

        internal var id: Int,

        /** Is this game in Edit Mode? **/
        internal var editMode: Boolean,

        /** Configurable resources **/
        internal val resource: GameResource
) {
    enum class Phase { LOBBY, PLAYING, FINISH, SUSPEND }

    /** The state of game progress **/
    lateinit var phase: Phase

    /** Can players can join at this moment? **/
    var canJoin = true

    /** All kind of modules **/
    val module = Module(this)

    /** Map Handler **/
    var map = resource.lobbyMap

    /** List of players in any PlayerState **/
    internal val players = ArrayList<UUID>()

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

        fun getGameList(): Array<String> {
            return Main.config.getConfigurationSection("games")
                    ?.getKeys(false)?.toTypedArray()
                    ?: emptyArray()
        }

        fun getMapList(gameName: String): Set<String> {
            return GameResource(gameName).mapRegistry.keys
        }

        /**
         * Make a new game instance.
         *
         * @param name Classifies the type of game
         * @param editMode The game is in editor mode, if true.
         * @param genLobby Determines if lobby must be generated.
         * @param consumer Returns the new instance once it's ready.
         * @throws GameNotFound No such game exists with given id.
         * @throws FaultyConfiguration Configuration is not complete.
         * @throws RuntimeException Unexpected issue has arrised.
         */
        fun openNew(name: String, editMode: Boolean, genLobby: Boolean, consumer: Consumer<Game>? = null) {
            val resource = GameResource(name)
            val game = Game(name, -1, editMode, resource)

            assignID(game)
            CoordTag.reload(game)

            if (genLobby) {
                val map = game.resource.lobbyMap
                map.generate(game, Consumer{
                    game.updatePhase(Phase.LOBBY)
                    consumer?.accept(game)
                })
            } else {
                consumer?.accept(game)
            }
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
     * @param mapID Select which map to play.
     * @param mapConsumer Consume the generated world.
     */
    fun start(mapID: String?, mapConsumer: Consumer<World>? = null) {
        if (id < 0 || editMode)
            throw RuntimeException("Illegal state of Game.")

        val plugin = Main.instance
        val scheduler = Bukkit.getScheduler()
        canJoin = false

        try {
            val thisMap = if (mapID == null) {
                resource.getRandomMap()
            } else {
                resource.mapRegistry[mapID]
            }
            assert(thisMap != null)
            thisMap?.generate(this, Consumer { world ->
                val players = this.players.mapNotNull { PlayerData.get(it) }

                module.spawn.spawnPlayer(players.first(), Consumer { succeed ->
                    scheduler.runTask(plugin, Runnable {
                        if (succeed) {
                            players.forEach { module.spawn.spawnPlayer(it) }
                        } else {
                            Main.logger.warning("Failed to teleport in async!")
                            players.forEach { it.player.sendMessage("The game has been terminated with an error.") }
                            stop()
                        }
                    })
                })
                scheduler.runTaskLater(plugin, Runnable {
                    updatePhase(Phase.PLAYING)
                }, 100L)
                mapConsumer?.accept(world)
            })
        } catch (e: RuntimeException) {
            e.printStackTrace()
            plugin.logger.severe("Failed to regenerate map for game: $name")
        }
    }

    fun finish() {
        if (!editMode && phase == Phase.PLAYING) {
            updatePhase(Phase.FINISH)
            /* --- TODO Ceremony period --- */
        }

        stop()
    }

    fun stop(async: Boolean = true) {
        players.mapNotNull { id -> Bukkit.getPlayer(id) }.forEach { leave(it) }
        resource.saveToDisk(saveTag = editMode)
        updatePhase(Phase.SUSPEND)

        if (map.world != null) {
            map.destruct(async)
        }
        purge(this)
    }

    fun join(player: Player) {
        val uid = player.uniqueId
        val playerData = GamePlayer.register(player, this)

        // TODO Restore Module: Config parse exception must be handled if World is not present.
        resource.restoreConfig.set(uid.toString().plus(".location"), player.location)
        module.spawn.spawnPlayer(playerData)
        players.add(uid)
        player.sendMessage("You joined $name.")
    }

    fun spectate(player: Player) {
        val uid = player.uniqueId
        val playerData = Spectator.register(player, this)

        resource.restoreConfig.set(uid.toString().plus(".location"), player.location)
        module.spawn.spawnPlayer(playerData)
        players.add(uid)
        Spectator.register(player, this)
        player.sendMessage("You are spectating $name.")
    }

    fun edit(playerData: PlayerData) {
        val player = playerData.player
        val uid = player.uniqueId

        resource.restoreConfig.set(uid.toString().plus(".location"), player.location)
        module.spawn.spawnPlayer(playerData)
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

    /**
     * Update the GamePhase and Modules.
     *
     * @param phase The new game phase
     */
    internal fun updatePhase(phase: Phase) {
        this.phase = phase
        module.update(phase)
    }
}