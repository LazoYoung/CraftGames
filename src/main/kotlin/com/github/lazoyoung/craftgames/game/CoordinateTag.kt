package com.github.lazoyoung.craftgames.game

import com.github.lazoyoung.craftgames.exception.MapNotFound
import org.bukkit.Location
import java.math.BigDecimal
import java.math.MathContext

class CoordinateTag(private val map: GameMap, private val name: String, private val mode: TagMode) {

    fun exists() : Boolean {
        return map.tagConfig.getStringList(getKey()).isNotEmpty()
    }

    fun add(loc: Location) {
        val result = map.tagConfig.getStringList(getKey())
        result.add(serialize(loc))
        map.tagConfig.set(getKey(), result)
    }

    fun get() : List<Location> {
        return map.tagConfig.getStringList(getKey()).map { deserialize(it) }
        /*
        if (arr.isNullOrEmpty())
            throw FaultyConfiguration("Block tag $groupName is not defined for: " +
                    "Game: ${map.game.name}, Map: ${map.mapID}")
         */
    }

    private fun serialize(loc: Location) : String {
        val c = MathContext(2)
        var x = BigDecimal(loc.blockX, c)
        var y = BigDecimal(loc.blockY, c)
        var z = BigDecimal(loc.blockZ, c)
        val yaw = BigDecimal(loc.yaw.toDouble(), c)
        val pitch = BigDecimal(loc.pitch.toDouble(), c)
        val str = StringBuilder()

        if (mode == TagMode.ENTITY) {
            x = BigDecimal(loc.x, c)
            y = BigDecimal(loc.y, c)
            z = BigDecimal(loc.z, c)
        }
        str.append(x.toString()).append(",").append(y.toString()).append(",").append(z.toString())
        if (mode == TagMode.ENTITY) {
            str.append(",").append(yaw).append(",").append(pitch)
        }
        return str.toString()
    }

    private fun deserialize(str: String) : Location {
        if (map.mapID == null || map.world == null)
            throw MapNotFound("Unable to load blocktag: Map unavailable.")

        val arr = str.split(',', ignoreCase = false, limit = 5).toTypedArray()
        val c = MathContext(2)
        val x = BigDecimal(arr[0], c).toDouble()
        val y = BigDecimal(arr[1], c).toDouble()
        val z = BigDecimal(arr[2], c).toDouble()
        val yaw: Float
        val pitch: Float

        if (mode == TagMode.ENTITY) {
            yaw = BigDecimal(arr[3], c).toFloat()
            pitch = BigDecimal(arr[4], c).toFloat()
            return Location(map.world, x, y, z, yaw, pitch)
        }
        return Location(map.world, x, y, z)
    }

    private fun getKey() : String {
        if (map.mapID == null || map.world == null)
            throw MapNotFound("Unable to load blocktag: Map unavailable.")

        return mode.label.plus(".").plus(name).plus(".").plus(map.mapID)
    }
}

enum class TagMode(val label: String) {
    BLOCK("block"), ENTITY("entity");
}