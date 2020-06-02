package com.github.lazoyoung.craftgames.api.tag.coordinate

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
        return Location(world, x + 0.5, y + 0.5, z + 0.5)
    }

}