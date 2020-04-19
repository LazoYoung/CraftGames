package com.github.lazoyoung.craftgames.coordtag.tag

import com.github.lazoyoung.craftgames.coordtag.capture.AreaCapture
import com.github.lazoyoung.craftgames.coordtag.capture.BlockCapture
import com.github.lazoyoung.craftgames.coordtag.capture.CoordCapture
import com.github.lazoyoung.craftgames.coordtag.capture.SpawnCapture
import com.github.lazoyoung.craftgames.game.Game
import com.github.lazoyoung.craftgames.game.GameResource

class CoordTag private constructor(
        val name: String,
        val mode: TagMode,
        val resource: GameResource,

        /** List of captures in this tag */
        private val captures: List<CoordCapture>,

        /** Suppress warning that it's incomplete. */
        private var suppress: Boolean = false
) {
    companion object Registry {
        /** Key: Game name, Value: List of tags */
        private val tags = HashMap<String, List<CoordTag>>()

        /**
         * This method allows you to access every coordinate tags in the game.
         * CoordTag generally preserve a set of CoordCaptures per each map.
         *
         * @param game Which game to get the tags from?
         * @return List of CoordTag matching the conditions above.
         */
        fun getAll(game: Game): List<CoordTag> {
            return getAll(game.name)
        }

        /**
         * Functionailty is equivalent to [getAll].
         */
        fun getAll(gameName: String): List<CoordTag> {
            var tagList = tags[gameName]

            if (tagList == null) {
                try {
                    reload(GameResource((gameName)))
                    tagList = tags[gameName]
                } catch (e: Exception) {}
            }

            return tagList ?: emptyList()
        }

        /**
         * @return The [tag][CoordTag] matching with [name] inside the [game]. Null if not found.
         */
        fun get(game: Game, tagName: String): CoordTag? {
            return getAll(game).firstOrNull { it.name == tagName }
        }

        /**
         * @see [get]
         */
        fun get(gameName: String, tagName: String): CoordTag? {
            return getAll(gameName).firstOrNull { it.name == tagName }
        }

        /**
         * Reload all tags associated with specific game.
         */
        @Suppress("UNCHECKED_CAST")
        internal fun reload(resource: GameResource) {
            val config = resource.tagConfig
            val list = ArrayList<CoordTag>()

            for (name in config.getKeys(false)) {
                val modeStr = config.getString(name.plus(".mode"))?.toUpperCase()
                        ?: continue
                val mode = TagMode.valueOf(modeStr)
                val suppress = config.getBoolean(name.plus(".suppress"), false)
                val captList = ArrayList<CoordCapture>()
                val mapIterate = config.getConfigurationSection(
                        name.plus('.').plus("captures")
                )?.getKeys(false) ?: emptyList<String>()

                for (map in mapIterate) {
                    captList.addAll(deserialize(resource, map, mode, name))
                }
                list.add(CoordTag(name, mode, resource, captList, suppress))
            }

            tags[resource.gameName] = list
        }

        internal fun create(resource: GameResource, mapID: String, mode: TagMode, name: String) {
            val config = resource.tagConfig

            config.set(name.plus(".mode"), mode.label)
            config.createSection(name.plus(".captures.").plus(mapID))
            reload(resource)
        }

        internal fun getKeyToCaptureStream(name: String, mapID: String): String {
            return name.plus('.').plus("captures").plus('.').plus(mapID)
        }

        private fun deserialize(resource: GameResource, mapID: String, mode: TagMode, tagName: String): List<CoordCapture> {
            val list = ArrayList<CoordCapture>()
            val stream = resource.tagConfig.getStringList(getKeyToCaptureStream(tagName, mapID))
            var index = 0

            for (line in stream) {
                val arr = line.split(',', ignoreCase = false, limit = 6)

                when (mode) {
                    TagMode.SPAWN -> {
                        val x = arr[0].toBigDecimal().toDouble()
                        val y = arr[1].toBigDecimal().toDouble()
                        val z = arr[2].toBigDecimal().toDouble()
                        val yaw = arr[3].toBigDecimal().toFloat()
                        val pitch = arr[4].toBigDecimal().toFloat()
                        list.add(SpawnCapture(x, y, z, yaw, pitch, mapID, index++))
                    }
                    TagMode.BLOCK -> {
                        val x = arr[0].toBigDecimal().toInt()
                        val y = arr[1].toBigDecimal().toInt()
                        val z = arr[2].toBigDecimal().toInt()
                        list.add(BlockCapture(x, y, z, mapID, index++))
                    }
                    TagMode.AREA -> {
                        val x1 = arr[0].toBigDecimal().toInt()
                        val x2 = arr[1].toBigDecimal().toInt()
                        val y1 = arr[2].toBigDecimal().toInt()
                        val y2 = arr[3].toBigDecimal().toInt()
                        val z1 = arr[4].toBigDecimal().toInt()
                        val z2 = arr[5].toBigDecimal().toInt()
                        list.add(AreaCapture(x1, x2, y1, y2, z1, z2, mapID, index++))
                    }
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
     * Choose whether or not to suppress warning that tag is incomplete.
     *
     * @param suppress whether or not to suppress warning.
     */
    fun suppress(suppress: Boolean) {
        val config = resource.tagConfig

        config.set(name.plus(".suppress"), suppress)
        resource.saveToDisk(true)
        reload(resource)
    }

    /**
     * This method scans the captures to examine if this tag is incomplete.
     * Incomplete tags are those who omit to capture coordinate at least 1 map.
     *
     * @return List of map IDs where this tag haven't captured yet.
     */
    fun scanIncompleteMaps(): List<String> {
        val list = ArrayList<String>()

        if (suppress) {
            return list
        }

        for (mapID in Game.getMapNames(resource.gameName)) {
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
        resource.tagConfig.set(name, null)
        reload(resource)
    }

    /**
     * Remove the one capture at given index and map inside this tag.
     * You will have to manually save the config to disk.
     *
     * @throws IllegalArgumentException if [capture] is not registerd to a tag.
     */
    fun removeCapture(capture: CoordCapture) {
        try {
            val key = getKeyToCaptureStream(name, capture.mapID!!)
            val config = resource.tagConfig
            val stream = config.getStringList(key)
            stream.removeAt(capture.index!!)
            config.set(key, stream)
            reload(resource)
        } catch (e: NullPointerException) {
            throw IllegalArgumentException(e)
        }
    }
}