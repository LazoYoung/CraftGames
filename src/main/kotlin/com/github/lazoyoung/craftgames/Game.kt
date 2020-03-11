package com.github.lazoyoung.craftgames

import com.github.lazoyoung.craftgames.exception.FaultyConfiguration
import com.github.lazoyoung.craftgames.exception.MapNotFound
import com.github.lazoyoung.craftgames.script.ScriptBase
import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.WorldCreator
import java.io.IOException
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path

class Game(
        val id: String,
        val scriptRegistry: Map<String, ScriptBase>,
        private val mapRegistry: MutableList<Map<*, *>>
) {
    var mapID: String? = null
        private set
    private var mapWorld: World? = null

    /**
     * @param name Name of map to load
     * @return The loaded world. Returns null if map is already loaded.
     * @throws MapNotFound
     * @throws RuntimeException
     * @throws FaultyConfiguration
     */
    fun loadMap(name: String) : World? {
        val iter = mapRegistry.listIterator()
        var pathStr: String? = null
        var mapSource: Path? = null
        val mapTarget: Path

        while (iter.hasNext()) {
            val map: Map<*, *> = iter.next()
            val thisID = map["id"] as String

            if (thisID != name)
                continue

            try { // Load world container
                mapTarget = Bukkit.getWorldContainer().toPath()
            } catch (e: InvalidPathException) {
                throw RuntimeException("Failed to convert World container to path.", e)
            }

            try { // Install map
                pathStr = map["path"] as String?
                mapSource = pathStr?.let { Main.instance.dataFolder.resolve(it).toPath() }

                if (mapSource == null || mapSource.fileName == null)
                    throw FaultyConfiguration("Illegal path of map $thisID for game $id: $pathStr}")
                if (mapSource.fileName.toString() == mapID)
                    return null

                if (!Files.isRegularFile(mapTarget.resolve(mapSource.fileName).resolve("level.dat"))) {
                    FileUtil(Main.instance.logger).cloneFileTree(mapSource, mapTarget)
                }
            } catch (e: IllegalArgumentException) {
                if (e.message?.startsWith("source", true) == true) {
                    throw FaultyConfiguration("$mapSource is not a directory of map $thisID for game $id", e)
                } else {
                    throw RuntimeException("$mapTarget doesn't seem to be the world container.", e)
                }
            } catch (e: SecurityException) {
                throw RuntimeException("Unable to access map file ($thisID) for game $id.", e)
            } catch (e: IOException) {
                throw RuntimeException("Unable to access map file ($thisID) for game $id.", e)
            } catch (e: InvalidPathException) {
                throw FaultyConfiguration("Unable to locate map $thisID at $pathStr for game $id", e)
            }

            val world = WorldCreator(mapSource.fileName.toString()).createWorld()

            if (world == null)
                throw RuntimeException("Unable to create world (map $thisID) for game $id")
            else {
                world.isAutoSave = false
                mapWorld = world
                this.mapID = name
                return world
            }
        }
        throw MapNotFound("Map $name does not exist.")
    }

    fun unloadMap() {
        mapWorld.let {
            if (it != null) {
                Bukkit.unloadWorld(it, false)
            }
        }
        mapWorld = null
        mapID = null
    }

    fun canJoin() : Boolean {
        return true
    }

}