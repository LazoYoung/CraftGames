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

class GameMap(private val game: Game) {
    var name: String? = null
    internal var world: World? = null
    private var worldPath: Path? = null
    private val worldName = game.worldName

    /**
     * Load the map by given name in asynchronous thread.
     *
     * @param name Name of map to load
     * @param callback Function consuming the loaded world (is null if it's already loaded)
     * @throws MapNotFound
     * @throws RuntimeException
     * @throws FaultyConfiguration
     */
    fun load(name: String, callback: Consumer<World?>) {
        val iter = game.mapRegistry.listIterator()
        var thisID: String? = null
        var pathStr: String? = null
        val mapSource: Path?
        val mapTarget: Path
        val scheduler = Bukkit.getScheduler()

        while (iter.hasNext()) {
            val map = iter.next()
            val id = map["id"] as String

            if (id == name) {
                thisID = id
                pathStr = map["path"] as String
                break
            }
        }

        if (thisID == null || pathStr == null)
            throw MapNotFound("Map $name does not exist.")

        // Load world container
        try {
            mapTarget = Bukkit.getWorldContainer().toPath()
        } catch (e: InvalidPathException) {
            throw RuntimeException("Failed to convert World container to path.", e)
        }

        // Install world
        try {
            mapSource = Main.instance.dataFolder.resolve(pathStr).toPath()

            if (mapSource == null || mapSource.fileName == null)
                throw FaultyConfiguration("Illegal path of map $thisID for ${game.name}: $pathStr}")
            if (mapSource.fileName.toString() == this.name) {
                callback.accept(null)
                return
            }
        } catch (e: InvalidPathException) {
            throw FaultyConfiguration("Unable to locate map $thisID at $pathStr for ${game.name}", e)
        }

        scheduler.runTaskAsynchronously(Main.instance, Runnable{
            try {
                val targetRoot = mapTarget.resolve(mapSource.fileName)

                if (!Files.isRegularFile(targetRoot.resolve("level.dat"))) {
                    FileUtil(Main.instance.logger).cloneFileTree(mapSource, mapTarget)

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

            // Load world
            scheduler.runTask(Main.instance, Runnable{
                val world = WorldCreator(worldName).createWorld()

                try {
                    if (world == null)
                        throw RuntimeException("Unable to load world $worldName for ${game.name}")
                    else {
                        world.keepSpawnInMemory = false
                        world.isAutoSave = false
                        this.world = world
                        this.name = name
                        this.worldPath = mapTarget.resolve(worldName)
                    }
                    callback.accept(world)
                } catch (e: InvalidPathException) {
                    throw RuntimeException(e)
                }
            })
        })
    }

    /**
     * @return whether or not it succeed to destruct the map
     */
    fun destruct() : Boolean {
        world.let {
            if (it != null) {
                try {
                    if (Bukkit.unloadWorld(it, false)) {
                        FileUtil(Main.instance.logger).deleteFileTree(worldPath!!)
                        worldPath = null
                        world = null
                        name = null
                        return true
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        return false
    }
}
