package com.github.lazoyoung.craftgames.api.coordtag.capture

import org.bukkit.Location
import org.bukkit.World

class BlockCapture(
        val x: Int,
        val y: Int,
        val z: Int,
        mapID: String,
        index: Int? = null
) : CoordCapture(mapID, index) {

    override fun serialize() : String {
        val builder = StringBuilder()

        for (e in arrayOf(x, y, z)) {
            builder.append(e.toBigDecimal()).append(",")
        }

        return builder.removeSuffix(",").toString()
    }

    fun toLocation(world: World): Location {
        return Location(world, x.toDouble(), y.toDouble(), z.toDouble())
    }

}