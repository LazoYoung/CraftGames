package com.github.lazoyoung.craftgames.coordtag

import com.github.lazoyoung.craftgames.game.Game
import java.math.BigDecimal
import java.math.MathContext

class EntityCapture(
        game: Game,
        mapID: String,
        x: Double,
        y: Double,
        z: Double,
        private val yaw: Float,
        private val pitch: Float,
        tagName: String? = null,
        index: Int? = null
) : CoordTag(game, mapID, x, y, z, tagName, index) {

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

    override fun saveCapture(tagName: String?) {
        val key: String
        val result: List<String>
        var name = tagName

        if (name.isNullOrBlank())
            name = this.tagName

        try {
            key = getKey(TagMode.ENTITY, name!!, mapID)
            result = game.tagConfig.getStringList(key)
            result.add(serialize())
            game.tagConfig.set(key, result)
        } catch (e: NullPointerException) {
            throw IllegalArgumentException(e)
        }
    }

}