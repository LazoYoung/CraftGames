package com.github.lazoyoung.craftgames.game

import com.github.lazoyoung.craftgames.Main
import com.github.lazoyoung.craftgames.internal.exception.FaultyConfiguration
import com.github.lazoyoung.craftgames.internal.exception.GameNotFound
import org.bukkit.configuration.file.YamlConfiguration
import java.io.BufferedReader
import java.io.IOException
import java.nio.file.InvalidPathException
import java.nio.file.Path

class GameLayout(private val gameName: String) {

    /** Layout defines path to every resource files. **/
    val config: YamlConfiguration

    /** Path to layout.yml **/
    val path: Path

    /** The root folder for every resources in this game **/
    val root: Path

    internal val scriptDir: Path

    internal val kitDir: Path

    internal val lootTableDir: Path?

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

        val scriptRootStr = config.getString("script.directory")
                ?: throw FaultyConfiguration("Script directory is not defined in $path")
        val lootTablePath = config.getString("datapack.loot-tables.directory")
        val kitPath = config.getString("kit.directory")
                ?: throw FaultyConfiguration("Kit directory is not defined in $path")

        scriptDir = root.resolve(scriptRootStr)
        lootTableDir = lootTablePath?.let { root.resolve(it) }
        kitDir = root.resolve(kitPath)
    }

}