package com.github.lazoyoung.craftgames.game

import com.github.lazoyoung.craftgames.FileUtil
import com.github.lazoyoung.craftgames.Main
import com.github.lazoyoung.craftgames.exception.FaultyConfiguration
import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.WorldCreator
import org.bukkit.WorldType
import org.bukkit.event.player.PlayerTeleportEvent
import java.io.IOException
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.util.function.Consumer

class GameMap internal constructor(
        /** ID of selected map **/
        internal val mapID: String,

        /** Alias name to be displayed **/
        val alias: String,

        /** Description of this map **/
        val description: List<String>,

        /** Whether or not it's the map for lobby **/
        val isLobby: Boolean = false,

        /** Path to the original map folder. **/
        internal val repository: Path
) {

    /** World instance **/
    internal var world: World? = null

    /** Path to world directory **/
    internal var worldPath: Path? = null

    /** Directory name of this world **/
    internal var worldName: String? = null

    private var isGenerated = false

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
        val label = Main.config.getString("world-name")
                    ?: throw FaultyConfiguration("world-name is missing in config.yml")
        val worldName: String

        if (game.map.isGenerated) {
            regen = true
            Main.logger.info("Regenerating world...")
            Game.reassignID(game)
        }
        worldName = StringBuilder(label).append('_').append(game.id).toString()

        // Load world container
        try {
            container = Bukkit.getWorldContainer().toPath()
        } catch (e: InvalidPathException) {
            throw RuntimeException("Failed to convert World container to path.", e)
        }

        // Copy map files to world container (Async)
        scheduler.runTaskAsynchronously(plugin, Runnable{
            try {
                val targetRoot = container.resolve(repository.fileName)
                val newRoot = container.resolve(worldName).toFile()
                FileUtil.cloneFileTree(repository, container)

                if (!targetRoot.toFile().renameTo(newRoot)) {
                    throw RuntimeException("Unable to rename folder ${repository.fileName} to $worldName.")
                }

                // Deal with Bukkit who doesn't like to have duplicated worlds simultaneously.
                newRoot.resolve("uid.dat").delete()
            } catch (e: IllegalArgumentException) {
                if (e.message?.startsWith("source", true) == true) {
                    Main.logger.config("World folder \'$repository\' is missing for ${game.name}. Generating a blank world...")
                } else {
                    throw RuntimeException("$container doesn't seem to be the world container.", e)
                }
            } catch (e: SecurityException) {
                throw RuntimeException("Unable to access map file ($mapID) for ${game.name}.", e)
            } catch (e: IOException) {
                throw RuntimeException("Failed to install map file ($mapID) for ${game.name}.", e)
            } catch (e: UnsupportedOperationException) {
                throw RuntimeException(e)
            }

            // Load world (Sync)
            scheduler.runTask(plugin, Runnable sync@{
                // Assign worldName so that WorldInitEvent detects the new world.
                this.worldName = worldName
                val gen = WorldCreator(worldName)
                val world: World?

                gen.type(WorldType.FLAT)
                world = gen.createWorld()
                Main.logger.info("World $worldName generated.")

                if (world == null)
                    throw RuntimeException("Unable to load world $worldName for ${game.name}")

                val init = {
                    // Feed new instance into the Game
                    world.isAutoSave = false
                    this.isGenerated = true
                    this.world = world
                    this.worldPath = container.resolve(worldName)
                    game.map = this

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

                                players.drop(1).forEach {
                                    it.teleport(world.spawnLocation, PlayerTeleportEvent.TeleportCause.PLUGIN)
                                }

                                scheduler.runTask(plugin, Runnable {
                                    // We're now safe to unload the old world.
                                    game.map.destruct()
                                    init()
                                })
                            })
                            return@sync
                        }
                    }

                    init()
                } catch (e: InvalidPathException) {
                    throw RuntimeException(e)
                }
            })
        })
    }

    /**
     * Players need to leave before taking this action.
     *
     * @param async Determines to unload the world asynchronously or not.
     * @throws RuntimeException is thrown if plugin fails to unload world.
     * @throws NullPointerException is thrown if world is not initialized.
     */
    internal fun destruct(async: Boolean = true) {
        if (Bukkit.unloadWorld(world!!, false)) {
            val run = Runnable{ FileUtil.deleteFileTree(worldPath!!) }

            if (async) {
                Bukkit.getScheduler().runTaskAsynchronously(Main.instance, run)
            } else {
                run.run()
            }
            isGenerated = false
        } else {
            Main.logger.warning("Failed to unload world \'$worldName\' at $worldPath")
        }
    }
}
