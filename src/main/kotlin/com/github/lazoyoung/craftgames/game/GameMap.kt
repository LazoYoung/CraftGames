package com.github.lazoyoung.craftgames.game

import com.github.lazoyoung.craftgames.Main
import com.github.lazoyoung.craftgames.coordtag.capture.AreaCapture
import com.github.lazoyoung.craftgames.internal.exception.FaultyConfiguration
import com.github.lazoyoung.craftgames.internal.util.FileUtil
import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.WorldCreator
import org.bukkit.WorldType
import org.bukkit.event.player.PlayerTeleportEvent
import java.io.IOException
import java.nio.file.*
import java.util.function.Consumer

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
        internal val repository: Path
) {

    /** World instance **/
    internal var world: World? = null

    /** Path to world directory **/
    internal var worldPath: Path? = null

    /** Directory name of this world **/
    internal var worldName: String? = null

    internal var isGenerated = false

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

        fun loadWorld() {
            scheduler.runTask(plugin, Runnable sync@{
                val gen = WorldCreator(worldName)
                val world: World?
                val legacyMap = game.map

                // Assign worldName so that WorldInitEvent detects the new world.
                this.worldName = worldName
                game.map = this
                gen.type(WorldType.FLAT)
                world = gen.createWorld()
                Main.logger.info("World $worldName generated.")

                if (world == null) {
                    game.map = legacyMap
                    game.forceStop(error = true)
                    throw RuntimeException("Unable to load world $worldName for ${game.name}")
                }

                val init = {
                    world.isAutoSave = false
                    this.isGenerated = true
                    this.world = world
                    this.worldPath = container.resolve(worldName)

                    // Don't forget to callback.
                    callback?.accept(world)
                }

                try {
                    if (regen) {
                        val players = game.players.mapNotNull { Bukkit.getPlayer(it) }

                        if (players.isNotEmpty()) {
                            // Move players to the new world. (Async)
                            scheduler.runTaskAsynchronously(plugin, Runnable {
                                players.first().teleportAsync(world.spawnLocation, PlayerTeleportEvent.TeleportCause.PLUGIN)
                                        .exceptionally { it.printStackTrace(); return@exceptionally null }
                                        .get()

                                scheduler.runTask(plugin, Runnable {
                                    players.drop(1).forEach {
                                        it.teleport(world.spawnLocation, PlayerTeleportEvent.TeleportCause.PLUGIN)
                                    }

                                    scheduler.runTaskLater(plugin, Runnable {
                                        // We're now safe to unload the old world.
                                        legacyMap.destruct()
                                        init()
                                    }, 5L)
                                })
                            })
                            return@sync
                        }
                    }

                    init()
                } catch (e: InvalidPathException) {
                    game.forceStop(error = true)
                    throw RuntimeException(e)
                }
            })
        }

        // Copy world files to container
        scheduler.runTaskAsynchronously(plugin, Runnable {
            when {
                Files.isDirectory(repository) -> {
                    try {
                        val renamed = repository.resolveSibling(worldName)
                        val source = Files.move(
                                repository, renamed,
                                StandardCopyOption.REPLACE_EXISTING
                        )
                        val outcome = container.resolve(worldName)

                        FileUtil.cloneFileTree(source, container)

                        // Deal with Bukkit as it doesn't like to have replicated worlds.
                        outcome.toFile().resolve("uid.dat").delete()

                        scheduler.runTask(plugin, Runnable {
                            Files.move(
                                    renamed, repository,
                                    StandardCopyOption.ATOMIC_MOVE
                            )
                            loadWorld()
                        })
                    } catch (e: IllegalArgumentException) {
                        if (e.message?.startsWith("source", true) == true) {
                            Main.logger.warning("World folder \'$repository\' inside ${game.name} is missing. Generating blank world...")
                        } else {
                            game.forceStop(error = true)
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
                    loadWorld()
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
}
