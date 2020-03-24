package com.github.lazoyoung.craftgames.game

import com.github.lazoyoung.craftgames.Main
import com.github.lazoyoung.craftgames.coordtag.CoordTag
import com.github.lazoyoung.craftgames.exception.FaultyConfiguration
import com.github.lazoyoung.craftgames.exception.GameNotFound
import com.github.lazoyoung.craftgames.exception.MapNotFound
import com.github.lazoyoung.craftgames.module.Module
import com.github.lazoyoung.craftgames.player.GamePlayer
import com.github.lazoyoung.craftgames.player.PlayerData
import com.github.lazoyoung.craftgames.player.Spectator
import net.md_5.bungee.api.ChatColor
import net.md_5.bungee.api.chat.ComponentBuilder
import org.bukkit.Bukkit
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

    /** List of players (regardless of PlayerState) **/
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

        fun getGameNames(): Array<String> {
            return Main.config.getConfigurationSection("games")
                    ?.getKeys(false)?.toTypedArray()
                    ?: emptyArray()
        }

        fun getMapNames(gameName: String, lobby: Boolean = true): List<String> {
            return getMapList(gameName, lobby).map { it.mapID }
        }

        /**
         * Get a list of maps exist in the given game.
         *
         * @param gameName represents the game where you seek for the maps.
         * @param lobby whether or not the lobby map is included as well.
         * @return The instances of GameMaps inside the game.
         */
        fun getMapList(gameName: String, lobby: Boolean = true): List<GameMap> {
            return GameResource(gameName).mapRegistry.values.filter { lobby || !it.isLobby }.toList()
        }

        /**
         * Make a new game instance.
         *
         * If null is passed to mapID, lobby map is chosen and generated.
         * Otherwise, lobby timer is skipped and the game starts immediately.
         *
         * @param name Classifies the type of game
         * @param editMode The game is in editor mode, if true.
         * @param mapID The map in which the game will take place.
         * @param consumer Returns the new instance once it's ready.
         * @throws GameNotFound No such game exists with given id.
         * @throws MapNotFound No such map exists in the game.
         * @throws FaultyConfiguration Configuration is not complete.
         * @throws RuntimeException Unexpected issue has arrised.
         */
        fun openNew(name: String, editMode: Boolean, mapID: String? = null, consumer: Consumer<Game>? = null) {
            val resource = GameResource(name)
            val game = Game(name, -1, editMode, resource)

            assignID(game)
            CoordTag.reload(game)

            if (mapID == null) {
                game.resource.lobbyMap.generate(game, Consumer {
                    game.updatePhase(Phase.LOBBY)
                    consumer?.accept(game)
                })
            } else try {
                game.start(mapID, result = Consumer {
                    consumer?.accept(game)
                })
            } catch (e: MapNotFound) {
                game.stop(async = false, error = true)
                e.printStackTrace()
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

    override fun equals(other: Any?): Boolean {
        return if (other is Game) {
            this.id == other.id
        } else {
            false
        }
    }

    /**
     * Leave the lobby (if present) and start the game.
     *
     * TODO Exclude players who ain't ready.
     *
     * @param mapID Select which map to play.
     * @param result Consume the generated world.
     * @throws MapNotFound is thrown if map is not found.
     */
    fun start(mapID: String?, votes: Int? = null, result: Consumer<Game>? = null) {
        val plugin = Main.instance
        canJoin = false

        try {
            val thisMap = (if (mapID == null) {
                resource.getRandomMap()
            } else {
                resource.mapRegistry[mapID] ?: throw MapNotFound("Map isn't defined in game: $name")
            })

            thisMap.generate(this, Consumer {
                getPlayers().forEach { player ->
                    if (votes == null) {
                        player.sendMessage("${map.alias} is randomly chosen!")
                    } else {
                        player.sendMessage("${map.alias} is chosen! It has received $votes vote(s).")
                    }
                }
                result?.accept(this)
                updatePhase(Phase.PLAYING)
            })
        } catch (e: RuntimeException) {
            e.printStackTrace()
            plugin.logger.severe("Failed to regenerate map for game: $name")
        }
    }

    // TODO Migrate to GameModule (internal code)
    fun finish() {
        if (!editMode && phase == Phase.PLAYING) {
            updatePhase(Phase.FINISH)
            /* TODO Ceremony period */
        }

        close()
    }

    fun stop(async: Boolean = true, error: Boolean) {
        getPlayers().forEach {
            if (error) {
                it.sendMessage(
                        *ComponentBuilder("The game is terminated due to error!")
                                .color(ChatColor.RED).create()
                )
            } else {
                it.sendMessage(
                        *ComponentBuilder("The game is terminated by administrator!")
                                .color(ChatColor.YELLOW).create()
                )
            }
        }

        close(async)
    }

    fun join(player: Player) {
        val uid = player.uniqueId
        val playerData = GamePlayer.register(player, this)

        // TODO Restore Module: Config parse exception must be handled if World is not present.
        resource.restoreConfig.set(uid.toString().plus(".location"), player.location)

        when (phase) {
            Phase.LOBBY -> {
                module.lobbyModule.teleport(player)
                player.sendMessage("You joined the game: $name")
            }
            Phase.PLAYING -> {
                module.spawnModule.teleport(playerData)
                player.sendMessage("You joined the ongoing game: $name")
            }
            else -> {
                player.sendMessage("This game is terminating soon.")
                return
            }
        }
        players.add(uid)
    }

    fun startSpectate(player: Player) {
        val uid = player.uniqueId
        val playerData = Spectator.register(player, this)

        resource.restoreConfig.set(uid.toString().plus(".location"), player.location)
        when (phase) {
            Phase.LOBBY -> {
                module.lobbyModule.teleport(player)
            }
            Phase.PLAYING -> {
                module.spawnModule.teleport(playerData)
            }
            else -> return
        }
        players.add(uid)
        player.sendMessage("You are spectating $name.")
    }

    fun startEdit(playerData: PlayerData) {
        val player = playerData.player
        val uid = player.uniqueId

        resource.restoreConfig.set(uid.toString().plus(".location"), player.location)
        module.spawnModule.teleport(playerData)
        players.add(uid)
        player.sendMessage("You are editing \'${map.mapID}\' in $name.")
    }

    fun leave(player: Player) {
        val uid = player.uniqueId

        resource.restoreConfig.getLocation(uid.toString().plus(".location"))?.let {
            player.teleport(it, PlayerTeleportEvent.TeleportCause.PLUGIN)
        }
        players.remove(uid)
        PlayerData.get(player)?.unregister() ?: Main.logger.fine("PlayerData is lost unexpectedly.")
        player.sendMessage("You left the game.")
    }

    fun getPlayers(): List<Player> {
        return players.mapNotNull { Bukkit.getPlayer(it) }
    }

    /**
     * Update the GamePhase, resulting in the changes of World and Modules.
     *
     * @param phase The new game phase
     */
    internal fun updatePhase(phase: Phase) {
        this.phase = phase
        module.update()
    }

    internal fun close(async: Boolean = true) {
        getPlayers().forEach { leave(it) }
        resource.saveToDisk(saveTag = editMode)
        updatePhase(Phase.SUSPEND)

        if (map.world != null) {
            map.destruct(async)
        }
        purge(this)
    }
}