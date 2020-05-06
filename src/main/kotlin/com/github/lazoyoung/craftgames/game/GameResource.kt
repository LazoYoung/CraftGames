package com.github.lazoyoung.craftgames.game

import com.github.lazoyoung.craftgames.Main
import com.github.lazoyoung.craftgames.coordtag.tag.CoordTag
import com.github.lazoyoung.craftgames.game.script.GameScript
import com.github.lazoyoung.craftgames.game.script.ScriptFactory
import com.github.lazoyoung.craftgames.internal.exception.FaultyConfiguration
import com.github.lazoyoung.craftgames.internal.exception.GameNotFound
import com.github.lazoyoung.craftgames.internal.exception.MapNotFound
import com.github.lazoyoung.craftgames.internal.exception.ScriptEngineNotFound
import com.github.lazoyoung.craftgames.internal.util.DatapackUtil
import org.bukkit.Bukkit
import java.io.File
import java.io.IOException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * @throws GameNotFound is thrown if game cannot be resolved by [gameName].
 */
class GameResource internal constructor(private val gameName: String) {

    internal val layout = GameLayout(gameName)
    val tagRegistry = CoordTag.Registry(layout)
    val mapRegistry = GameMap.Registry(layout, tagRegistry)
    lateinit var gameScript: GameScript
    internal val kitRoot: Path
    internal val kitData = HashMap<String, ByteArray>()
    private val kitFiles = HashMap<String, File>()
    private val namespace = gameName.toLowerCase()
    private val lootTableContainer: Path?
    internal val scriptRoot: Path

    init {
        /*
         * Load coordinate tags, kits, datapack into memory.
         */
        val lootTablePath = layout.config.getString("datapack.loot-tables.path")
        val kitPath = layout.config.getString("kit.path")
                ?: throw FaultyConfiguration("Kit must have a path in ${layout.path}")

        lootTableContainer = if (lootTablePath != null) {
            layout.root.resolve(lootTablePath)
        } else {
            null
        }
        kitRoot = layout.root.resolve(kitPath)
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

        /*
         * Load scripts from config
         */
        val scriptPathStr = layout.config.getString("script.path")
                ?: throw FaultyConfiguration("Script path is not defined in ${layout.path}")
        val mainScriptStr = layout.config.getString("script.main")
                ?: throw FaultyConfiguration("Main script path is not defined in ${layout.path}")
        scriptRoot = layout.root.resolve(scriptPathStr)
        val mainScript = scriptRoot.resolve(mainScriptStr)

        try {
            try {
                Files.createDirectories(scriptRoot)
            } catch (e: FileAlreadyExistsException) {
                Files.delete(scriptRoot)
                Files.createDirectories(scriptRoot)
            }

            Files.createFile(mainScript)
        } catch (e: SecurityException) {
            throw RuntimeException("Failed to create script: $scriptPathStr", e)
        } catch (e: FileAlreadyExistsException) {}

        try {
            gameScript = ScriptFactory.get(mainScript)
        } catch (e: ScriptEngineNotFound) {
            Main.logger.warning(e.localizedMessage)
        }
    }

    internal fun saveToDisk() {
        try {
            tagRegistry.saveToDisk()
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
            map = mapRegistry.getMaps().filter { !it.isLobby }.random()
        } catch (e: NoSuchElementException) {
            throw MapNotFound("$gameName doesn't have any registered map.")
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

                check(lootTableDir.isDirectory || lootTableDir.mkdirs()) {
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