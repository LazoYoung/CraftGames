package com.github.lazoyoung.craftgames.coordtag

import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.Player

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
            val config = tag.game.resource.tagConfig
            val stream = config.getStringList(key)
            stream.add(serialize())
            config.set(key, stream)
            CoordTag.reload(tag.game)
        } catch (e: NullPointerException) {
            throw IllegalArgumentException(e)
        }
    }

    abstract fun serialize(): String

    abstract fun toLocation(world: World): Location

    @Deprecated("World is vauge.")
    abstract fun teleport(player: Player)
}