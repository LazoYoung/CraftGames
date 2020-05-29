package com.github.lazoyoung.craftgames.impl.game

import com.github.lazoyoung.craftgames.api.tag.coordinate.AreaCapture
import com.github.lazoyoung.craftgames.api.tag.coordinate.SpawnCapture
import com.github.lazoyoung.craftgames.api.tag.coordinate.TagMode
import com.github.lazoyoung.craftgames.impl.Main
import com.github.lazoyoung.craftgames.impl.exception.FaultyConfiguration
import com.github.lazoyoung.craftgames.impl.game.service.WorldModuleService
import com.github.lazoyoung.craftgames.impl.tag.TagRegistry
import com.github.lazoyoung.craftgames.impl.util.FileUtil
import org.bukkit.*
import org.bukkit.event.player.PlayerTeleportEvent
import java.io.IOException
import java.nio.file.*
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer
import kotlin.collections.HashMap
import kotlin.math.floor

class GameMap internal constructor(
        /** ID of this map **/
        internal val id: String,

        /** Display name **/
        val alias: String,

        /** Description of this map **/
        val description: List<String>,

        /** Determines if this map is lobby **/
        val isLobby: Boolean = false,

        /** Key: Tag name, Value: AreaCapture instance **/
        internal val areaRegistry: HashMap<String, List<AreaCapture>> = HashMap(),

        /** Path to original map folder. **/
        internal val directory: Path,

        private val tagRegistry: TagRegistry
) {

    /** World instance **/
    internal var world: World? = null

    /** Path to world directory **/
    internal var worldPath: Path? = null

    /** Directory name of this world **/
    internal var worldName: String? = null

    internal var isGenerated = false

    class Registry internal constructor(layout: GameLayout, tagRegistry: TagRegistry) {

        private val storage: HashMap<String, GameMap>
        private var lobby: String? = null

        constructor(gameName: String) : this(GameLayout(gameName))
        private constructor(layout: GameLayout) : this(layout, TagRegistry(layout))

        init {
            val entryList = layout.config.getMapList("maps")
            val entryIterator = entryList.listIterator()
            storage = HashMap(entryList.size)

            @Suppress("UNCHECKED_CAST")
            while (entryIterator.hasNext()) {
                val directory: Path?
                val entry = entryIterator.next().toMutableMap()
                val mapID = entry["id"] as String?
                var alias = entry["alias"] as String?
                val directoryStr = entry["directory"] as String?
                val isLobby = entry["lobby"] as Boolean? ?: false
                val description: List<String> = when (val descRaw = entry["description"]) {
                    is String -> {
                        listOf(descRaw)
                    }
                    is List<*> -> {
                        descRaw as List<String>
                    }
                    else -> {
                        listOf()
                    }
                }

                if (mapID == null) {
                    Main.logger.warning("Map ID is not defined in ${layout.path}")
                    continue
                }

                if (alias == null)
                    alias = mapID

                if (directoryStr == null) {
                    Main.logger.warning("Map directory is not defined: $mapID in ${layout.path}")
                    continue
                }

                try {
                    directory = layout.root.resolve(directoryStr)
                } catch (e: InvalidPathException) {
                    throw FaultyConfiguration("Unable to locate map directory: $mapID in ${layout.path}", e)
                }

                val areaRegistry = HashMap<String, List<AreaCapture>>()

                tagRegistry.getCoordTags().filter { it.mode == TagMode.AREA }.forEach {
                    areaRegistry[it.name] = it.getCaptures(mapID) as List<AreaCapture>
                }

                val map = GameMap(mapID, alias, description, isLobby, areaRegistry, directory, tagRegistry)
                storage[mapID] = map

                if (isLobby) {
                    lobby = map.id
                }
            }

            if (lobby == null) {
                throw FaultyConfiguration("Lobby map is not defined in ${layout.path}.")
            }
        }

        fun getMaps(): List<GameMap> {
            return storage.values.toList()
        }

        fun getMap(id: String): GameMap? {
            return storage[id]
        }

        fun getMapNames(excludeLobby: Boolean = false): List<String> {
            return getMaps().filterNot { excludeLobby && it.isLobby }.map { it.id }
        }

        fun getLobby(): GameMap {
            return storage.values.first { it.isLobby }
        }
    }

    /**
     * Install a map from repository and generate it in asynchronous thread.
     *
     * @param game The game in which this map generates.
     * @param callback Returns the generated world after the end of process.
     * @throws RuntimeException Failed to generate map for unexpected reason.
     * @throws FaultyConfiguration
     */
    internal fun generate(game: Game, callback: Consumer<World>? = null) {
        var regen = false
        val container: Path
        val plugin = Main.instance
        val scheduler = Bukkit.getScheduler()
        val label = Main.getConfig()?.getString("world-label")

        if (label == null) {
            game.forceStop(error = true)
            throw FaultyConfiguration("world-name is missing in config.yml")
        }

        if (game.map.isGenerated) {
            regen = true
            Main.logger.info("Regenerating world...")
            Game.reassignID(game)
        }

        // Load world container
        try {
            container = Bukkit.getWorldContainer().toPath()
        } catch (e: InvalidPathException) {
            game.forceStop(error = true)
            throw RuntimeException("Failed to convert World container to path.", e)
        }

        val worldName = StringBuilder(label).append('_').append(game.id).toString()

        // Copy world files to container
        scheduler.runTaskAsynchronously(plugin, Runnable {
            when {
                Files.isDirectory(directory) -> {
                    try {
                        val renamed = directory.resolveSibling(worldName)
                        val source = Files.move(
                                directory, renamed,
                                StandardCopyOption.REPLACE_EXISTING
                        )
                        val outcome = container.resolve(worldName)

                        FileUtil.cloneFileTree(source, container).handleAsync {
                            result, t ->

                            if (!result || t != null) {
                                Main.logger.warning("Failed to clone world!")

                                if (t != null) {
                                    throw t
                                } else {
                                    scheduler.runTask(plugin, Runnable {
                                        game.forceStop(error = true)
                                    })
                                    return@handleAsync false
                                }
                            }

                            // Deal with Bukkit as it doesn't like to have replicated worlds.
                            outcome.toFile().resolve("uid.dat").delete()

                            scheduler.runTask(plugin, Runnable {
                                Files.move(
                                        renamed, directory,
                                        StandardCopyOption.ATOMIC_MOVE
                                )
                                loadWorld(worldName, game, container, regen, callback)
                            })
                        }
                    } catch (e: IllegalArgumentException) {
                        if (e.message?.startsWith("source", true) == true) {
                            Main.logger.warning("World folder \'$directory\' inside ${game.name} is missing. Generating blank world...")
                        } else {
                            scheduler.runTask(plugin, Runnable {
                                game.forceStop(error = true)
                            })
                            throw RuntimeException("$container doesn't seem to be the world container.", e)
                        }
                    } catch (e: SecurityException) {
                        game.forceStop(error = true)
                        throw RuntimeException("Unable to access map file ($id) for ${game.name}.", e)
                    } catch (e: IOException) {
                        game.forceStop(error = true)
                        throw RuntimeException("Failed to install map file ($id) for ${game.name}.", e)
                    } catch (e: UnsupportedOperationException) {
                        game.forceStop(error = true)
                        throw RuntimeException(e)
                    } catch (e: AtomicMoveNotSupportedException) {
                        game.forceStop(error = true)
                        throw RuntimeException(e)
                    }
                }
                container.toFile().listFiles()?.firstOrNull { it.name == worldName } != null -> {
                    game.forceStop(error = true)
                    throw FaultyConfiguration("There's an existing map with the same name: $worldName")
                }
                else -> {
                    loadWorld(worldName, game, container, regen, callback)
                }
            }
        })
    }

    /**
     * Erase this world completely.
     *
     * Remaining players are kicked out of the server.
     * Their destination is configuration-dependent.
     *
     * @param async Determines if the task is conducted asynchronously or not.
     * @throws RuntimeException is thrown if the task fails.
     * @throws NullPointerException is thrown if world is not initialized.
     */
    internal fun destruct(async: Boolean = true) {
        world!!.players.forEach { it.kickPlayer("Destructing the world! Please join again.") }

        try {
            if (Bukkit.unloadWorld(world!!, false)) {
                val run = Runnable { FileUtil.deleteFileTree(worldPath!!) }

                if (async) {
                    Bukkit.getScheduler().runTaskAsynchronously(Main.instance, run)
                } else {
                    run.run()
                }
                isGenerated = false
            } else {
                throw RuntimeException("Failed to unload world \'$worldName\' at $worldPath")
            }
        } catch (e: Exception) {
            throw RuntimeException("Failed to unload world \'$worldName\' at $worldPath", e)
        }
    }

    @Suppress("DEPRECATION")
    private fun loadWorld(
            worldName: String,
            game: Game,
            container: Path,
            regen: Boolean,
            callback: Consumer<World>? = null
    ) {
        val scheduler = Bukkit.getScheduler()

        scheduler.runTask(Main.instance, Runnable {
            val gen = WorldCreator(worldName)
            val world: World?
            val legacyMap = game.map
            val worldService = game.getWorldService()

            // Assign worldName so that WorldInitEvent detects new world.
            this.worldName = worldName
            game.map = this
            gen.type(WorldType.FLAT)
            world = gen.createWorld()
            Main.logger.info("World $worldName generated.")

            if (world == null) {
                game.map = legacyMap
                game.forceStop(error = true)
                Main.logger.warning("Unable to load world $worldName for ${game.name}")
                return@Runnable
            }

            // Apply gamerules and difficulty
            worldService.gamerules.forEach { (rule, value) ->
                world.setGameRuleValue(rule, value)
            }
            world.difficulty = worldService.difficulty

            fun init() {
                val futures = LinkedList<CompletableFuture<Chunk>>()

                // Setup world
                world.isAutoSave = false
                world.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true)
                this.isGenerated = true
                this.world = world
                this.worldPath = container.resolve(worldName)

                // Asynchronously load chunks referred by coordinate tags.
                tagRegistry.getCoordTags().forEach {
                    loop@ for (capture in it.getCaptures(id)) {

                        val cursorList: List<Pair<Int, Int>> = when (capture) {
                            is SpawnCapture -> {
                                val xCursor = floor(capture.x / 16).toInt()
                                val zCursor = floor(capture.z / 16).toInt()

                                listOf(Pair(xCursor, zCursor))
                            }
                            is AreaCapture -> {
                                var xCursor = WorldModuleService.getChunkX(capture.x1)
                                var zCursor = WorldModuleService.getChunkZ(capture.z1)
                                val pairs = LinkedList<Pair<Int, Int>>()

                                do {
                                    do {
                                        pairs.add(Pair(xCursor, zCursor))
                                        xCursor++
                                    } while (xCursor * 16 <= capture.x2)

                                    xCursor = WorldModuleService.getChunkX(capture.x1)
                                    zCursor++
                                } while (zCursor * 16 <= capture.z2)

                                pairs
                            }
                            else -> continue@loop
                        }

                        cursorList.forEach { cursor ->
                            futures.add(
                                    world.getChunkAtAsync(cursor.first, cursor.second).exceptionally {
                                        t -> t.printStackTrace()
                                        null
                                    })
                        }
                    }
                }

                // Finish the process. All chunks are loaded.
                CompletableFuture.allOf(*futures.toTypedArray()).thenAccept {
                    futures.forEach {
                        it.join()?.addPluginChunkTicket(Main.instance)
                    }

                    callback?.accept(world)
                }
            }

            try {
                if (!regen) {
                    init()
                } else {
                    val teleportCause = PlayerTeleportEvent.TeleportCause.PLUGIN
                    val teleportFutures = LinkedList<CompletableFuture<Boolean>>()
                    val players = game.players.mapNotNull { Bukkit.getPlayer(it) }

                    if (players.isNotEmpty()) {

                        // Teleport players to new world.
                        players.forEach { player ->
                            teleportFutures.add(
                                    player.teleportAsync(world.spawnLocation, teleportCause)
                                    .exceptionally {
                                        it.printStackTrace()
                                        game.forceStop(error = true)
                                        return@exceptionally null
                                    })
                        }

                        CompletableFuture.allOf(*teleportFutures.toTypedArray()).thenAcceptAsync {
                            scheduler.runTask(Main.instance, Runnable sync@{
                                teleportFutures.forEach {
                                    if (!it.join()) {
                                        game.forceStop(error = true)
                                        error("Failed to teleport players into game.")
                                    }
                                }

                                // We're now safe to unload the old world.
                                legacyMap.destruct()
                                init()
                            })
                        }
                    }
                }
            } catch (e: InvalidPathException) {
                game.forceStop(error = true)
                throw RuntimeException(e)
            }
        })
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as GameMap

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}