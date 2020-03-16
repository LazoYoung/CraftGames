package com.github.lazoyoung.craftgames.coordtag

import com.github.lazoyoung.craftgames.game.Game
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerTeleportEvent

class BlockCapture(
        game: Game,
        mapID: String,
        x: Int,
        y: Int,
        z: Int,
        tagName: String? = null,
        index: Int? = null
) : CoordTag(game, mapID, x.toDouble(), y.toDouble(), z.toDouble(), tagName, index) {

    override fun serialize() : String {
        return StringBuilder().append(x.toString()).append(",")
                .append(y.toString()).append(",").append(z.toString()).toString()
    }

    override fun teleport(player: Player) {
        val loc = Location(game.map.world, x, y, z)
        player.teleport(loc, PlayerTeleportEvent.TeleportCause.PLUGIN)
    }

    override fun saveCapture(tagName: String?) {
        val key: String
        val result: List<String>
        var name = tagName

        if (name.isNullOrBlank())
            name = this.tagName

        try {
            key = getKey(TagMode.BLOCK, name!!, mapID)
            result = game.tagConfig.getStringList(key)
            result.add(serialize())
            game.tagConfig.set(key, result)
        } catch (e: NullPointerException) {
            throw IllegalArgumentException(e)
        }
    }

}