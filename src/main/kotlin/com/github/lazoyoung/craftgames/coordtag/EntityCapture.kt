package com.github.lazoyoung.craftgames.coordtag

import com.github.lazoyoung.craftgames.game.Game
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerTeleportEvent
import java.math.BigDecimal
import java.math.RoundingMode

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
        val r = RoundingMode.HALF_UP
        val x = x.toBigDecimal().setScale(1, r)
        val y = y.toBigDecimal().setScale(1, r)
        val z = z.toBigDecimal().setScale(1, r)
        val yaw = BigDecimal(yaw.toDouble())
        val pitch = BigDecimal(pitch.toDouble())
        val str = StringBuilder()

        str.append(x.toString()).append(",").append(y.toString()).append(",")
                .append(z.toString()).append(",").append(yaw).append(",").append(pitch)
        return str.toString()
    }

    override fun teleport(player: Player) {
        val loc = Location(game.map.world, x, y, z, yaw, pitch)
        player.teleport(loc, PlayerTeleportEvent.TeleportCause.PLUGIN)
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