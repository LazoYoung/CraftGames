package com.github.lazoyoung.craftgames.coordtag.capture

import com.github.lazoyoung.craftgames.coordtag.tag.CoordTag

abstract class CoordCapture(
        val mapID: String?,
        val index: Int?
) {

    /**
     * Add this capture into the tag. You will have to manually save the config.
     *
     * @param tag a tag for which this capture is saved.
     * @throws RuntimeException Thrown if mapID is undefined for this instance.
     */
    fun add(tag: CoordTag) {
        try {
            val key = CoordTag.getKeyToCaptureStream(tag.name, mapID!!)
            val config = tag.resource.tagConfig
            val stream = config.getStringList(key)
            stream.add(serialize())
            config.set(key, stream)
            CoordTag.reload(tag.resource)
        } catch (e: NullPointerException) {
            throw IllegalArgumentException(e)
        }
    }

    abstract fun serialize(): String
}