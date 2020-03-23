package com.github.lazoyoung.craftgames.coordtag

import com.github.lazoyoung.craftgames.game.Game
import java.math.BigDecimal

class CoordTag private constructor(
        val game: Game,
        val mode: TagMode,
        val name: String,
        private val captures: List<CoordCapture>
) {
    companion object Registry {
        /** Key: Game name, Value: List of tags **/
        private val tags = HashMap<String, List<CoordTag>>()

        /**
         * This method allows you to access every coordinate tags in the game.
         * CoordTag preserves a set of CoordCapture per each map.
         * CoordCapture is fundamental unit of coordinate tags.
         *
         * @param game Which game to get the tags from?
         * @return List of CoordTag matching the conditions above.
         */
        fun getAll(game: Game): List<CoordTag> {
            return tags[game.name] ?: emptyList()
        }

        /**
         * @return CoordTag matching the name inside the game, if found.
         */
        fun get(game: Game, name: String): CoordTag? {
            return getAll(game).firstOrNull { it.name == name }
        }

        /**
         * Reload all tags associated with specific game.
         */
        fun reload(game: Game) {
            val config = game.resource.tagConfig
            val list = ArrayList<CoordTag>()

            for (name in config.getKeys(false)) {
                val modeStr = config.getString(name.plus('.').plus("mode"))
                        ?.toUpperCase() ?: continue
                val mode = TagMode.valueOf(modeStr)
                val captList = ArrayList<CoordCapture>()
                val mapIterate = config.getConfigurationSection(name.plus('.').plus("captures"))
                        ?.getKeys(false) ?: emptyList<String>()

                for (map in mapIterate) {
                    captList.addAll(deserialize(game, map, mode, name))
                }
                list.add(CoordTag(game, mode, name, captList))
            }
            tags[game.name] = list
        }

        fun create(game: Game, mode: TagMode, name: String) {
            val config = game.resource.tagConfig

            config.set(name.plus(".mode"), mode.label)
            config.createSection(name.plus(".captures.").plus(game.map.mapID))
            reload(game)
        }

        internal fun getKeyToCaptureStream(name: String, mapID: String): String {
            return name.plus('.').plus("captures").plus('.').plus(mapID)
        }

        private fun deserialize(game: Game, mapID: String, mode: TagMode, tagName: String): List<CoordCapture> {
            val list = ArrayList<CoordCapture>()
            val stream = game.resource.tagConfig.getStringList(getKeyToCaptureStream(tagName, mapID))
            var index = 0

            for (line in stream) {
                val arr = line.split(',', ignoreCase = false, limit = 5)
                val x = arr[0].toBigDecimal()
                val y = arr[1].toBigDecimal()
                val z = arr[2].toBigDecimal()
                val yaw: BigDecimal
                val pitch: BigDecimal

                if (mode == TagMode.SPAWN) {
                    yaw = arr[3].toBigDecimal()
                    pitch = arr[4].toBigDecimal()
                    list.add(SpawnCapture(x.toDouble(), y.toDouble(), z.toDouble(),
                            yaw.toFloat(), pitch.toFloat(), mapID, index++))
                } else {
                    list.add(BlockCapture(x.toInt(), y.toInt(), z.toInt(), mapID, index++))
                }
            }
            return list
        }
    }

    /**
     * Returns all the captures.
     *
     * @param mapID Excludes the captures outside the given map, if specified.
     * @return List of CoordCapture matching the conditions.
     */
    fun getCaptures(mapID: String?): List<CoordCapture> {
        return captures.filter { mapID == null || mapID == it.mapID }
    }

    /**
     * Returns the captures associated with the current map.
     *
     * @return List of CoordCapture matching the conditions.
     */
    fun getLocalCaptures(): List<CoordCapture> {
        return captures.filter { it.mapID == game.map.mapID }
    }

    /**
     * This method scans the captures to examine if this tag is incomplete.
     * Incomplete tags are those who omit to capture coordinate from at least one map.
     *
     * @return List of IDs for game-maps that are excluded from the tag.
     */
    fun scanIncompleteMaps(): List<String> {
        val list = ArrayList<String>()

        for (mapID in Game.getMapNames(game.name)) {
            if (captures.none { mapID == it.mapID }) {
                list.add(mapID)
            }
        }
        return list
    }

    /**
     * Remove the tag and the whole captures in it.
     * You will have to manually save the config to disk.
     */
    fun remove() {
        game.resource.tagConfig.set(name, null)
        reload(game)
    }

    /**
     * Remove the one capture at given index and map inside this tag.
     * You will have to manually save the config to disk.
     */
    fun removeCapture(index: Int, mapID: String) {
        try {
            val key = getKeyToCaptureStream(name, mapID)
            val config = game.resource.tagConfig
            val stream = config.getStringList(key)
            stream.removeAt(index)
            config.set(key, stream)
            reload(game)
        } catch (e: NullPointerException) {
            throw IllegalArgumentException(e)
        }
    }
}