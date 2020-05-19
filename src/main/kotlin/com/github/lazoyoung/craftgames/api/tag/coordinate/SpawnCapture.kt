package com.github.lazoyoung.craftgames.api.tag.coordinate

import org.bukkit.Location
import org.bukkit.World
import java.math.RoundingMode

class SpawnCapture(
        val x: Double,
        val y: Double,
        val z: Double,
        private val yaw: Float,
        private val pitch: Float,
        mapID: String,
        index: Int? = null
) : CoordCapture(mapID, index) {

    override fun serialize() : String {
        val r = RoundingMode.HALF_UP
        val x = x.toBigDecimal().setScale(1, r)
        val y = y.toBigDecimal().setScale(1, r)
        val z = z.toBigDecimal().setScale(1, r)
        val yaw = this.yaw.toBigDecimal().setScale(1, r)
        val pitch = this.pitch.toBigDecimal().setScale(1, r)
        val builder = StringBuilder()

        for (e in arrayOf(x, y, z, yaw, pitch)) {
            builder.append(e).append(",")
        }

        return builder.removeSuffix(",").toString()
    }

    fun toLocation(world: World): Location {
        return Location(world, x, y, z, yaw, pitch)
    }
}