package com.github.lazoyoung.craftgames.game

import com.github.lazoyoung.craftgames.Main
import com.github.lazoyoung.craftgames.exception.FaultyConfiguration
import com.github.lazoyoung.craftgames.exception.GameNotFound
import com.github.lazoyoung.craftgames.script.ScriptBase
import com.github.lazoyoung.craftgames.script.ScriptFactory
import org.bukkit.Bukkit
import org.bukkit.configuration.file.YamlConfiguration
import java.io.BufferedReader
import java.io.FileReader
import java.io.IOException

class GameFactory {
    companion object {
        /** Games Registry. (Key: ID of the game) **/
        private val gameRegistry: MutableMap<Int, Game> = HashMap()

        /** Next ID for new game **/
        private var nextID = 0

        /**
         * Find games with the given filters.
         *
         * @param name The name of the game to find. (Pass null to search everything)
         * @param isEditMode Find the games that are in edit mode. Defaults to false.
         * @return A list of games found by given arguments.
         */
        fun find(name: String? = null, isEditMode: Boolean = false) : List<Game> {
            return gameRegistry.values.filter {
                (name == null || it.name == name) && it.editMode == isEditMode
            }
        }

        /**
         * Returns the running game matching the id. (Each game has its unique id)
         *
         * Note that the games in editor mode have no assigned ID.
         *
         * @param id Instance ID
         */
        fun findByID(id: Int) : Game? {
            return gameRegistry[id]
        }

        /**
         * Make a dummy game with given name.
         * Dummy games cannot generate its map.
         * The purpose is merely to access data underneath it.
         *
         * @param name Classifies the type of game
         * @return The new dummy instance.
         * @throws GameNotFound No such game exists with given id.
         * @throws FaultyConfiguration Configuration is not complete.
         * @throws RuntimeException Unexpected issue has arrised.
         */
        fun getDummy(name: String) : Game {
            if (name.first().isDigit())
                throw FaultyConfiguration("Name should never start with number.")

            val layout: YamlConfiguration
            val scriptRegistry: MutableMap<String, ScriptBase> = HashMap()
            val path = Main.config.getString("games.$name.layout")
                    ?: throw GameNotFound("Game \'$name\' is not defined in config.yml")
            val file = Main.instance.dataFolder.resolve(path)

            try {
                if (!file.isFile)
                    throw FaultyConfiguration("Game \'$name\' does not have layout.yml")

                layout = YamlConfiguration.loadConfiguration(BufferedReader(FileReader(file, Main.charset)))
            } catch (e: IOException) {
                throw FaultyConfiguration("Unable to read ${file.toPath()} for $name. Is it missing?", e)
            } catch (e: IllegalArgumentException) {
                throw FaultyConfiguration("File is empty: ${file.toPath()}")
            }

            val mapRegistry = layout.getMapList("maps")
            val confScripts = layout.getMapList("scripts")
            val mapItr = mapRegistry.listIterator()
            val scriptItr = confScripts.listIterator()

            while (mapItr.hasNext()) {
                val map = mapItr.next().toMutableMap()
                val mapID = map["id"] as String? ?: throw FaultyConfiguration("Entry \'id\' of map is missing in ${file.toPath()}")
                if (!map.containsKey("alias")) {
                    map["alias"] = mapID; mapItr.set(map)
                    layout.set("maps", mapRegistry); layout.save(file)
                }
                if (!map.containsKey("path"))
                    throw FaultyConfiguration("Entry \'path\' of $mapID is missing in ${file.toPath()}")
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

            val tagPath = layout.getString("coordinate-tags.path")
                    ?: throw FaultyConfiguration("coordinate-tags.path is not defined in ${file.toPath()}.")
            val tagFile = Main.instance.dataFolder.resolve(tagPath)
            val game: Game

            if (!tagFile.isFile && !tagFile.createNewFile())
                throw RuntimeException("Unable to create file: ${tagFile.toPath()}")
            if (tagFile.extension != "yml")
                throw FaultyConfiguration("This file has wrong extension: ${tagFile.name} (Rename it to .yml)")

            game = Game(-1, tagFile, false, name, scriptRegistry, mapRegistry)
            return game
        }

        /**
         * Make a new game instance with given name.
         * This goes live and available for players to join.
         *
         * @param name Classifies the type of game
         * @return The new game instance.
         * @throws GameNotFound No such game exists with given id.
         * @throws FaultyConfiguration Configuration is not complete.
         * @throws RuntimeException Unexpected issue has arrised.
         */
        fun openNew(name: String) : Game {
            val game = getDummy(name)
            val label = Main.config.getString("worlds.directory-label")!!

            Bukkit.getWorldContainer().listFiles()?.forEach {
                if (it.isDirectory && it.name.startsWith(label.plus('_'))) {
                    val id = Regex("(_\\d+)").findAll(it.name).last().value.drop(1).toInt()

                    // Prevents possible conflict with an existing folder
                    if (id >= nextID) {
                        nextID = id + 1
                    }
                }
            }
            game.id = nextID
            gameRegistry[nextID++] = game
            return game
        }

        internal fun purge(game: Game) {
            gameRegistry.remove(game.id)
        }
    }
}