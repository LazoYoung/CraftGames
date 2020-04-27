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
import com.github.lazoyoung.craftgames.internal.util.DatapackUtil
import org.bukkit.Bukkit
import org.bukkit.configuration.file.YamlConfiguration
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * @throws GameNotFound is thrown if game cannot be resolved by [gameName].
 */
class GameResource(val gameName: String) {

    lateinit var script: ScriptBase
    var lobbyMap: GameMap
    val mapRegistry = HashMap<String, GameMap>()
    internal val kitRoot: Path
    internal val kitData = HashMap<String, ByteArray>()
    private val kitFiles = HashMap<String, File>()
    private val namespace = gameName.toLowerCase()
    private val lootTableContainer: Path?

    /** CoordTags configuration for every maps in this game. **/
    internal val tagConfig: YamlConfiguration
    private val tagFile: File

    /** The root folder for every resources in this game **/
    internal val root: Path

    init {
        /*
         * Read layout.yml
         */
        var fileReader: BufferedReader? = null
        val layoutConfig: YamlConfiguration
        val layoutPathname = Main.getConfig()?.getString("games.$gameName.layout")
                ?: throw GameNotFound("Game layout is not defined in config.yml")
        val layoutFile = Main.instance.dataFolder.resolve(layoutPathname)
        val layoutPath = layoutFile.toPath()

        if (layoutPathname.startsWith('_')) {
            throw FaultyConfiguration("Layout path cannot start with underscore character: $layoutPathname")
        }

        try {
            if (!layoutFile.isFile) {
                throw FaultyConfiguration("layout.yml for $gameName is missing.")
            }

            fileReader = layoutFile.bufferedReader(Main.charset)
            root = layoutFile.parentFile.toPath()
            root.toFile().setWritable(true, true)
            layoutConfig = YamlConfiguration.loadConfiguration(fileReader)
        } catch (e: IOException) {
            throw FaultyConfiguration("Unable to read $layoutPath.", e)
        } catch (e: IllegalArgumentException) {
            throw FaultyConfiguration("File is empty: $layoutPath")
        } catch (e: InvalidPathException) {
            throw RuntimeException("Failed to resolve layout path.", e)
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
         * Load coordinate tags, kits, datapack into memory.
         */
        val lootTablePath = layoutConfig.getString("datapack.loot-tables.path")
        val tagPath = layoutConfig.getString("coordinate-tags.file")
                ?: throw FaultyConfiguration("coordinate-tags.file is not defined in $layoutPath.")
        val kitPath = layoutConfig.getString("kit.path")
                ?: throw FaultyConfiguration("Kit must have a path in $layoutPath")

        lootTableContainer = if (lootTablePath != null) {
            root.resolve(lootTablePath)
        } else {
            null
        }
        tagFile = root.resolve(tagPath).toFile()
        tagFile.parentFile?.mkdirs()

        if (!tagFile.isFile && !tagFile.createNewFile())
            throw RuntimeException("Unable to create file: ${tagFile.toPath()}")
        if (tagFile.extension != "yml")
            throw FaultyConfiguration("File extension is illegal: ${tagFile.name} (Replace with .yml)")

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
                Main.logger.warning("Entry \'id\' of map is missing in $layoutPath")
                continue
            }

            if (alias == null)
                alias = mapID

            if (rawPath == null) {
                Main.logger.warning("Entry 'path' of $mapID is missing in $layoutPath")
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
        val scriptPathStr = layoutConfig.getString("script.path")
                ?: throw FaultyConfiguration("Script path is not defined in $layoutPath")
        val scriptMainStr = layoutConfig.getString("script.main")
                ?: throw FaultyConfiguration("Main script path is not defined in $layoutPath")
        val scriptPath = root.resolve(scriptPathStr)
        val scriptMain = scriptPath.resolve(scriptMainStr).toFile()

        try {
            if (!Files.isDirectory(scriptPath)) {
                Files.createDirectory(scriptPath)
            }
            if (!scriptMain.isFile) {
                scriptMain.createNewFile()
            }
        } catch (e: SecurityException) {
            throw RuntimeException("Failed to create script: $scriptPathStr", e)
        }

        try {
            script = ScriptFactory.get(scriptPath, scriptMain)
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

    internal fun loadDatapack(): Boolean {
        try {
            val packName = DatapackUtil.getInternalPackName()
            val packDir = DatapackUtil.getPackDirectory(Bukkit.getWorlds().first(), packName)
                    ?: DatapackUtil.createPack(packName, true)
            val resourceDir = packDir.resolve("data").resolve(namespace)

            // Clone loot tables into datapack.
            if (lootTableContainer != null) {
                val lootTableDir = resourceDir.resolve("loot_tables")

                check(lootTableDir.mkdirs()) {
                    "Failed to create directory."
                }
                Files.newDirectoryStream(lootTableContainer).use {
                    it.forEach { entry ->
                        val entryTarget = lootTableDir.toPath().resolve(lootTableContainer.relativize(entry))
                        Files.copy(entry, entryTarget, StandardCopyOption.REPLACE_EXISTING)
                    }
                }
            }

            Bukkit.reloadData()
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
        return true
    }
}