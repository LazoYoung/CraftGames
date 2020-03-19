package com.github.lazoyoung.craftgames.game

import com.github.lazoyoung.craftgames.Main
import com.github.lazoyoung.craftgames.exception.FaultyConfiguration
import com.github.lazoyoung.craftgames.exception.GameNotFound
import com.github.lazoyoung.craftgames.exception.ScriptEngineNotFound
import com.github.lazoyoung.craftgames.script.ScriptBase
import com.github.lazoyoung.craftgames.script.ScriptFactory
import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.configuration.file.YamlConfiguration
import java.io.BufferedReader
import java.io.FileReader
import java.io.IOException
import java.util.function.Consumer

class GameFactory {
    companion object {
        /** Games Registry. (Key: ID of the game) **/
        private val gameRegistry: MutableMap<Int, Game> = HashMap()

        /** Next ID for new game **/
        private var nextID = 0

        /**
         * Find live games with the given filters.
         *
         * @param name The name of the game to find. (Pass null to search everything)
         * @param isEditMode Find the games that are in edit mode. Defaults to false.
         * @return A list of games found by given arguments.
         */
        fun find(name: String? = null, isEditMode: Boolean? = null) : List<Game> {
            return gameRegistry.values.filter {
                (name == null || it.name == name)
                        && (isEditMode == null || it.editMode == isEditMode)
            }
        }

        /**
         * Find the exact live game by id. (Every game has unique id)
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
            val layoutPathStr = Main.config.getString("games.$name.layout")
                    ?: throw GameNotFound("Game \'$name\' is not defined in config.yml")
            val layoutFile = Main.instance.dataFolder.resolve(layoutPathStr)

            try {
                if (!layoutFile.isFile)
                    throw FaultyConfiguration("Game \'$name\' does not have layout.yml")

                layout = YamlConfiguration.loadConfiguration(BufferedReader(FileReader(layoutFile, Main.charset)))
            } catch (e: IOException) {
                throw FaultyConfiguration("Unable to read ${layoutFile.toPath()} for $name. Is it missing?", e)
            } catch (e: IllegalArgumentException) {
                throw FaultyConfiguration("File is empty: ${layoutFile.toPath()}")
            }


            val mapRegistry = layout.getMapList("maps")
            val scriptRegistry: MutableMap<String, ScriptBase> = HashMap()
            val mapItr = mapRegistry.listIterator()
            val scriptItr = layout.getMapList("scripts").listIterator()
            var lobby: String? = null

            while (mapItr.hasNext()) {
                val map = mapItr.next().toMutableMap()
                val mapID = map["id"] as String? ?: throw FaultyConfiguration("Entry \'id\' of map is missing in ${layoutFile.toPath()}")

                if (!map.containsKey("alias")) {
                    map["alias"] = mapID; mapItr.set(map)
                    layout.set("maps", mapRegistry)
                }

                if (!map.containsKey("path"))
                    throw FaultyConfiguration("Entry \'path\' of $mapID is missing in ${layoutFile.toPath()}")

                if (map["lobby"] == true)
                    lobby = map["id"] as String?

                layout.save(layoutFile)
            }

            if (lobby.isNullOrBlank())
                throw FaultyConfiguration("Game \'$name\' doesn't have lobby map.")

            // Load script registry from layout.yml
            while (scriptItr.hasNext()) {
                val map = scriptItr.next()
                val scriptID = map["id"] as String? ?: throw FaultyConfiguration("Entry \'id\' of script is missing in ${layoutFile.toPath()}")
                val pathStr = map["path"] as String? ?: throw FaultyConfiguration("Entry \'path\' of script $scriptID is missing in ${layoutFile.toPath()}")
                val scriptFile = layoutFile.parentFile!!.resolve(pathStr)

                try {
                    if (!scriptFile.isFile)
                        throw FaultyConfiguration("Unable to locate the script: $scriptFile")
                } catch (e: SecurityException) {
                    throw RuntimeException("Unable to read script: $scriptFile", e)
                }

                try {
                    scriptRegistry[scriptID] = ScriptFactory.getInstance(scriptFile, null)
                } catch (e: ScriptEngineNotFound) {
                    Main.logger.warning(e.localizedMessage)
                }
            }

            // Load configuration files (coordinate tags, players restore)
            val restorePath = layout.getString("players.path")
                    ?: throw FaultyConfiguration("players.path is not defined in ${layoutFile.toPath()}.")
            val tagPath = layout.getString("coordinate-tags.path")
                    ?: throw FaultyConfiguration("coordinate-tags.path is not defined in ${layoutFile.toPath()}.")
            val restoreFile = layoutFile.parentFile!!.resolve(restorePath)
            val tagFile = layoutFile.parentFile!!.resolve(tagPath)
            val game: Game

            restoreFile.parentFile!!.mkdirs()
            if (!restoreFile.isFile && !restoreFile.createNewFile())
                throw RuntimeException("Unable to create file: ${restoreFile.toPath()}")
            if (!tagFile.isFile && !tagFile.createNewFile())
                throw RuntimeException("Unable to create file: ${tagFile.toPath()}")
            if (restoreFile.extension != "yml")
                throw FaultyConfiguration("This file has wrong extension: ${tagFile.name} (Rename it to .yml)")
            if (tagFile.extension != "yml")
                throw FaultyConfiguration("This file has wrong extension: ${tagFile.name} (Rename it to .yml)")

            game = Game(-1, false, layoutFile.parentFile!!.toPath(), tagFile, restoreFile, name, scriptRegistry, mapRegistry, lobby)
            return game
        }

        /**
         * Make a new game instance with given name.
         *
         * @param name Classifies the type of game
         * @param genLobby Determines if lobby must be generated.
         * @param consumer is called when lobby is generated. (Unnecessary if genLobby is false)
         * @return The new game instance.
         * @throws GameNotFound No such game exists with given id.
         * @throws FaultyConfiguration Configuration is not complete.
         * @throws RuntimeException Unexpected issue has arrised.
         */
        fun openNew(name: String, genLobby: Boolean, consumer: Consumer<World>? = null) : Game {
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

            if (genLobby) { // Generate lobby
                val map = game.map
                map.generate(map.lobbyID, false, consumer)
            }
            return game
        }

        internal fun purge(game: Game) {
            gameRegistry.remove(game.id)
        }
    }
}