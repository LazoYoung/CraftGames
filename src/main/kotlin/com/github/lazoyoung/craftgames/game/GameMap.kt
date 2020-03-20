package com.github.lazoyoung.craftgames.game

import com.github.lazoyoung.craftgames.FileUtil
import com.github.lazoyoung.craftgames.Main
import com.github.lazoyoung.craftgames.exception.FaultyConfiguration
import com.github.lazoyoung.craftgames.player.PlayerData
import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.WorldCreator
import org.bukkit.WorldType
import java.io.IOException
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.util.function.Consumer

class GameMap internal constructor(
        /** ID of selected map **/
        internal var mapID: String,

        /** Alias name to be displayed **/
        internal var alias: String,

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
     * @param callback Consume the generated world.
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

        // Copy map files to world container
        scheduler.runTaskAsynchronously(plugin, Runnable{
            try {
                val targetRoot = container.resolve(repository.fileName)
                FileUtil.cloneFileTree(repository, container)

                if (!targetRoot.toFile().renameTo(container.resolve(worldName).toFile())) {
                    throw RuntimeException("Unable to rename folder ${repository.fileName} to $worldName.")
                }
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

            // Load world and attributes
            scheduler.runTask(plugin, Runnable{
                // Assign worldName so that WorldInitEvent detects the new world.
                this.worldName = worldName
                val gen = WorldCreator(worldName)
                val world: World?

                gen.type(WorldType.FLAT)
                world = gen.createWorld()
                Main.logger.info("World $worldName generated.")

                try {
                    if (world != null) {
                        if (regen) {
                            game.map.world!!.players
                                    .mapNotNull { pid -> PlayerData.get(pid) }
                                    .forEach { game.module.spawn.spawnPlayer(world, it) }
                            game.map.destruct()
                        }

                        world.isAutoSave = false
                        this.world = world
                        this.worldPath = container.resolve(worldName)
                        game.map = this
                    } else {
                        throw RuntimeException("Unable to load world $worldName for ${game.name}")
                    }
                    callback?.accept(world)
                } catch (e: InvalidPathException) {
                    throw RuntimeException(e)
                }
            })
        })
    }

    /**
     * Players need to leave before taking this action.
     *
     * @throws RuntimeException is thrown if plugin fails to unload world.
     * @throws NullPointerException is thrown if world is not initialized.
     */
    internal fun destruct() {
        if (Bukkit.unloadWorld(world!!, false)) {
            FileUtil.deleteFileTree(worldPath!!)
            isGenerated = false
        } else {
            Main.logger.warning("Failed to unload world \'$worldName\' at $worldPath")
        }
    }
}
