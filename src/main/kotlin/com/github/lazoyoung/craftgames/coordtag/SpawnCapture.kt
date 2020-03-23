package com.github.lazoyoung.craftgames.coordtag

import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerTeleportEvent
import java.math.RoundingMode

class SpawnCapture(
        x: Double,
        y: Double,
        z: Double,
        val yaw: Float,
        val pitch: Float,
        mapID: String,
        index: Int? = null
) : CoordCapture(x, y, z, mapID, index) {

    override fun serialize() : String {
        val r = RoundingMode.HALF_UP
        val x = x.toBigDecimal().setScale(1, r)
        val y = y.toBigDecimal().setScale(1, r)
        val z = z.toBigDecimal().setScale(1, r)
        val yaw = this.yaw.toBigDecimal().setScale(1, r)
        val pitch = this.pitch.toBigDecimal().setScale(1, r)
        val str = StringBuilder()

        str.append(x.toString()).append(",").append(y.toString()).append(",")
                .append(z.toString()).append(",").append(yaw).append(",").append(pitch)
        return str.toString()
    }

    override fun teleport(player: Player) {
        val loc = Location(player.world, x, y, z, yaw, pitch)
        player.teleport(loc, PlayerTeleportEvent.TeleportCause.PLUGIN)
    }

}