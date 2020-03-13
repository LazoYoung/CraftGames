package com.github.lazoyoung.craftgames.game

import com.github.lazoyoung.craftgames.exception.MapNotFound
import org.bukkit.Location

class BlockTag(private val map: GameMap, private val group: String, private val mode: TagMode) {
    private enum class Point(val label: String) {
        FIRST("1"), SECOND("2")
    }

    fun addSingularPosition(pos: Location) {
        map.tagConfig.set(getKey(), pos)
    }

    fun addCuboidArea(p1: Location, p2: Location) {
        map.tagConfig.set(getKey(Point.FIRST), p1)
        map.tagConfig.set(getKey(Point.SECOND), p2)
    }

    fun getSingularPosition() : Location? {
        return map.tagConfig.getLocation(getKey())
        /*
        if (arr.isNullOrEmpty())
            throw FaultyConfiguration("Block tag $groupName is not defined for: " +
                    "Game: ${map.game.name}, Map: ${map.mapID}")
         */
    }

    fun getCuboidArea() : List<Location> {
        val p1 = map.tagConfig.getLocation(getKey(Point.FIRST))
        val p2 = map.tagConfig.getLocation(getKey(Point.SECOND))
        val loc = ArrayList<Location>()

        if (p1 == null || p2 == null)
            return emptyList()

        for (x in p1.blockX.rangeTo(p2.blockX)) {
            for (y in p1.blockY.rangeTo(p2.blockY)) {
                for (z in p1.blockZ.rangeTo(p2.blockZ)) {
                    loc.add(Location(map.world, x.toDouble(), y.toDouble(), z.toDouble()))
                }
            }
        }
        return loc.toList()
    }

    private fun getKey() : String {
        if (map.mapID == null || map.world == null)
            throw MapNotFound("Unable to load blocktag: Map unavailable.")

        return mode.label.plus(".").plus(group).plus(".").plus(map.mapID)
    }

    private fun getKey(point: Point) : String {
        return getKey().plus(".").plus(point.label)
    }
}

enum class TagMode(val label: String) {
    SINGULAR("singular"), CUBOID("cuboid");
}