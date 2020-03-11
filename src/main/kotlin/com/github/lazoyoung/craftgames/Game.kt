package com.github.lazoyoung.craftgames

import com.github.lazoyoung.craftgames.exception.FaultyConfiguration
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
        val scriptList: List<ScriptBase>,
        private val mapList: MutableList<Map<*, *>>
) {
    private var mapLabel: String? = null
    private var mapWorld: World? = null

    fun loadMap(name: String) {
        val iter = mapList.listIterator()

        while (iter.hasNext()) {
            val map = iter.next().toMutableMap()
            val mapID = map["id"] as String
            var pathStr: String? = null
            var mapSource: Path? = null
            val mapTarget: Path

            try { // Load world container
                mapTarget = Bukkit.getWorldContainer().toPath()
            } catch (e: InvalidPathException) {
                throw RuntimeException("Failed to convert World container to path.", e)
            }

            try { // Install map
                pathStr = map["path"] as String
                mapSource = Path.of(pathStr)

                if (mapSource.fileName == null)
                    throw FaultyConfiguration("Illegal path of map $mapID for game $id: $mapSource")

                if (!Files.isRegularFile(mapTarget.resolve(mapSource.fileName).resolve("level.dat"))) {
                    FileUtil(Main.instance.logger).cloneFileTree(mapSource, mapTarget)
                }
            } catch (e: IllegalArgumentException) {
                if (e.message?.startsWith("source", true) == true) {
                    throw FaultyConfiguration("$mapSource is not a directory of map $mapID for game $id", e)
                } else {
                    throw RuntimeException("$mapTarget doesn't seem to be the world container.")
                }
            } catch (e: SecurityException) {
                throw RuntimeException("Unable to access map file ($mapID) for game $id.")
            } catch (e: IOException) {
                throw RuntimeException("Unable to access map file ($mapID) for game $id.")
            } catch (e: InvalidPathException) {
                throw FaultyConfiguration("Unable to locate map $mapID at $pathStr for game $id", e)
            }

            mapWorld = WorldCreator(mapSource.fileName.toString()).createWorld()

            if (mapWorld == null)
                throw RuntimeException("Unable to create world (map $mapID) for game $id")
        }
    }

    fun unloadMap() {
        mapWorld.let {
            if (it != null) {
                Bukkit.unloadWorld(it, false)
            }
        }
        mapWorld = null
        mapLabel = null
    }

    fun canJoin() : Boolean {
        return true
    }

}