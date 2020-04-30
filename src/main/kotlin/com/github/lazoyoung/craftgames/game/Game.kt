package com.github.lazoyoung.craftgames.game

import com.github.lazoyoung.craftgames.Main
import com.github.lazoyoung.craftgames.api.*
import com.github.lazoyoung.craftgames.api.Timer
import com.github.lazoyoung.craftgames.event.GameInitEvent
import com.github.lazoyoung.craftgames.event.GameJoinEvent
import com.github.lazoyoung.craftgames.event.GameJoinPostEvent
import com.github.lazoyoung.craftgames.event.GameLeaveEvent
import com.github.lazoyoung.craftgames.game.module.*
import com.github.lazoyoung.craftgames.game.player.*
import com.github.lazoyoung.craftgames.internal.exception.FaultyConfiguration
import com.github.lazoyoung.craftgames.internal.exception.GameJoinRejectedException
import com.github.lazoyoung.craftgames.internal.exception.GameNotFound
import com.github.lazoyoung.craftgames.internal.exception.MapNotFound
import com.github.lazoyoung.craftgames.internal.util.LocationUtil
import com.github.lazoyoung.craftgames.internal.util.MessengerUtil
import net.md_5.bungee.api.ChatColor
import net.md_5.bungee.api.chat.ComponentBuilder
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerTeleportEvent
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer
import javax.script.ScriptException
import kotlin.collections.ArrayList
import kotlin.collections.LinkedHashMap

