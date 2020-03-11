package com.github.lazoyoung.craftgames

import com.github.lazoyoung.craftgames.exception.FaultyConfiguration
import com.github.lazoyoung.craftgames.exception.GameNotFound
import com.github.lazoyoung.craftgames.script.ScriptBase
import com.github.lazoyoung.craftgames.script.ScriptFactory
import org.bukkit.configuration.file.YamlConfiguration
import java.io.BufferedReader
import java.io.FileReader
import java.io.IOException

class GameFactory {
    companion object {
        private val runners: MutableList<Game> = ArrayList()

        /**
         * Find a free game with given id.
         * It silently opens a new game if everything is running full.
         *
         * @param id Classifies the type of game
         * @return A game where players can join at this moment.
         */
        fun getAvailable(id: String) : Game {
            for (game in runners) {
                if (game.id == id && game.canJoin()) {
                    return game
                }
            }
            return openNew(id)
        }

        /**
         * Open a new running game with given id.
         *
         * @param id Classifies the type of game
         * @throws GameNotFound No such game exists with given id.
         * @throws FaultyConfiguration Configuration is not complete.
         * @throws RuntimeException Unexpected issue has arrised.
         */
        fun openNew(id: String) : Game {
            val path = Main.config.getString("games.$id.layout")
                    ?: throw GameNotFound("Game \'$id\' is not defined in config.yml")
            val file = Main.instance.dataFolder.resolve(path)
            val reader: BufferedReader
            val config: YamlConfiguration
            val confMaps: MutableList<Map<*, *>>
            val confScripts: MutableList<Map<*, *>>
            val scriptRegistry: MutableMap<String, ScriptBase> = HashMap()
            val mapItr: MutableListIterator<Map<*, *>>
            val scriptItr: MutableListIterator<Map<*, *>>

            try {
                if (!file.isFile)
                    throw FaultyConfiguration("Game \'$id\' does not have layout.yml")

                reader = BufferedReader(FileReader(file, Main.charset))
                config = YamlConfiguration.loadConfiguration(reader)
            } catch (e: IOException) {
                throw FaultyConfiguration("Unable to read ${file.toPath()} for game $id. Is it missing?", e)
            } catch (e: IllegalArgumentException) {
                throw FaultyConfiguration("File is empty: ${file.toPath()}")
            }

            confMaps = config.getMapList("maps")
            confScripts = config.getMapList("scripts")
            mapItr = confMaps.listIterator()
            scriptItr = confScripts.listIterator()

            while (mapItr.hasNext()) {
                val map = mapItr.next().toMutableMap()
                val mapID = map["id"] as String? ?: throw FaultyConfiguration("Entry \'id\' of map is missing in ${file.toPath()}")
                if (!map.containsKey("alias")) {
                    map["alias"] = mapID; mapItr.set(map)
                    config.set("maps", confMaps); config.save(file)
                }
                if (!map.containsKey("path"))
                    throw FaultyConfiguration("Entry \'path\' of map $id is missing in ${file.toPath()}")
            }

            while (scriptItr.hasNext()) {
                val map = scriptItr.next()
                val scriptID = map["id"] as String? ?: throw FaultyConfiguration("Entry \'id\' of script is missing in ${file.toPath()}")
                val pathStr = map["path"] as String? ?: throw FaultyConfiguration("Entry \'path\' of script $scriptID is missing in ${file.toPath()}")
                val scriptFile = Main.instance.dataFolder.resolve(pathStr)

                try {
                    if (!scriptFile.isFile)
                        throw FaultyConfiguration("Unable to locate the script: $scriptFile")
                } catch (e: SecurityException) {
                    throw RuntimeException("Unable to read script: $scriptFile", e)
                }

                scriptRegistry[scriptID] = ScriptFactory.getInstance(scriptFile, null)
            }

            return Game(id, scriptRegistry, confMaps)
        }
    }
}