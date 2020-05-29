package com.github.lazoyoung.craftgames.api.tag.coordinate

import com.github.lazoyoung.craftgames.api.module.WorldModule
import com.github.lazoyoung.craftgames.impl.game.GameMap
import com.github.lazoyoung.craftgames.impl.tag.TagRegistry
import java.util.*
import kotlin.collections.ArrayList

class CoordTag internal constructor(
        name: String,
        mode: TagMode,
        val registry: TagRegistry,
        private val captures: LinkedList<CoordCapture>,
        private var suppress: Boolean
) {
    var name = name
        private set
    var mode = mode
        private set
    var removed: Boolean = false
        private set

    /**
     * Returns every [CoordCapture] in this game.
     *
     * @param mapID Excludes the captures outside the given map, if specified.
     * @return List of CoordCapture matching the conditions.
     * @throws IllegalStateException is raised if [removed] is true.
     * @see [WorldModule.getMapID]
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

        registry.ctagConfig.set(name.plus(".suppress"), suppress)
        registry.reloadCoordTags(this)
    }

    /**
     * This method scans the captures to examine if this tag is incomplete.
     * Incomplete tags are those who omit to capture coordinate at least 1 map.
     *
     * @return List of [GameMap] where this tag is incomplete.
     * @throws IllegalStateException is raised if this tag has been [removed].
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

        registry.ctagConfig.set(name, null)
        registry.reloadCoordTags(this)
    }

    /**
     * Remove the given [capture] from this [CoordTag].
     * [TagRegistry.saveToDisk] should be used to save changes.
     *
     * @throws IllegalArgumentException if [capture] is not registerd to a tag.
     * @throws IllegalStateException is raised if [removed] is true.
     */
    internal fun removeCapture(capture: CoordCapture) {
        requireNotNull(capture.mapID) {
            "This capture isn't assigned to any map."
        }
        check(!removed) {
            "This tag has been removed."
        }

        try {
            val key = registry.getCoordCaptureStreamKey(name, capture.mapID)
            val stream = registry.getCoordCaptureStream(name, capture.mapID)
                    .toMutableList()

            stream.removeAt(capture.index!!)
            registry.ctagConfig.set(key, stream)
            registry.reloadCoordTags(this)
        } catch (e: NullPointerException) {
            throw IllegalArgumentException(e)
        }
    }

    internal fun update(tag: CoordTag?) {
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