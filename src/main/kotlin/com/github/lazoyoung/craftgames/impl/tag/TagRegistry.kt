package com.github.lazoyoung.craftgames.impl.tag

import com.github.lazoyoung.craftgames.api.tag.coordinate.*
import com.github.lazoyoung.craftgames.api.tag.item.ItemTag
import com.github.lazoyoung.craftgames.impl.exception.FaultyConfiguration
import com.github.lazoyoung.craftgames.impl.game.GameLayout
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import kotlin.collections.HashMap

class TagRegistry internal constructor(
        internal val layout: GameLayout
) {
    internal val ctagConfig: YamlConfiguration
    internal val itagConfig: YamlConfiguration
    private val ctagPath = layout.root.resolve("coordinate-tags.yml")
    private val itagPath = layout.root.resolve("item-tags.yml")
    private val ctagFile = ctagPath.toFile()
    private val itagFile = itagPath.toFile()
    private val ctagStorage = HashMap<String, CoordTag>()
    private val itagStorage = HashMap<String, ItemTag>()

    constructor(gameName: String) : this(GameLayout(gameName))

    init {
        ctagConfig = loadConfig(ctagPath, ctagFile)
        itagConfig = loadConfig(itagPath, itagFile)
    }

    fun getCoordTag(name: String): CoordTag? {
        return ctagStorage[name]
    }

    fun getCoordTags(): List<CoordTag> {
        return ctagStorage.values.toList()
    }

    fun getItemTag(name: String): ItemTag? {
        return itagStorage[name]
    }

    fun getItemTags(): List<ItemTag> {
        return itagStorage.values.toList()
    }

    internal fun getCoordCaptureStream(name: String, mapID: String): List<String> {
        return ctagConfig.getStringList(getCoordCaptureStreamKey(name, mapID))
    }

    internal fun getCoordCaptureStreamKey(name: String, mapID: String): String {
        return name.plus('.').plus("captures").plus('.').plus(mapID)
    }

    internal fun saveToDisk() {
        ctagConfig.save(ctagFile)
        itagConfig.save(itagFile)
    }

    internal fun createCoordTag(mapID: String, mode: TagMode, name: String) {
        ctagConfig.set(name.plus(".mode"), mode.label)
        ctagConfig.createSection(name.plus(".captures.").plus(mapID))
        reloadCoordTags(null)
    }

    /**
     * Reload [ctagStorage].
     *
     * @param tag Reference which is going to be updated to reflect changes.
     */
    internal fun reloadCoordTags(tag: CoordTag?) {
        ctagStorage.clear()

        for (name in ctagConfig.getKeys(false)) {
            val modeStr = ctagConfig.getString(name.plus(".mode"))?.toUpperCase()
                    ?: continue
            val mode = TagMode.valueOf(modeStr)
            val suppress = ctagConfig.getBoolean(name.plus(".suppress"), false)
            val captureList = LinkedList<CoordCapture>()
            val mapIterate = ctagConfig.getConfigurationSection(
                    name.plus('.').plus("captures")
            )?.getKeys(false) ?: emptyList<String>()

            for (map in mapIterate) {
                val list = ArrayList<CoordCapture>()
                var index = 0

                for (line in getCoordCaptureStream(name, map)) {
                    val arr = line.split(',', ignoreCase = false, limit = 6)

                    when (mode) {
                        TagMode.SPAWN -> {
                            val x = arr[0].toBigDecimal().toDouble()
                            val y = arr[1].toBigDecimal().toDouble()
                            val z = arr[2].toBigDecimal().toDouble()
                            val yaw = arr[3].toBigDecimal().toFloat()
                            val pitch = arr[4].toBigDecimal().toFloat()
                            list.add(SpawnCapture(x, y, z, yaw, pitch, map, index++))
                        }
                        TagMode.BLOCK -> {
                            val x = arr[0].toBigDecimal().toInt()
                            val y = arr[1].toBigDecimal().toInt()
                            val z = arr[2].toBigDecimal().toInt()
                            list.add(BlockCapture(x, y, z, map, index++))
                        }
                        TagMode.AREA -> {
                            val x1 = arr[0].toBigDecimal().toInt()
                            val x2 = arr[1].toBigDecimal().toInt()
                            val y1 = arr[2].toBigDecimal().toInt()
                            val y2 = arr[3].toBigDecimal().toInt()
                            val z1 = arr[4].toBigDecimal().toInt()
                            val z2 = arr[5].toBigDecimal().toInt()
                            list.add(AreaCapture(x1, x2, y1, y2, z1, z2, map, index++))
                        }
                    }
                }
                captureList.addAll(list)
            }
            ctagStorage[name] = CoordTag(name, mode, this, captureList, suppress)
        }
        tag?.update(ctagStorage[tag.name])
    }

    private fun loadConfig(path: Path, file: File): YamlConfiguration {
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

        return YamlConfiguration.loadConfiguration(file)
    }
}