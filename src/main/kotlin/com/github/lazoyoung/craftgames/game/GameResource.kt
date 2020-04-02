package com.github.lazoyoung.craftgames.game

import com.github.lazoyoung.craftgames.Main
import com.github.lazoyoung.craftgames.coordtag.capture.AreaCapture
import com.github.lazoyoung.craftgames.coordtag.tag.CoordTag
import com.github.lazoyoung.craftgames.coordtag.tag.TagMode
import com.github.lazoyoung.craftgames.game.script.ScriptBase
import com.github.lazoyoung.craftgames.game.script.ScriptFactory
import com.github.lazoyoung.craftgames.internal.exception.FaultyConfiguration
import com.github.lazoyoung.craftgames.internal.exception.GameNotFound
import com.github.lazoyoung.craftgames.internal.exception.MapNotFound
import com.github.lazoyoung.craftgames.internal.exception.ScriptEngineNotFound
import com.github.lazoyoung.craftgames.internal.util.FileUtil
import org.bukkit.configuration.file.YamlConfiguration
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path

/**
 * @throws GameNotFound
 */
class GameResource(val gameName: String) {

    lateinit var script: ScriptBase

    var lobbyMap: GameMap

    val mapRegistry = HashMap<String, GameMap>()

    internal val kitData = HashMap<String, ByteArray>()

    internal val kitFiles = HashMap<String, File>()

    /** CoordTags configuration across all maps. **/
    internal val tagConfig: YamlConfiguration

    /** The root folder among all the resources in this game **/
    internal val root: Path

    private val kitRoot: Path

    private val tagFile: File

    init {
        /*
         * Read layout.yml
         */
        var fileReader: BufferedReader? = null
        val layoutFile: File
        val layoutConfig: YamlConfiguration
        val layoutPathStr = Main.getConfig()?.getString("games.$gameName.layout")
                ?: throw GameNotFound("Game \'$gameName\' is not defined in config.yml")
        layoutFile = Main.instance.dataFolder.resolve(layoutPathStr)

        try {
            if (!layoutFile.isFile)
                throw FaultyConfiguration("Game \'$gameName\' does not have layout.yml")

            fileReader = FileUtil.getBufferedReader(layoutFile)
            root = layoutFile.parentFile.toPath()
            layoutConfig = YamlConfiguration.loadConfiguration(fileReader)
        } catch (e: IOException) {
            throw FaultyConfiguration("Unable to read ${layoutFile.toPath()} for $gameName. Is it missing?", e)
        } catch (e: IllegalArgumentException) {
            throw FaultyConfiguration("File is empty: ${layoutFile.toPath()}")
        } catch (e: InvalidPathException) {
            throw RuntimeException("Failed to resolve resource path.", e)
        } finally {
            try {
                fileReader?.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }

        val mapItr = layoutConfig.getMapList("maps").listIterator()
        var lobbyMap: GameMap? = null

        /*
         * Load CoordTags, location, and kit.
         */
        val tagPath = layoutConfig.getString("coordinate-tags.file.path")
                ?: throw FaultyConfiguration("coordinate-tags.file.path is not defined in ${layoutFile.toPath()}.")

        tagFile = root.resolve(tagPath).toFile()
        tagFile.parentFile?.mkdirs()

        val kitPath = layoutConfig.getString("kits.path") ?: throw FaultyConfiguration("Kit must have a path in ${layoutFile.toPath()}")

        if (!tagFile.isFile && !tagFile.createNewFile())
            throw RuntimeException("Unable to create file: ${tagFile.toPath()}")
        if (tagFile.extension != "yml")
            throw FaultyConfiguration("This file has wrong extension: ${tagFile.name} (Rename it to .yml)")

        tagConfig = YamlConfiguration.loadConfiguration(tagFile)
        kitRoot = root.resolve(kitPath)

        kitRoot.toFile().let {
            it.mkdirs()
            it.listFiles()?.forEach { file ->
                if (file.extension != "kit")
                    return@forEach

                val name = file.nameWithoutExtension

                try {
                    kitData[name] = Files.readAllBytes(file.toPath())
                    kitFiles[name] = file
                } catch (e: IOException) {
                    throw RuntimeException("Failed to read kit file.", e)
                }
            }
        }

        // Load tags into memory.
        CoordTag.reload(this)

        /*
         * Load maps from config
         */
        @Suppress("UNCHECKED_CAST")
        while (mapItr.hasNext()) {
            val mutmap = mapItr.next().toMutableMap()
            val mapID = mutmap["id"] as String?
            var alias = mutmap["alias"] as String?  // Subname
            val rawPath = mutmap["path"] as String?
            val repository: Path?  // Path to original map folder
            val lobby = mutmap["lobby"] as Boolean? ?: false

            val description: List<String> = when (val descRaw = mutmap["description"]) {
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
                Main.logger.warning("Entry \'id\' of map is missing in ${layoutFile.toPath()}")
                continue
            }

            if (alias == null)
                alias = mapID

            if (rawPath == null) {
                Main.logger.warning("Entry 'path' of $mapID is missing in ${layoutFile.toPath()}")
                continue
            }

            try {
                repository = root.resolve(rawPath)
            } catch (e: InvalidPathException) {
                throw FaultyConfiguration("Unable to locate path to map '$mapID' for $gameName", e)
            }

            val areaRegistry = HashMap<String, List<AreaCapture>>()

            CoordTag.getAll(gameName).filter { it.mode == TagMode.AREA }.forEach {
                areaRegistry[it.name] = it.getCaptures(mapID) as List<AreaCapture>
            }

            val map = GameMap(mapID, alias, description, lobby, areaRegistry, repository)
            mapRegistry[mapID] = map

            if (lobby) {
                lobbyMap = map
            }
        }

        if (lobbyMap != null) {
            this.lobbyMap = lobbyMap
        } else {
            throw FaultyConfiguration("Game \'$gameName\' doesn't have lobby map.")
        }

        /*
         * Load scripts from config
         */
        val scriptPath = layoutConfig.getString("script.file.path")
                ?: throw FaultyConfiguration("Script path is not defined in ${layoutFile.toPath()}")
        val scriptFile = layoutFile.parentFile!!.resolve(scriptPath)

        try {
            if (!scriptFile.isFile)
                throw FaultyConfiguration("Unable to locate the script: $scriptFile")
        } catch (e: SecurityException) {
            throw RuntimeException("Unable to read script: $scriptFile", e)
        }

        try {
            script = ScriptFactory.get(scriptFile)
        } catch (e: ScriptEngineNotFound) {
            Main.logger.warning(e.localizedMessage)
        }
    }

    internal fun saveToDisk(editMode: Boolean) {
        if (editMode) {
            try {
                tagConfig.save(tagFile)
                kitData.forEach { (name, byteArr) ->
                    var file = kitFiles[name]

                    if (file == null) {
                        file = kitRoot.resolve(name.plus(".kit")).toFile()
                        file.createNewFile()
                    }

                    Files.write(file!!.toPath(), byteArr)
                }
            } catch (e: IOException) {
                e.printStackTrace()
                Main.logger.severe("Failed to save kit data!")
            }
        }
    }

    /**
     * Look for playable maps and get a random element among them.
     * Lobby map is never obtained by using this method.
     *
     * @return A randomly chosen map.
     * @throws MapNotFound if this game doesn't have a map.
     */
    internal fun getRandomMap(): GameMap {
        val map: GameMap
        try {
            map = mapRegistry.filterValues { !it.isLobby }.values.random()
        } catch (e: NoSuchElementException) {
            throw MapNotFound("$gameName doesn't have a map.")
        }
        return map
    }
}