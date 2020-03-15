package com.github.lazoyoung.craftgames.coordtag

import com.github.lazoyoung.craftgames.game.GameMap
import java.math.BigDecimal
import java.math.MathContext

class BlockCapture(
        map: GameMap,
        name: String,
        x: Double,
        y: Double,
        z: Double
) : CoordTag(map, name, x, y, z) {

    override fun serialize() : String {
        val c = MathContext(2)
        val x = BigDecimal(x, c)
        val y = BigDecimal(y, c)
        val z = BigDecimal(z, c)
        val str = StringBuilder()

        str.append(x.toString()).append(",").append(y.toString()).append(",").append(z.toString())
        return str.toString()
    }

    override fun capture() {
        val key = getKey(TagMode.BLOCK, name, map.mapID!!)
        val result = map.game.tagConfig.getStringList(key)
        result.add(serialize())
        map.game.tagConfig.set(key, result)
    }

}