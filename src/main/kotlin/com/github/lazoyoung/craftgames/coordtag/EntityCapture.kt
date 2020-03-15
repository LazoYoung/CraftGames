package com.github.lazoyoung.craftgames.coordtag

import com.github.lazoyoung.craftgames.game.GameMap
import java.math.BigDecimal
import java.math.MathContext

class EntityCapture(
        map: GameMap,
        x: Double,
        y: Double,
        z: Double,
        private val yaw: Float,
        private val pitch: Float
) : CoordTag(map, x, y, z) {

    override fun serialize() : String {
        val c = MathContext(2)
        val x = BigDecimal(x, c)
        val y = BigDecimal(y, c)
        val z = BigDecimal(z, c)
        val yaw = BigDecimal(yaw.toDouble(), c)
        val pitch = BigDecimal(pitch.toDouble(), c)
        val str = StringBuilder()

        str.append(x.toString()).append(",").append(y.toString()).append(",")
                .append(z.toString()).append(",").append(yaw).append(",").append(pitch)
        return str.toString()
    }

    override fun saveCapture(tagName: String) {
        val key = getKey(TagMode.ENTITY, tagName, map.mapID!!)
        val result = map.game.tagConfig.getStringList(key)
        result.add(serialize())
        map.game.tagConfig.set(key, result)
    }

}