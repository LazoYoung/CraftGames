package com.github.lazoyoung.craftgames.game

import com.github.lazoyoung.craftgames.Main
import com.github.lazoyoung.craftgames.api.*
import com.github.lazoyoung.craftgames.api.Timer
import com.github.lazoyoung.craftgames.event.*
import com.github.lazoyoung.craftgames.game.module.Module
import com.github.lazoyoung.craftgames.game.player.GameEditor
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
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerTeleportEvent
import java.util.*
import java.util.function.Consumer
import javax.script.ScriptException

class Game(
        val name: String,

        internal var id: Int,

        /** Is this game in Edit Mode? **/
        internal var editMode: Boolean,

        /** Configurable resources **/
        internal val resource: GameResource
) {
    enum class Phase {
        /** Game is being initialized. **/
        INIT,

        /** Map is being generated. **/
        GENERATE,

        /** Players are waiting for game to start. **/
        LOBBY,

        /** Game-play is in progress. **/
        PLAYING,

        /** Game has finished. Ceremony is in progress. **/
        FINISH,

        /** Game is being terminated. **/
        TERMINATE
    }

    enum class JoinRejection { FULL, IN_GAME, GENERATING, TERMINATING }

    /** List of players (regardless of PlayerState) **/
    internal val players = LinkedList<UUID>()

    /** The state of game progress **/
    var phase = Phase.INIT

    /** All kind of modules **/
    val module = Module(this)

    /** Map Handler **/
    var map = resource.lobbyMap

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
        fun find(name: String? = null, isEditMode: Boolean? = null): List<Game> {
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
        fun findByID(id: Int): Game? {
            return gameRegistry[id]
        }

        /**
         * Find the live game by world.
         *
         * @param world world instance
         * @throws FaultyConfiguration is thrown if world-label is not defined in config.yml
         */
        fun findByWorld(world: World): Game? {
            val label = Main.getConfig()?.getString("world-label")?.plus("_")
                    ?: throw FaultyConfiguration("world-label is not defined in config.yml")
            val worldName = world.name

            return if (worldName.startsWith(label)) {
                findByID(worldName.replace(label, "").toInt())
            } else {
                null
            }
        }

        fun getGameNames(): Array<String> {
            return Main.getConfig()
                    ?.getConfigurationSection("games")
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
         * @throws ScriptException Cannot evaluate script.
         */
        fun openNew(name: String, editMode: Boolean, mapID: String? = null, consumer: Consumer<Game>? = null) {
            val resource = GameResource(name)
            val game = Game(name, -1, editMode, resource)
            val initEvent = GameInitEvent(game)

            try {
                resource.script.execute()
            } catch (e: Exception) {
                resource.script.writeStackTrace(e)
                game.forceStop(error = true)
                throw ScriptException("Cannot evaluate script.")
            }

            Bukkit.getPluginManager().callEvent(initEvent)

            if (initEvent.isCancelled) {
                game.forceStop(error = true)
                throw RuntimeException("Game failed to init.")
            } else {
                game.phase = Phase.GENERATE
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
            val label = Main.getConfig()?.getString("world-label")

            if (label == null) {
                game.forceStop(error = true)
                throw FaultyConfiguration("world-label is not defined in config.yml")
            }

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
     * @param mapID Select which map to play.
     * @param result Consume the generated world.
     * @throws MapNotFound is thrown if map is not found.
     * @throws RuntimeException is thrown if map generation was failed.
     * @throws FaultyConfiguration is thrown if map configuration is not valid.
     */
    fun start(mapID: String?, votes: Int? = null, result: Consumer<Game>? = null) {
        val gameModule = Module.getGameModule(this)
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
            if (votes == null) {
                gameModule.broadcast("${map.alias} is randomly chosen!")
            } else {
                gameModule.broadcast("${map.alias} is chosen! It has received $votes vote(s).")
            }

            map.description.forEach(gameModule::broadcast)
            result?.accept(this)
            updatePhase(Phase.PLAYING)
        })
    }

    /**
     * Force to stop the game.
     *
     * This function is NOT thread-safe.
     */
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

        return if (phase == Phase.INIT || phase == Phase.GENERATE) {
            JoinRejection.GENERATING
        } else if (!service.canJoinAfterStart && phase == Phase.PLAYING) {
            JoinRejection.IN_GAME
        } else if (players.count() >= service.maxPlayer) {
            JoinRejection.FULL
        } else if (phase == Phase.TERMINATE) {
            JoinRejection.TERMINATING
        } else {
            null
        }
    }

    fun canJoin(): Boolean {
        return getRejectCause() == null
    }

    fun joinPlayer(player: Player) {
        if (canJoin()) {
            val event = GameJoinEvent(this, player, PlayerType.PLAYER)
            val postEvent = GameJoinPostEvent(this, player, PlayerType.PLAYER)
            val uid = player.uniqueId
            val playerData: GamePlayer
            val warning = ComponentBuilder("Unable to join the game.").color(ChatColor.YELLOW).create()

            Bukkit.getPluginManager().callEvent(event)

            if (event.isCancelled) {
                player.sendMessage(*warning)
                return
            } else try {
                playerData = GamePlayer.register(player, this)
            } catch (e: RuntimeException) {
                e.printStackTrace()
                player.sendMessage(*warning)
                return
            }

            resource.saveToDisk(false)
            players.add(uid)

            if (phase == Phase.LOBBY) {
                playerData.restore(respawn = false, leave = false)
                Module.getLobbyModule(this).join(player)
                Module.getGameModule(this).broadcast("&f${player.displayName} &6joined the game.")
                ActionbarTask(
                        player = player,
                        text = *arrayOf(
                                "&aWelcome to &f$name&a!",
                                "&aPlease wait until the game starts.",
                                "&6CraftGames &7is developed by &fLazoYoung&7."
                        )
                ).start()
            } else if (phase == Phase.PLAYING) {
                playerData.restore(respawn = true, leave = false)
                Module.getWorldModule(this).teleportSpawn(playerData, null)
                player.sendMessage("You joined the ongoing game: $name")
                Module.getGameModule(this).broadcast("&f${player.displayName} &6joined the game.")
                ActionbarTask(
                        player = player,
                        text = *arrayOf("&aWelcome to &f$name&a!", "&aThis game was started a while ago.")
                ).start()
            }

            Bukkit.getPluginManager().callEvent(postEvent)
        } else {
            val text = when (getRejectCause()) {
                JoinRejection.TERMINATING -> "The game is terminating."
                JoinRejection.FULL -> "The game is full."
                JoinRejection.IN_GAME -> "The game has already started."
                JoinRejection.GENERATING -> "The game is still loading."
                else -> "Unable to join the game."
            }

            player.sendMessage(*ComponentBuilder(text).color(ChatColor.YELLOW).create())
        }
    }

    fun joinSpectator(player: Player) {
        val uid = player.uniqueId
        val playerData: Spectator
        val event = GameJoinEvent(this, player, PlayerType.SPECTATOR)
        val postEvent = GameJoinPostEvent(this, player, PlayerType.SPECTATOR)
        val warning = ComponentBuilder("Unable to join the game.").color(ChatColor.YELLOW).create()

        Bukkit.getPluginManager().callEvent(event)

        if (event.isCancelled) {
            player.sendMessage(*warning)
            return
        } else try {
            playerData = Spectator.register(player, this)
        } catch (e: RuntimeException) {
            e.printStackTrace()
            player.sendMessage(*warning)
            return
        }

        when (phase) {
            Phase.LOBBY -> {
                Module.getLobbyModule(this).join(player)
            }
            Phase.PLAYING -> {
                Module.getWorldModule(this).teleportSpawn(playerData, null)
            }
            else -> return
        }

        players.add(uid)
        playerData.restore(respawn = false, leave = false)
        playerData.updateEditors()
        player.gameMode = GameMode.SPECTATOR
        player.sendMessage("You are now spectating $name.")
        Bukkit.getPluginManager().callEvent(postEvent)
    }

    fun joinEditor(gameEditor: GameEditor) {
        val player = gameEditor.getPlayer()
        val uid = player.uniqueId
        val event = GameJoinEvent(this, player, PlayerType.EDITOR)
        val postEvent = GameJoinPostEvent(this, player, PlayerType.EDITOR)
        val text = if (players.size == 0) {
            "You are now editing '${map.id}\'."
        } else {
            getPlayers().joinToString(
                    prefix = "You are now editing '${map.id}\' with ",
                    postfix = ".",
                    limit = 5
            ) { it.displayName }
        }

        Bukkit.getPluginManager().callEvent(event)

        if (!event.isCancelled) {
            players.add(uid)
            gameEditor.restore(respawn = false, leave = false)
            player.gameMode = GameMode.CREATIVE
            player.sendMessage(text)
            Module.getWorldModule(this).teleportSpawn(gameEditor, null)
            Bukkit.getPluginManager().callEvent(postEvent)
            Bukkit.getScheduler().runTask(Main.instance, Runnable {
                if (player.location.add(0.0, -1.0, 0.0).block.isEmpty) {
                    player.allowFlight = true
                    player.isFlying = true
                }
            })
        }
    }

    internal fun leave(playerData: PlayerData) {
        val player = playerData.getPlayer()
        val uid = player.uniqueId
        val event = GameLeaveEvent(this, player, playerData.getPlayerType())

        // Clear data
        MessageTask.clearAll(player)
        ActionbarTask.clearAll(player)
        module.ejectPlayer(playerData)
        players.remove(uid)

        exit(player)
        player.sendMessage("You left the game.")
        Bukkit.getPluginManager().callEvent(event)
    }

    internal fun exit(player: Player) {
        val cause = PlayerTeleportEvent.TeleportCause.PLUGIN
        val lobby = Module.getLobbyModule(this)

        if (lobby.exitLoc != null) {
            player.teleport(lobby.exitLoc!!, cause)
        } else {
            val fallback = Main.getConfig()?.getConfigurationSection("exit-fallback")
            val fWorld = fallback?.getString("world")
            var world: World? = null
            val loc: Location

            if (fWorld != null) {
                world = Bukkit.getWorld(fWorld)
            }

            if (world != null && fallback != null) {
                val fX = fallback.getDouble("x")
                val fY = fallback.getDouble("y")
                val fZ = fallback.getDouble("z")
                val fYaw = fallback.getDouble("yaw").toFloat()
                val fPitch = fallback.getDouble("pitch").toFloat()

                loc = Location(world, fX, fY, fZ, fYaw, fPitch)
            } else {
                world = Bukkit.getWorlds().filter { it.name != map.worldName }.random()
                loc = world.spawnLocation
            }

            if (!loc.add(0.0, 1.0, 0.0).block.isPassable) {
                loc.y = world!!.getHighestBlockYAt(loc).toDouble()
            }

            player.teleport(loc, cause)
        }

        if (lobby.exitServer != null) {
            MessengerUtil.request(player, arrayOf("GetServers"), Consumer { servers ->
                if (servers?.split(", ")?.contains(lobby.exitServer!!) == true) {
                    MessengerUtil.request(player, arrayOf("Connect", lobby.exitServer!!))
                }
            })
        }
    }

    fun getPlayers(): List<Player> {
        return players.mapNotNull { Bukkit.getPlayer(it) }
    }

    /**
     * Terminate the game.
     *
     * @param async Asynchronously destruct the current map.
     * @param timer The amount of time to wait before termination.
     */
    internal fun close(async: Boolean = true, timer: Timer = Timer(TimeUnit.TICK, 0)) {

        fun terminate() {
            getPlayers().mapNotNull { PlayerData.get(it) }.forEach(PlayerData::leaveGame)
            resource.saveToDisk(editMode)
            purge(this)

            if (map.isGenerated) {
                map.destruct(async)
            }
        }

        if (timer.toTick() > 0L) {
            updatePhase(Phase.FINISH)
            Bukkit.getScheduler().runTaskLater(Main.instance, Runnable {
                updatePhase(Phase.TERMINATE)
                terminate()
            }, timer.toTick())
        } else {
            if (phase != Phase.INIT) {
                updatePhase(Phase.TERMINATE)
            }

            terminate()
        }
    }

    /**
     * Change [Phase] of this game,
     * triggering modules and world to be changed.
     *
     * @param phase The new game phase
     */
    internal fun updatePhase(phase: Phase) {
        this.phase = phase
        module.update()
    }
}