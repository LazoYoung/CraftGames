package com.github.lazoyoung.craftgames.coordtag

import com.github.lazoyoung.craftgames.game.Game
import java.math.BigDecimal

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
        val x = BigDecimal(x)
        val y = BigDecimal(y)
        val z = BigDecimal(z)
        val yaw = BigDecimal(yaw.toDouble())
        val pitch = BigDecimal(pitch.toDouble())
        val str = StringBuilder()
        x.setScale(1)
        y.setScale(1)
        z.setScale(1)
        yaw.setScale(2)
        pitch.setScale(2)

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