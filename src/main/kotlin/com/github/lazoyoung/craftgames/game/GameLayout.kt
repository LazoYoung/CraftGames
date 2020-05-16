package com.github.lazoyoung.craftgames.game

import com.github.lazoyoung.craftgames.Main
import com.github.lazoyoung.craftgames.internal.exception.FaultyConfiguration
import com.github.lazoyoung.craftgames.internal.exception.GameNotFound
import org.bukkit.configuration.file.YamlConfiguration
import java.io.BufferedReader
import java.io.IOException
import java.nio.file.InvalidPathException
import java.nio.file.Path

class GameLayout(gameName: String) {

    /** Layout defines path to every resource files. **/
    val config: YamlConfiguration

    /** Path to layout.yml **/
    val path: Path

    /** The root folder for every resources in this game **/
    val root: Path

    internal val scriptDir: Path

    internal val kitDir: Path

    internal val lootTableDir: Path?

    internal val shopkeepersDir: Path

    internal val dataDir: Path

    init {
        var fileReader: BufferedReader? = null
        val layoutPathname = Main.getConfig()?.getString("games.$gameName.layout")
                ?: throw GameNotFound("Game layout is not defined in config.yml")
        val layoutFile = Main.instance.dataFolder.resolve(layoutPathname)
        path = layoutFile.toPath()

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
            config = YamlConfiguration.loadConfiguration(fileReader)
        } catch (e: IOException) {
            throw FaultyConfiguration("Unable to read $path.", e)
        } catch (e: IllegalArgumentException) {
            throw FaultyConfiguration("File is empty: $path")
        } catch (e: InvalidPathException) {
            throw RuntimeException("Failed to resolve layout path.", e)
        } finally {
            try {
                fileReader?.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }

        val dataDir = "_data"
        val scriptDir = config.getString("script.directory")
                ?: throw FaultyConfiguration("Script directory is not defined in $path")
        val lootTableDir = config.getString("datapack.loot-tables.directory")

        if(scriptDir == dataDir || lootTableDir == dataDir) {
            throw FaultyConfiguration("Illegal directory($dataDir) is defined in $path")
        }

        this.dataDir = root.resolve(dataDir)
        this.scriptDir = root.resolve(scriptDir)
        this.lootTableDir = lootTableDir?.let { root.resolve(it) }
        this.kitDir = this.dataDir.resolve("kit")
        this.shopkeepersDir = this.dataDir.resolve("shopkeepers")
    }

}