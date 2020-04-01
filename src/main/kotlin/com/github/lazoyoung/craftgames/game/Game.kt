package com.github.lazoyoung.craftgames.game

import com.github.lazoyoung.craftgames.Main
import com.github.lazoyoung.craftgames.api.ActionbarTask
import com.github.lazoyoung.craftgames.api.GameResult
import com.github.lazoyoung.craftgames.api.MessageTask
import com.github.lazoyoung.craftgames.event.GameFinishEvent
import com.github.lazoyoung.craftgames.event.GameInitEvent
import com.github.lazoyoung.craftgames.event.PlayerJoinGameEvent
import com.github.lazoyoung.craftgames.event.PlayerLeaveGameEvent
import com.github.lazoyoung.craftgames.game.module.Module
import com.github.lazoyoung.craftgames.game.player.GamePlayer
import com.github.lazoyoung.craftgames.game.player.PlayerData
import com.github.lazoyoung.craftgames.game.player.Spectator
import com.github.lazoyoung.craftgames.internal.exception.FaultyConfiguration
import com.github.lazoyoung.craftgames.internal.exception.GameNotFound
import com.github.lazoyoung.craftgames.internal.exception.MapNotFound
import com.github.lazoyoung.craftgames.internal.util.MessengerUtil
import net.md_5.bungee.api.ChatColor
import net.md_5.bungee.api.chat.ComponentBuilder
import org.bukkit.Bukkit
import org.bukkit.GameMode
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
    enum class Phase { LOBBY, PLAYING, SUSPEND }

    enum class JoinRejection { FULL, IN_GAME, TERMINATING }

    /** The state of game progress **/
    lateinit var phase: Phase

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
            return getMapList(gameName, lobby).map { it.id }
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
            val initEvent = GameInitEvent(game)

            Bukkit.getPluginManager().callEvent(initEvent)

            if (initEvent.isCancelled) {
                game.forceStop(error = true)
                throw RuntimeException("Game failed to init.")
            } else {
                assignID(game)

                if (mapID == null) {
                    game.resource.lobbyMap.generate(game, Consumer {
                        game.updatePhase(Phase.LOBBY)
                        consumer?.accept(game)
                    })
                } else {
                    val map = game.resource.mapRegistry[mapID]
                            ?: throw MapNotFound("Map $mapID does not exist for game: $name.")

                    map.generate(game, Consumer { consumer?.accept(game) })
                }
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
            val label = Main.config.getString("world-label")!!

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

    override fun hashCode(): Int {
        return id
    }

    /**
     * Leave the lobby (if present) and start the game.
     *
     * TODO Exclude players who ain't ready.
     *
     * @param mapID Select which map to play.
     * @param result Consume the generated world.
     * @throws MapNotFound is thrown if map is not found.
     * @throws RuntimeException is thrown if map generation was failed.
     * @throws FaultyConfiguration is thrown if map configuration is not valid.
     */
    fun start(mapID: String?, votes: Int? = null, result: Consumer<Game>? = null) {
        val thisMap = if (mapID == null) {
            resource.getRandomMap()
        } else {
            val map = resource.mapRegistry[mapID]

            if (map == null) {
                forceStop(error = true)
                throw MapNotFound("Map $mapID is not found for game: $name")
            }
            map
        }

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
    }

    fun forceStop(async: Boolean = true, error: Boolean) {
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

        // Fire event
        Bukkit.getPluginManager().callEvent(
                GameFinishEvent(this, GameResult.SUSPENSION, null, null)
        )

        close(async)
    }

    /**
     * This function explains why you can't join this game.
     * @return A [JoinRejection] unless you can join (in that case null is returned).
     */
    fun getRejectCause(): JoinRejection? {
        val service = Module.getGameModule(this)

        return if (!service.canJoinAfterStart && phase == Phase.PLAYING) {
            JoinRejection.IN_GAME
        } else if (players.count() >= service.maxPlayer) {
            JoinRejection.FULL
        } else if (phase == Phase.SUSPEND) {
            JoinRejection.TERMINATING
        } else {
            null
        }
    }

    fun canJoin(): Boolean {
        return getRejectCause() == null
    }

    fun join(player: Player) {
        if (canJoin()) {
            val event = PlayerJoinGameEvent(this, player)
            val uid = player.uniqueId
            val playerData: GamePlayer

            // Fire an event.
            Bukkit.getPluginManager().callEvent(event)

            if (event.isCancelled) {
                player.sendMessage(
                        *ComponentBuilder("Unable to join the game.")
                                .color(ChatColor.YELLOW).create()
                )
                return
            } else {
                playerData = GamePlayer.register(player, this)
            }

            // TODO Restore Module: Config parse exception must be handled if World is not present.
            resource.restoreConfig.set(uid.toString().plus(".location"), player.location)
            players.add(uid)

            if (phase == Phase.LOBBY) {
                Module.getPlayerModule(this).restore(player)
                Module.getLobbyModule(this).join(player)
                Module.getGameModule(this).broadcast("&f${player.displayName} &6joined the game.")
                ActionbarTask(
                        player = player,
                        text = *arrayOf("&aWelcome to &f$name&a!", "&aPlease wait until the game starts.")
                ).start()
            } else if (phase == Phase.PLAYING) {
                Module.getPlayerModule(this).restore(player)
                Module.getGameModule(this).teleportSpawn(playerData)
                player.sendMessage("You joined the ongoing game: $name")
                Module.getGameModule(this).broadcast("&f${player.displayName} &6joined the game.")
                ActionbarTask(
                        player = player,
                        text = *arrayOf("&aWelcome to &f$name&a!", "&aThis game was started a while ago.")
                ).start()
            }
        } else {
            val text = when (getRejectCause()) {
                JoinRejection.TERMINATING -> "The game is terminating."
                JoinRejection.FULL -> "The game is full."
                JoinRejection.IN_GAME -> "The game has already started."
                else -> "Unable to join the game."
            }

            player.sendMessage(*ComponentBuilder(text).color(ChatColor.YELLOW).create())
        }
    }

    fun startSpectate(player: Player) {
        val uid = player.uniqueId
        val playerData = Spectator.register(player, this)

        resource.restoreConfig.set(uid.toString().plus(".location"), player.location)
        when (phase) {
            Phase.LOBBY -> {
                Module.getLobbyModule(this).join(player)
            }
            Phase.PLAYING -> {
                Module.getGameModule(this).teleportSpawn(playerData)
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
        Module.getGameModule(this).teleportSpawn(playerData)
        players.add(uid)
        player.gameMode = GameMode.CREATIVE
        player.sendMessage("You are editing \'${map.id}\' in $name.")
    }

    fun leave(playerData: PlayerData) {
        val player = playerData.player
        val restoreKey = player.uniqueId.toString().plus(".location")
        val uid = player.uniqueId
        val cause = PlayerTeleportEvent.TeleportCause.PLUGIN
        val lobby = Module.getLobbyModule(this)

        // Fire an event.
        Bukkit.getPluginManager().callEvent(PlayerLeaveGameEvent(this, player))

        // Clear data
        MessageTask.clearAll(player)
        ActionbarTask.clearAll(player)
        module.ejectPlayer(playerData)
        players.remove(uid)
        playerData.unregister()

        if (lobby.exitLoc != null) {
            player.teleport(lobby.exitLoc!!, cause)
        } else {
            resource.restoreConfig.getLocation(restoreKey)?.let {
                player.teleport(it, cause)
            }
        }

        if (lobby.exitServer != null) {
            MessengerUtil.request(player, arrayOf("GetServers"), Consumer { servers ->
                if (servers?.split(", ")?.contains(lobby.exitServer!!) == true) {
                    MessengerUtil.request(player, arrayOf("Connect", lobby.exitServer!!))
                }
            })
        }

        player.sendMessage("You left the game.")
    }

    fun getPlayers(): List<Player> {
        return players.mapNotNull { Bukkit.getPlayer(it) }
    }

    /**
     * Terminate the game.
     *
     * @param async Asynchronously destruct the map if true.
     */
    internal fun close(async: Boolean = true) {
        players.mapNotNull { PlayerData.get(it) }.forEach(PlayerData::leaveGame)
        resource.saveToDisk(editMode)
        updatePhase(Phase.SUSPEND)

        if (map.world != null) {
            map.destruct(async)
        }
        purge(this)
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
}