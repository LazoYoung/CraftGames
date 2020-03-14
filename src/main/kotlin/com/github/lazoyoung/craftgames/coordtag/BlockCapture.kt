package com.github.lazoyoung.craftgames.coordtag

import com.github.lazoyoung.craftgames.game.Game
import java.math.BigDecimal
import java.math.MathContext

class BlockCapture(
        x: Double,
        y: Double,
        z: Double
) : CoordTag(x, y, z) {

    override fun serialize() : String {
        val c = MathContext(2)
        val x = BigDecimal(x, c)
        val y = BigDecimal(y, c)
        val z = BigDecimal(z, c)
        val str = StringBuilder()

        str.append(x.toString()).append(",").append(y.toString()).append(",").append(z.toString())
        return str.toString()
    }

    override fun add(game: Game, name: String, mapID: String) {
        val key = getKey(TagMode.BLOCK, name, mapID)
        val result = game.tagConfig.getStringList(key)
        result.add(serialize())
        game.tagConfig.set(key, result)
    }

}