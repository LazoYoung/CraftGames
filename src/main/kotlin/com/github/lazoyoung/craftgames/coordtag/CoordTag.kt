package com.github.lazoyoung.craftgames.coordtag

import com.github.lazoyoung.craftgames.game.Game
import java.math.BigDecimal
import java.math.MathContext

abstract class CoordTag(val x: Double, val y: Double, val z: Double) {
    companion object {
        /**
         * Gets all tags by reading through coordinate_tags.yml
         * @param mode is used to filter the result.
         * @return The list of tag names
         */
        fun getTagNames(game: Game, mode: TagMode? = null): MutableList<String> {
            if (mode == null) {
                val names = getTagNames(game, TagMode.BLOCK)
                names.addAll(getTagNames(game, TagMode.ENTITY))
                return names
            }

            return game.tagConfig.getConfigurationSection(mode.label)
                    ?.getKeys(false)
                    ?.toMutableList()
                    ?: mutableListOf()
        }

        /**
         * Gets all the captures inside the given tag. A capture is fundamental unit of CoordinateTag.
         * Each parameter is a working filter. Pass relevant arguments that fit your need.
         * @param mode Filter out all except the ones matching this tag mode.
         * @param name Filter out all except the ones matching this tag name.
         * @param mapID Filter out all except the ones inside the given map.
         * @return A list of captures(coordinates) that is the outcome of this query.
         */
        fun getCaptures(game: Game, mode: TagMode? = null, name: String? = null, mapID: String? = null): List<CoordTag> {
            if (mode == null)
                return getCaptures(game, TagMode.BLOCK, name, mapID).plus(getCaptures(game, TagMode.ENTITY, name, mapID))

            if (name == null)
                return getTagNames(game, mode).flatMap { getCaptures(game, mode, it, mapID) }

            if (mapID == null)
                return game.map.getMapList().flatMap { getCaptures(game, mode, name, it) }

            return game.tagConfig.getStringList(getKey(mode, name, mapID)).map { deserialize(mode, it) }
        }

        internal fun getKey(mode: TagMode, name: String, mapID: String) : String {
            return mode.label.plus(".").plus(name).plus(".").plus(mapID)
        }

        private fun deserialize(mode: TagMode, str: String) : CoordTag {
            val arr = str.split(',', ignoreCase = false, limit = 5).toTypedArray()
            val c = MathContext(2)
            val x = BigDecimal(arr[0], c).toDouble()
            val y = BigDecimal(arr[1], c).toDouble()
            val z = BigDecimal(arr[2], c).toDouble()
            val yaw: Float
            val pitch: Float

            if (mode == TagMode.ENTITY) {
                pitch = BigDecimal(arr[3], c).toFloat()
                yaw = BigDecimal(arr[4], c).toFloat()
                return EntityCapture(x, y, z, yaw, pitch)
            }
            return BlockCapture(x, y, z)
        }
    }

    abstract fun add(game: Game, name: String, mapID: String)
    abstract fun serialize() : String
}

enum class TagMode(val label: String) {
    BLOCK("block"), ENTITY("entity");
}