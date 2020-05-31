package com.github.lazoyoung.craftgames.impl.tag

import com.github.lazoyoung.craftgames.api.tag.coordinate.*
import com.github.lazoyoung.craftgames.api.tag.item.ItemTag
import com.github.lazoyoung.craftgames.impl.Main
import com.github.lazoyoung.craftgames.impl.exception.FaultyConfiguration
import com.github.lazoyoung.craftgames.impl.game.GameLayout
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.inventory.ItemStack
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
    private val ctagPath = layout.dataDir.resolve("coordinate-tags.yml")
    private val itagPath = layout.dataDir.resolve("item-tags.yml")
    private val ctagFile = ctagPath.toFile()
    private val itagFile = itagPath.toFile()
    private val ctagStorage = HashMap<String, CoordTag>()
    private val itagStorage = HashMap<String, ItemTag>()

    constructor(gameName: String) : this(GameLayout(gameName))

    init {
        ctagConfig = loadConfig(ctagPath, ctagFile)
        itagConfig = loadConfig(itagPath, itagFile)
        reloadCoordTags(null)
        reloadItemTags(null)
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

    /**
     * @throws IllegalArgumentException is raised if [name] contains illegal character.
     * @throws IllegalArgumentException is raised if [name] conflicts with another tag.
     */
    fun createCoordTag(mapID: String, mode: TagMode, name: String): CoordTag {
        require(Regex("^\\w+$").matches(name)) {
            "Illegal character found: $name" +
                    "\nAlphanumeric & underscore character can be used."
        }
        require(getCoordTag(name) == null) {
            "This tag already exists: $name"
        }

        ctagConfig.set(name.plus(".mode"), mode.label)
        ctagConfig.createSection(name.plus(".captures.").plus(mapID))
        reloadCoordTags(null)
        return checkNotNull(getCoordTag(name)) {
            "Failed to create coordinate tag."
        }
    }

    /**
     * @throws IllegalArgumentException is raised if [name] contains illegal character.
     * @throws IllegalArgumentException is raised if [name] conflicts with another tag.
     */
    fun createItemTag(name: String, itemStack: ItemStack): ItemTag {
        require(Regex("^\\w+$").matches(name)) {
            "Illegal character found: $name" +
                    "\nAlphanumeric & underscore character can be used."
        }
        require(getItemTag(name) == null) {
            "This tag already exists: $name"
        }

        itagConfig.set(name, itemStack)
        reloadItemTags(null)
        return checkNotNull(getItemTag(name)) {
            "Failed to create item tag."
        }
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

    /**
     * Reload [ctagStorage].
     *
     * @param tag Reference which is going to be updated to reflect changes.
     */
    internal fun reloadCoordTags(tag: CoordTag?) {
        ctagStorage.clear()

        try {
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
        } catch (t: Throwable) {
            t.printStackTrace()
            Main.logger.severe("Failed to read ${ctagFile.name}! Is it corrupted?")
        }

        tag?.update(ctagStorage[tag.name])
    }

    @Suppress("UNCHECKED_CAST")
    internal fun reloadItemTags(tag: ItemTag?) {
        itagStorage.clear()

        try {
            for (name in itagConfig.getKeys(false)) {
                val itemStack = itagConfig.getItemStack(name)
                        ?: continue
                itagStorage[name] = ItemTag(name, itemStack, this)
            }
        } catch (t: Throwable) {
            t.printStackTrace()
            Main.logger.severe("Failed to read ${itagFile.name}! Is it corrupted?")
        }

        if (tag != null && !itagStorage.containsKey(tag.name)) {
            tag.removed = true
        }
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