class Game(
        val name: String,
        internal var id: Int,
        internal var editMode: Boolean,
        internal val resource: GameResource
) {
    /** The state of game progress **/
    var phase = GamePhase.INIT

    /** All kind of modules **/
    val module = ModuleService(this)

    /** Current map **/
    var map = resource.lobbyMap

    /** List of players (regardless of PlayerState) **/
    internal val players = LinkedList<UUID>()

    internal val taskList = ArrayList<GameTask>()
    private var taskFailed = false

    init {
        module.registerTasks()
        updatePhase(GamePhase.INIT)
    }

    companion object {

        /** Games Registry. (Key: ID of the game) **/
        private val gameRegistry = LinkedHashMap<Int, Game>()

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
        fun getByID(id: Int): Game? {
            return gameRegistry[id]
        }

        /**
         * Find the live game by world.
         *
         * @param world world instance
         * @throws FaultyConfiguration is thrown if world-label is not defined in config.yml
         */
        fun getByWorld(world: World): Game? {
            val label = Main.getConfig()?.getString("world-label")?.plus("_")
                    ?: throw FaultyConfiguration("world-label is not defined in config.yml")
            val worldName = world.name

            return if (worldName.startsWith(label)) {
                getByID(worldName.replace(label, "").toInt())
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
            val mapInstances = GameResource(gameName)
                    .mapRegistry.values
                    .filter { lobby || !it.isLobby }
                    .toList()

            return mapInstances.map { it.id }
        }

        /**
         * Make a new game instance.
         *
         * If null is passed to mapID, lobby map is chosen and generated.
         *
         * @param name Classifies the type of game
         * @param editMode The game is in editor mode, if true.
         * @param mapID The map in which the game will take place.
         * @throws GameNotFound No such game exists by given [name].
         * @throws MapNotFound No such map exists by given [mapID].
         * @throws FaultyConfiguration Configuration is not complete.
         * @throws RuntimeException Unexpected issue has arrised.
         * @throws ScriptException Cannot evaluate script.
         */
        fun openNew(name: String, editMode: Boolean, mapID: String? = null): Game {
            val resource = GameResource(name)
            val game = Game(name, -1, editMode, resource)
            val postGenerate = Consumer<World> {
                val initEvent = GameInitEvent(game)
                Bukkit.getPluginManager().callEvent(initEvent)

                if (initEvent.isCancelled || !resource.loadDatapack()) {
                    game.forceStop(error = true)
                    throw RuntimeException("Game failed to init.")
                }

                if (editMode) {
                    game.updatePhase(GamePhase.EDIT)
                } else {
                    game.updatePhase(GamePhase.LOBBY)
                }
            }

            try {
                resource.script.execute()
            } catch (e: Exception) {
                resource.script.writeStackTrace(e)
                game.forceStop(error = true)
                throw ScriptException("Cannot evaluate script.")
            }

            game.phase = GamePhase.GENERATE
            assignID(game)

            if (mapID == null) {
                game.resource.lobbyMap.generate(game, postGenerate)
            } else {
                game.map = game.resource.mapRegistry[mapID]
                        ?: throw MapNotFound("Map $mapID does not exist for game: $name.")
                game.map.generate(game, postGenerate)
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
     * Close the lobby and start the game.
     *
     * @param mapID Select which map to play.
     * @param result Consume the generated world.
     * @throws MapNotFound is thrown if map is not found.
     * @throws RuntimeException is thrown if map generation was failed.
     * @throws FaultyConfiguration is thrown if map configuration is not valid.
     * @throws IllegalStateException is thrown if [GamePhase] is not lobby.
     */
    fun start(mapID: String?, votes: Int? = null, result: Consumer<Game>? = null) {
        if (phase != GamePhase.LOBBY) {
            error("Game isn't in lobby phase.")
        }

        val gameModule = getGameService()
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
            updatePhase(GamePhase.PLAYING)
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

        close(async)
    }

    /**
     * This function explains why you can't join this game.
     * @return A [GameJoinRejectedException.Cause] unless you can join (in that case null is returned).
     */
    fun getRejectCause(player: Player): GameJoinRejectedException.Cause? {
        val service = getGameService()
        val joinPerm = Bukkit.getPluginCommand("join")!!.permission!!
        val editPerm = Bukkit.getPluginCommand("game")!!.permission!!

        return if (players.contains(player.uniqueId)) {
            GameJoinRejectedException.Cause.PLAYING_THIS
        } else if (PlayerData.get(player) != null) {
            GameJoinRejectedException.Cause.PLAYING_OTHER
        } else if (!player.hasPermission(joinPerm) || editMode && !player.hasPermission(editPerm)) {
            GameJoinRejectedException.Cause.NO_PERMISSION
        } else if (players.count() >= service.maxPlayer) {
            GameJoinRejectedException.Cause.FULL
        } else {
            when (phase) {
                GamePhase.FINISH, GamePhase.TERMINATE -> {
                    GameJoinRejectedException.Cause.TERMINATING
                }
                GamePhase.INIT, GamePhase.GENERATE, GamePhase.LOBBY -> {
                    null
                }
                GamePhase.PLAYING -> {
                    if (service.canJoinAfterStart) {
                        null
                    } else {
                        GameJoinRejectedException.Cause.GAME_IN_PROGRESS
                    }
                }
                else -> {
                    GameJoinRejectedException.Cause.UNKNOWN
                }
            }
        }
    }

    fun canJoin(player: Player): Boolean {
        return getRejectCause(player) == null
    }

    /**
     * Make the [player] join this game.
     *
     * Caller is responsible for handling the [outcome][CompletableFuture].
     */
    fun joinPlayer(player: Player) {
        val future = CompletableFuture<Boolean>()

        if (!canJoin(player)) {
            future.completeExceptionally(
                    GameJoinRejectedException(player, getRejectCause(player)!!)
            )
        } else {
            val event = GameJoinEvent(this, player, PlayerType.PLAYER)
            val gamePlayer: GamePlayer

            Bukkit.getPluginManager().callEvent(event)

            if (event.isCancelled) {
                return
            } else try {
                gamePlayer = GamePlayer.register(player, this)
            } catch (e: RuntimeException) {
                e.printStackTrace()
                future.completeExceptionally(
                        GameJoinRejectedException(player, GameJoinRejectedException.Cause.ERROR)
                )
                return
            }

            enterGame(gamePlayer, future) {
                resource.saveToDisk(false)
                players.add(player.uniqueId)

                if (phase == GamePhase.LOBBY) {
                    gamePlayer.restore(RestoreMode.JOIN)
                    getGameService().broadcast("&f${player.displayName} &6joined the game.")
                    ActionbarTask(
                            player = player,
                            text = *arrayOf(
                                    "&aWelcome to &f$name&a!",
                                    "&aPlease wait until the game starts.",
                                    "&6CraftGames &7is developed by &fLazoYoung&7."
                            )
                    ).start()
                } else { // PLAYING
                    gamePlayer.restore(RestoreMode.JOIN)
                    gamePlayer.restore(RestoreMode.RESPAWN)
                    player.sendMessage("You joined the ongoing game: $name")
                    getGameService().broadcast("&f${player.displayName} &6joined the game.")
                    ActionbarTask(
                            player = player,
                            text = *arrayOf("&aWelcome to &f$name&a!", "&aThis game was started a while ago.")
                    ).start()
                }
            }
        }

        handleJoinException(future)
    }

    fun joinSpectator(player: Player) {
        val uid = player.uniqueId
        val spectator: Spectator
        val event = GameJoinEvent(this, player, PlayerType.SPECTATOR)
        val future = CompletableFuture<Boolean>()

        Bukkit.getPluginManager().callEvent(event)

        if (event.isCancelled) {
            future.complete(false)
        } else {
            try {
                spectator = Spectator.register(player, this)
            } catch (e: RuntimeException) {
                e.printStackTrace()
                future.completeExceptionally(
                        GameJoinRejectedException(player, GameJoinRejectedException.Cause.ERROR)
                )
                return
            }

            enterGame(spectator, future) {
                players.add(uid)
                spectator.restore(RestoreMode.JOIN)
                player.gameMode = GameMode.SPECTATOR
                player.sendMessage("You are now spectating $name.")
            }
        }

        handleJoinException(future)
    }

    fun joinEditor(gameEditor: GameEditor) {
        val player = gameEditor.getPlayer()
        val uid = player.uniqueId
        val event = GameJoinEvent(this, player, PlayerType.EDITOR)
        val future = CompletableFuture<Boolean>()
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

        if (event.isCancelled) {
            future.complete(false)
        } else {
            enterGame(gameEditor, future) {
                players.add(uid)
                gameEditor.restore(RestoreMode.JOIN)
                gameEditor.updateActionbar()
                player.gameMode = GameMode.CREATIVE
                player.sendMessage(text)
                Bukkit.getScheduler().runTask(Main.instance, Runnable {
                    if (player.location.add(0.0, -1.0, 0.0).block.isEmpty) {
                        player.allowFlight = true
                        player.isFlying = true
                    }
                })
            }
        }

        handleJoinException(future)
    }

    fun getPlayers(): List<Player> {
        return players.mapNotNull { Bukkit.getPlayer(it) }
    }

    fun getGameService(): GameModuleService {
        return module.getGameModule() as GameModuleService
    }

    fun getTeamService(): TeamModuleService {
        return module.getTeamModule() as TeamModuleService
    }

    fun getLobbyService(): LobbyModuleService {
        return module.getLobbyModule() as LobbyModuleService
    }

    fun getPlayerService(): PlayerModuleService {
        return module.getPlayerModule() as PlayerModuleService
    }

    fun getMobService(): MobModuleService {
        return module.getMobModule() as MobModuleService
    }

    fun getScriptService(): ScriptModuleService {
        return module.getScriptModule() as ScriptModuleService
    }

    fun getWorldService(): WorldModuleService {
        return module.getWorldModule() as WorldModuleService
    }

    fun getItemService(): ItemModuleService {
        return module.getItemModule() as ItemModuleService
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
            updatePhase(GamePhase.FINISH)
            Bukkit.getScheduler().runTaskLater(Main.instance, Runnable {
                updatePhase(GamePhase.TERMINATE)
                terminate()
            }, timer.toTick())
        } else {
            if (phase != GamePhase.INIT) {
                updatePhase(GamePhase.TERMINATE)
            }

            terminate()
        }
    }

    internal fun leave(playerData: PlayerData) {
        val player = playerData.getPlayer()
        val uid = player.uniqueId
        val event = GameLeaveEvent(this, player, playerData.getPlayerType())
        val cause = PlayerTeleportEvent.TeleportCause.PLUGIN
        val lobby = getLobbyService()
        val exitLoc = if (lobby.exitLoc != null) {
            lobby.exitLoc!!
        } else {
            LocationUtil.getExitFallback(player.world.name)
        }

        // Clear data
        MessageTask.clearAll(player)
        ActionbarTask.clearAll(player)
        module.ejectPlayer(playerData)
        players.remove(uid)
        player.teleport(exitLoc, cause)

        if (lobby.exitServer != null) {
            MessengerUtil.request(player, arrayOf("GetServers"), Consumer { servers ->
                if (servers?.split(", ")?.contains(lobby.exitServer!!) == true) {
                    MessengerUtil.request(player, arrayOf("Connect", lobby.exitServer!!))
                }
            })
        }

        getGameService().broadcast("&f${player.displayName} &6left the game.")
        player.sendMessage("\u00A76You left game.")
        Bukkit.getPluginManager().callEvent(event)
    }

    internal fun updatePhase(phase: GamePhase) {
        this.phase = phase

        try {
            if (!taskFailed) {
                val iter = taskList.iterator()

                while (iter.hasNext()) {
                    val task = iter.next()

                    if (task.phase.contains(phase)) {
                        task.execute()
                        iter.remove()
                    }
                }
            }
        } catch (e: Exception) {
            // Prevent possible infinite loop.
            taskFailed = true
            e.printStackTrace()
            forceStop(error = true)
        }
    }

    private fun enterGame(playerData: PlayerData, future: CompletableFuture<Boolean>, postLogic: () -> Unit) {
        val player = playerData.getPlayer()
        val event = GameJoinPostEvent(this, player, playerData.getPlayerType())
        val actionbar = ActionbarTask(
                player = player,
                repeat = true,
                period = Timer(TimeUnit.SECOND, 2),
                text = *arrayOf("&6Loading game...", "&ePlease wait for a moment.")
        ).start()

        fun refinedPostLogic() {
            // Teleport player
            when (phase) {
                GamePhase.LOBBY -> {
                    getLobbyService().teleportSpawn(player)
                }
                GamePhase.PLAYING, GamePhase.EDIT -> {
                    getWorldService().teleportSpawn(playerData, null)
                }
                else -> error("Illegal GamePhase.")
            }

            // Do post logic in respect to PlayerType.
            postLogic()
            actionbar.clear()
            Bukkit.getPluginManager().callEvent(event)
        }

        when (phase) {
            GamePhase.LOBBY, GamePhase.PLAYING -> {
                refinedPostLogic()
            }
            GamePhase.INIT, GamePhase.GENERATE -> {
                // Wait until the game becomes ready.
                GameTask(this, GamePhase.EDIT, GamePhase.LOBBY, GamePhase.PLAYING)
                        .schedule {
                            refinedPostLogic()
                        }
            }
            else -> {
                future.completeExceptionally(
                        GameJoinRejectedException(player, GameJoinRejectedException.Cause.ERROR, playerData)
                )
            }
        }

        future.complete(true)
    }

    private fun handleJoinException(future: CompletableFuture<Boolean>) {
        future.handleAsync { result, t ->
            if (t != null || !result) {
                if (t is GameJoinRejectedException) {
                    Bukkit.getScheduler().runTask(Main.instance, Runnable {
                        t.informPlayer()
                    })

                    if (t.rejectCause == GameJoinRejectedException.Cause.ERROR) {
                        t.printStackTrace()
                    }
                } else {
                    t?.printStackTrace()
                }
            }
        }
    }
}