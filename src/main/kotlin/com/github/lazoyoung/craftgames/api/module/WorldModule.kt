package com.github.lazoyoung.craftgames.api.module

import com.github.lazoyoung.craftgames.internal.exception.MapNotFound
import org.bukkit.GameRule
import org.bukkit.World
import org.bukkit.WorldBorder
import org.bukkit.entity.Player
import org.bukkit.loot.LootTable
import java.util.function.Consumer

interface WorldModule {

    /**
     * Get map ID of the current world.
     */
    fun getMapID(): String

    /**
     * Get [WorldBorder] of the current world.
     *
     * @throws MapNotFound is thrown if world is not yet loaded.
     */
    fun getWorldBorder(): WorldBorder

    fun setAreaTrigger(tag: String, task: Consumer<Player>?)

    /**
     * Set world weather to [storm] or not.
     */
    fun setStormyWeather(storm: Boolean)

    /**
     * Get world time.
     *
     * See [World.getTime] for relative time.
     *
     * See [World.getFullTime] for absolute time.
     */
    fun getTime(absolute: Boolean): Long

    /**
     * Add up the amount of [time] to this world.
     */
    fun addTime(time: Long)

    /**
     * Set world time.
     *
     * See [World.setTime] for relative time.
     *
     * See [World.setFullTime] for absolute time.
     */
    fun setTime(time: Long, absolute: Boolean)

    /**
     * Set world [GameRule].
     */
    fun <T> setGameRule(rule: GameRule<T>, value: T)

    fun fillContainers(tag: String, loot: LootTable)

    /**
     * Place blocks by reading a schematic file where the [path] points to.
     * The blocks will be __placed relative to the coordinate of [tag]__.
     *
     * @param tag The target(s) of blocks to be placed.
     *   If tag has multiple captures, the same amount of clones will be generated.
     * @param path Path to schematic file. Root directory is same as where layout.yml resides.
     * @param biomes Whether or not to copy biomes.
     * @param entities Whether or not to copy entities.
     * @param ignoreAir Whether or not to ignore air blocks.
     */
    fun placeSchematics(tag: String, path: String, biomes: Boolean, entities: Boolean, ignoreAir: Boolean)

}