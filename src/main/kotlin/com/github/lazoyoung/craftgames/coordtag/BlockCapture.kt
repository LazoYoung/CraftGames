package com.github.lazoyoung.craftgames.coordtag

import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerTeleportEvent

class BlockCapture(
        x: Int,
        y: Int,
        z: Int,
        mapID: String,
        index: Int? = null
) : CoordCapture(x.toDouble(), y.toDouble(), z.toDouble(), mapID, index) {

    override fun serialize() : String {
        return StringBuilder().append(x.toString()).append(",")
                .append(y.toString()).append(",").append(z.toString()).toString()
    }

    override fun teleport(player: Player) {
        val loc = Location(player.world, x, y, z)
        player.teleport(loc, PlayerTeleportEvent.TeleportCause.PLUGIN)
    }

}