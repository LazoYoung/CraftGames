package com.github.lazoyoung.craftgames.api.coordtag.tag

import com.github.lazoyoung.craftgames.api.coordtag.capture.AreaCapture
import com.github.lazoyoung.craftgames.api.coordtag.capture.BlockCapture
import com.github.lazoyoung.craftgames.api.coordtag.capture.CoordCapture
import com.github.lazoyoung.craftgames.api.coordtag.capture.SpawnCapture
import com.github.lazoyoung.craftgames.impl.exception.FaultyConfiguration
import com.github.lazoyoung.craftgames.impl.game.GameLayout
import com.github.lazoyoung.craftgames.impl.game.GameMap
import org.bukkit.configuration.file.YamlConfiguration
import java.io.IOException
import java.nio.file.Files
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

class CoordTag private constructor(
        var name: String,
        var mode: TagMode,
        val registry: Registry,
        private val captures: LinkedList<CoordCapture>,
        private var suppress: Boolean
) {
    var removed: Boolean = false
        private set

    class Registry internal constructor(val layout: GameLayout) {

        /** CoordTags configuration for every maps in this game. **/
        internal val config: YamlConfiguration
        private val path = layout.dataDir.resolve("coordinate-tags.yml")
        private val file = path.toFile()
        private val storage = HashMap<String, CoordTag>()

        constructor(gameName: String) : this(GameLayout(gameName))

        init {
            file.parentFile?.mkdirs()

            if (!Files.isRegularFile(path)) {
                try {
                    Files.createFile(path)
                } catch (e: IOException) {
                    throw RuntimeException("Unable to create file: $path", e)
                }
            }

            if (file.extension != "yml") {
                throw FaultyConfiguration("File extension is illegal: ${file.name} (Replace with .yml)")
            }

            config = YamlConfiguration.loadConfiguration(file)
            reload(null)
        }

        /**
         * Get a specific [CoordTag].
         *
         * @param tagName Name of tag to get.
         * @return Returns the [CoordTag] matching with [tagName] if found. Otherwise null.
         */
        fun get(tagName: String): CoordTag? {
            return storage[tagName]
        }

        /**
         * Get all [CoordTag]s in this registry.
         */
        fun getAll(): List<CoordTag> {
            return storage.values.toList()
        }

        internal fun create(mapID: String, mode: TagMode, name: String) {
            config.set(name.plus(".mode"), mode.label)
            config.createSection(name.plus(".captures.").plus(mapID))
            reload(null)
        }

        /**
         * Reload this registry.
         *
         * @param tag Reference which is going to be updated to reflect changes.
         */
        internal fun reload(tag: CoordTag?) {
            storage.clear()

            for (name in config.getKeys(false)) {
                val modeStr = config.getString(name.plus(".mode"))?.toUpperCase()
                        ?: continue
                val mode = TagMode.valueOf(modeStr)
                val suppress = config.getBoolean(name.plus(".suppress"), false)
                val captList = LinkedList<CoordCapture>()
                val mapIterate = config.getConfigurationSection(
                        name.plus('.').plus("captures")
                )?.getKeys(false) ?: emptyList<String>()

                for (map in mapIterate) {
                    captList.addAll(deserialize(map, mode, name))
                }

                storage[name] = CoordTag(name, mode, this, captList, suppress)
            }

            tag?.update(storage[tag.name])
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
    }

    companion object {
        internal fun getKeyToCaptureStream(name: String, mapID: String): String {
            return name.plus('.').plus("captures").plus('.').plus(mapID)
        }
    }

    /**
     * Returns all the captures.
     *
     * @param mapID Excludes the captures outside the given map, if specified.
     * @return List of CoordCapture matching the conditions.
     * @throws IllegalStateException is raised if [removed] is true.
     */
    fun getCaptures(mapID: String?): List<CoordCapture> {
        check(!removed) {
            "This tag has been removed."
        }

        return captures.filter { mapID == null || mapID == it.mapID }
    }

    /**
     * Choose whether or not to suppress warning that tag is incomplete.
     *
     * @param suppress whether or not to suppress warning.
     * @throws IllegalStateException is raised if [removed] is true.
     */
    internal fun suppress(suppress: Boolean) {
        check(!removed) {
            "This tag has been removed."
        }

        registry.config.set(name.plus(".suppress"), suppress)
        registry.reload(this)
    }

    /**
     * This method scans the captures to examine if this tag is incomplete.
     * Incomplete tags are those who omit to capture coordinate at least 1 map.
     *
     * @return List of [GameMap] at which this tag haven't captured.
     * @throws IllegalStateException is raised if [removed] is true.
     */
    internal fun scanIncompleteMaps(): List<GameMap> {
        check(!removed) {
            "This tag has been removed."
        }

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
     *
     * @throws IllegalStateException is raised if [removed] is true.
     */
    internal fun remove() {
        check(!removed) {
            "This tag has been removed."
        }

        registry.config.set(name, null)
        registry.reload(this)
    }

    /**
     * Remove the one capture at given index and map inside this tag.
     * You will have to manually save the config to disk.
     *
     * @throws IllegalArgumentException if [capture] is not registerd to a tag.
     * @throws IllegalStateException is raised if [removed] is true.
     */
    internal fun removeCapture(capture: CoordCapture) {
        check(!removed) {
            "This tag has been removed."
        }

        try {
            val key = getKeyToCaptureStream(name, capture.mapID!!)
            val stream = registry.config.getStringList(key)
            stream.removeAt(capture.index!!)
            registry.config.set(key, stream)
            registry.reload(this)
        } catch (e: NullPointerException) {
            throw IllegalArgumentException(e)
        }
    }

    private fun update(tag: CoordTag?) {
        if (tag == null) {
            this.removed = true
        } else {
            this.removed = tag.removed
            this.captures.clear()
            this.captures.addAll(tag.captures)
            this.mode = tag.mode
            this.name = tag.name
            this.suppress = tag.suppress
        }
    }
}