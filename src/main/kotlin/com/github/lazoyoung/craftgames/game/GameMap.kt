package com.github.lazoyoung.craftgames.game

import com.github.lazoyoung.craftgames.FileUtil
import com.github.lazoyoung.craftgames.Main
import com.github.lazoyoung.craftgames.exception.FaultyConfiguration
import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.WorldCreator
import org.bukkit.WorldType
import java.io.IOException
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.util.function.Consumer

class GameMap internal constructor(
        /** The game associated to this map **/
        internal val game: Game,

        /** List of maps available **/
        internal val mapRegistry: MutableList<Map<*, *>>,

        internal val lobbyID: String
) {
    /** ID of selected map **/
    internal var mapID: String? = null

    /** Alias name to be displayed **/
    internal var alias: String? = null

    /** World instance **/
    internal var world: World? = null

    /** Directory name of this world **/
    internal var worldName: String? = null

    /** Path to world directory **/
    internal var worldPath: Path? = null

    /**
     * Returns ID list of all maps available.
     */
    fun getMapList(): Array<String> {
        return mapRegistry.mapNotNull { it["id"] as String? }.toTypedArray()
    }

    /**
     * Install a map from repository and generate it in asynchronous thread.
     *
     * @param mapID ID of the map you want to be loaded.
     * @param destructOld Whether or not to destruct the old map.
     * @param callback Consume the generated world.
     * @throws RuntimeException Failed to generate map for unexpected reason.
     * @throws FaultyConfiguration
     */
    internal fun generate(mapID: String, destructOld: Boolean, callback: Consumer<World>? = null) {
        var thisID: String? = null
        var pathStr: String? = null
        var alias: String? = null
        val iter = mapRegistry.listIterator()
        val mapSource: Path?
        val mapTarget: Path
        val plugin = Main.instance
        val scheduler = Bukkit.getScheduler()
        val label = Main.config.getString("world-name")
                    ?: throw FaultyConfiguration("world-name is missing in config.yml")
        val worldName = if (game.editMode) {
            StringBuilder(game.id).append('_').append(mapID).toString()
        } else {
            StringBuilder(label).append('_').append(game.id).toString()
        }

        while (iter.hasNext()) {
            val map = iter.next()
            val iterID = map["id"] as String

            if (iterID == mapID) {
                thisID = mapID
                alias = map["alias"] as String
                pathStr = map["path"] as String
                break
            }
        }

        if (thisID == null || pathStr == null)
            throw RuntimeException("Map $mapID is not defined in layout.yml")

        // Load world container
        try {
            mapTarget = Bukkit.getWorldContainer().toPath()
        } catch (e: InvalidPathException) {
            throw RuntimeException("Failed to convert World container to path.", e)
        }

        // Resolve installation target
        try {
            mapSource = game.contentPath.resolve(pathStr)

            if (mapSource == null || mapSource.fileName == null)
                throw FaultyConfiguration("Illegal path of map $thisID for ${game.name}: $pathStr}")
        } catch (e: InvalidPathException) {
            throw FaultyConfiguration("Unable to locate map $thisID at $pathStr for ${game.name}", e)
        } catch (e: NullPointerException) {
            throw FaultyConfiguration("Unable to locate map $thisID at $pathStr for ${game.name}", e)
        }

        // Copy map files to world container
        scheduler.runTaskAsynchronously(plugin, Runnable{
            try {
                val targetRoot = mapTarget.resolve(mapSource.fileName)

                if (!Files.isRegularFile(targetRoot.resolve("level.dat"))) {
                    FileUtil(plugin.logger).cloneFileTree(mapSource, mapTarget)

                    if (!targetRoot.toFile().renameTo(mapTarget.resolve(worldName).toFile()))
                        throw RuntimeException("Unable to rename folder ${mapSource.fileName} to $worldName.")
                }
            } catch (e: IllegalArgumentException) {
                if (e.message?.startsWith("source", true) == true) {
                    Main.logger.config("World folder \'$mapSource\' is missing for ${game.name}. Generating a blank world...")
                } else {
                    throw RuntimeException("$mapTarget doesn't seem to be the world container.", e)
                }
            } catch (e: SecurityException) {
                throw RuntimeException("Unable to access map file ($thisID) for ${game.name}.", e)
            } catch (e: IOException) {
                throw RuntimeException("Unable to access map file ($thisID) for ${game.name}.", e)
            } catch (e: UnsupportedOperationException) {
                throw RuntimeException(e)
            }

            // Load world and attributes
            scheduler.runTask(plugin, Runnable{
                if (destructOld) {
                    // TODO Regenerating process won't work!
                    destruct()
                }

                this.worldName = worldName  // Assign worldName so that WorldInitEvent listener can detect it.
                val gen = WorldCreator(worldName)
                val world: World?

                gen.type(WorldType.FLAT)
                world = gen.createWorld()

                try {
                    if (world == null)
                        throw RuntimeException("Unable to load world $worldName for ${game.name}")
                    else {
                        world.isAutoSave = false
                        this.alias = alias
                        this.world = world
                        this.mapID = mapID
                        this.worldPath = mapTarget.resolve(worldName)
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
            FileUtil(Main.logger).deleteFileTree(worldPath!!)
            worldName = null
            worldPath = null
            world = null
            alias = null
            mapID = null
        } else {
            Main.logger.warning("Failed to unload world \'${world!!.name}\' in game: ${game.name}")
        }
    }
}
