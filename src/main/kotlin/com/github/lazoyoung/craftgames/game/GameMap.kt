package com.github.lazoyoung.craftgames.game

import com.github.lazoyoung.craftgames.FileUtil
import com.github.lazoyoung.craftgames.Main
import com.github.lazoyoung.craftgames.exception.FaultyConfiguration
import com.github.lazoyoung.craftgames.exception.MapNotFound
import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.WorldCreator
import java.io.IOException
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.util.function.Consumer

class GameMap internal constructor(
        /** The game associated to this map **/
        internal val game: Game,

        /** List of maps available **/
        internal val mapRegistry: MutableList<Map<*, *>>
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
     * @param mapID ID of the map you want to be loaded
     * @param callback Consume the generated world.
     * @throws MapNotFound
     * @throws RuntimeException
     * @throws FaultyConfiguration
     */
    internal fun generate(mapID: String, callback: Consumer<World>? = null) {
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
            throw MapNotFound("Map $mapID does not exist.")

        // Load world container
        try {
            mapTarget = Bukkit.getWorldContainer().toPath()
        } catch (e: InvalidPathException) {
            throw RuntimeException("Failed to convert World container to path.", e)
        }

        // Resolve installation target
        try {
            mapSource = plugin.dataFolder.resolve(pathStr).toPath()

            if (mapSource == null || mapSource.fileName == null)
                throw FaultyConfiguration("Illegal path of map $thisID for ${game.name}: $pathStr}")
        } catch (e: InvalidPathException) {
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
                    throw FaultyConfiguration("$mapSource is not a directory of map $thisID for ${game.name}", e)
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

            // Create(load) world
            scheduler.runTask(plugin, Runnable{
                this.worldName = worldName  // Assign worldName so that WorldInitEvent listener can detect it.
                val world = WorldCreator(worldName).createWorld()

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
     * @throws RuntimeException is thrown if plugin fails to unload world.
     * @throws NullPointerException is thrown if world is not initialized.
     */
    internal fun destruct() {
        world!!.players.forEach {
            // TODO Module: global lobby spawnpoint
            it.teleport(Bukkit.getWorld("world")!!.spawnLocation)
        }

        if (Bukkit.unloadWorld(world!!, false)) {
            FileUtil(Main.instance.logger).deleteFileTree(worldPath!!)
            worldName = null
            worldPath = null
            world = null
            mapID = null
        } else {
            throw RuntimeException("Failed to unload world ${world!!.name} in game: ${game.name}")
        }
    }
}
