package com.github.lazoyoung.craftgames.coordtag

import com.github.lazoyoung.craftgames.game.GameMap

class BlockCapture(
        map: GameMap,
        x: Int,
        y: Int,
        z: Int
) : CoordTag(map, x.toDouble(), y.toDouble(), z.toDouble()) {

    override fun serialize() : String {
        return StringBuilder().append(x.toString()).append(",")
                .append(y.toString()).append(",").append(z.toString()).toString()
    }

    override fun saveCapture(tagName: String) {
        val key = getKey(TagMode.BLOCK, tagName, map.mapID!!)
        val result = map.game.tagConfig.getStringList(key)
        result.add(serialize())
        map.game.tagConfig.set(key, result)
    }

}