package com.github.lazoyoung.craftgames.coordtag

import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerTeleportEvent

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

    override fun toLocation(world: World): Location {
        return Location(world, x.toDouble(), y.toDouble(), z.toDouble())
    }

    override fun teleport(player: Player) {
        val loc = Location(player.world, x.toDouble(), y.toDouble(), z.toDouble())
        player.teleport(loc, PlayerTeleportEvent.TeleportCause.PLUGIN)
    }

}