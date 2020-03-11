package com.github.lazoyoung.craftgames

import com.github.lazoyoung.craftgames.exception.FaultyConfiguration
import com.github.lazoyoung.craftgames.exception.GameNotFound
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import java.io.BufferedReader
import java.io.FileReader

class GameFactory {
    companion object {
        val runners: MutableList<Game> = ArrayList()

        fun getAvailable(id: String) : Game {
            for (game in runners) {
                if (game.id == id && game.canJoin()) {
                    return game
                }
            }

            TODO()
        }

        fun openNew(id: String) {
            val path = Main.config.getString("games.$id.layout")
                    ?: throw GameNotFound("Game \'$id\' is not defined in config.yml")
            val file = Main.instance.dataFolder.resolve(path)
            val reader: BufferedReader
            val config: YamlConfiguration
            val mapConfig: ConfigurationSection?
            val scriptConfig: ConfigurationSection?

            try {
                if (!file.isFile)
                    throw FaultyConfiguration("Game \'$id\' does not have layout.yml")

                reader = BufferedReader(FileReader(file, Main.charset))
                config = YamlConfiguration.loadConfiguration(reader)
            } catch (e: Exception) {
                e.printStackTrace()
                Main.instance.logger.severe("Failed to read layout.yml")
                return
            }

            mapConfig = config.getConfigurationSection("map")
            scriptConfig = config.getConfigurationSection("script")

            if (mapConfig == null)
                throw FaultyConfiguration("Map is not defined in layout.yml for game: $id")


        }
    }
}