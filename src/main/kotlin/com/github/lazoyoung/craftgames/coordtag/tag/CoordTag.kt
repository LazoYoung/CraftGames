package com.github.lazoyoung.craftgames.coordtag.tag

import com.github.lazoyoung.craftgames.coordtag.capture.AreaCapture
import com.github.lazoyoung.craftgames.coordtag.capture.BlockCapture
import com.github.lazoyoung.craftgames.coordtag.capture.CoordCapture
import com.github.lazoyoung.craftgames.coordtag.capture.SpawnCapture
import com.github.lazoyoung.craftgames.game.GameLayout
import com.github.lazoyoung.craftgames.game.GameMap
import com.github.lazoyoung.craftgames.internal.exception.FaultyConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File

open class CoordTag private constructor(
        val name: String,
        val mode: TagMode,
        val registry: Registry,

        /** List of captures in this tag */
        private val captures: List<CoordCapture>,

        /** Suppress warning that it's incomplete. */
        private var suppress: Boolean = false
) {
    class Registry internal constructor(val layout: GameLayout) {

        /** CoordTags configuration for every maps in this game. **/
        internal val config: YamlConfiguration

        private val file: File
        private val storage = HashMap<String, CoordTag>()

        constructor(gameName: String) : this(GameLayout(gameName))

        init {
            val tagPath = layout.config.getString("coordinate-tags.file")
                    ?: throw FaultyConfiguration("coordinate-tags.file is not defined in ${layout.path}.")
            file = layout.root.resolve(tagPath).toFile()

            file.parentFile?.mkdirs()

            if (!file.isFile && !file.createNewFile())
                throw RuntimeException("Unable to create file: ${file.toPath()}")
            if (file.extension != "yml")
                throw FaultyConfiguration("File extension is illegal: ${file.name} (Replace with .yml)")

            config = YamlConfiguration.loadConfiguration(file)
            reload()
        }

        fun get(tagName: String): CoordTag? {
            return storage[tagName]
        }

        internal fun create(mapID: String, mode: TagMode, name: String) {
            config.set(name.plus(".mode"), mode.label)
            config.createSection(name.plus(".captures.").plus(mapID))
            reload()
        }

        internal fun reload() {
            storage.clear()

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
                    captList.addAll(deserialize(map, mode, name))
                }
                storage[name] = CoordTag(name, mode, this, captList, suppress)
            }
        }

        internal fun saveToDisk() {
            config.save(file)
        }

        private fun deserialize(mapID: String, mode: TagMode, tagName: String): List<CoordCapture> {
            val list = ArrayList<CoordCapture>()
            val stream = config.getStringList(getKeyToCaptureStream(tagName, mapID))
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

        fun getAll(): List<CoordTag> {
            return storage.values.toList()
        }
    }

    companion object {
        internal fun getKeyToCaptureStream(name: String, mapID: String): String {
            return name.plus('.').plus("captures").plus('.').plus(mapID)
        }
    }

    /*
    companion object {
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
            var tagList = storage[gameName]

            if (tagList == null) {
                try {
                    reload(GameResource(gameName))
                    tagList = storage[gameName]
                } catch (e: Exception) {}
            }

            return tagList ?: emptyList()
        }

        internal fun create(resource: GameResource, mapID: String, mode: TagMode, name: String) {
            val config = resource.tagConfig

            config.set(name.plus(".mode"), mode.label)
            config.createSection(name.plus(".captures.").plus(mapID))
            reload(resource)
        }
    }
    */

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
        registry.config.set(name.plus(".suppress"), suppress)
        registry.reload()
    }

    /**
     * This method scans the captures to examine if this tag is incomplete.
     * Incomplete tags are those who omit to capture coordinate at least 1 map.
     *
     * @return List of [GameMap] at which this tag haven't captured.
     */
    fun scanIncompleteMaps(): List<GameMap> {
        val list = ArrayList<GameMap>()

        if (!suppress) {
            for (map in GameMap.Registry(registry.layout, registry).getMaps()) {
                if (captures.none { map.id == it.mapID }) {
                    list.add(map)
                }
            }
        }

        return list
    }

    /**
     * Remove the tag and the whole captures in it.
     * You will have to manually save the config to disk.
     */
    fun remove() {
        registry.config.set(name, null)
        registry.reload()
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
            val stream = registry.config.getStringList(key)
            stream.removeAt(capture.index!!)
            registry.config.set(key, stream)
            registry.reload()
        } catch (e: NullPointerException) {
            throw IllegalArgumentException(e)
        }
    }
}