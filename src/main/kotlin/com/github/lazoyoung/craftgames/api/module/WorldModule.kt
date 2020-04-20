package com.github.lazoyoung.craftgames.api.module

import com.github.lazoyoung.craftgames.internal.exception.MapNotFound
import org.bukkit.*
import org.bukkit.loot.LootTable
import org.bukkit.loot.Lootable

interface WorldModule {

    /**
     * Get current map ID.
     */
    fun getMapID(): String

    /**
     * Get [WorldBorder] of this world.
     *
     * @throws MapNotFound is thrown if world is not yet loaded.
     */
    fun getWorldBorder(): WorldBorder

    /**
     * Set center of [WorldBorder] for this world.
     *
     * @param blockTag Name of the block coordinate tag which represents the center of border.
     * @param index Index of capture (This is optional).
     * @throws IllegalArgumentException is thrown if [blockTag]
     * does not indicate a block coordinate in this world.
     * @throws MapNotFound is thrown if world is not yet loaded.
     */
    fun setBorderCenter(blockTag: String, index: Int = 0)

    /**
     * Set [max] number of mobs in this world.
     * (Defaults to 100)
     */
    @Deprecated("Moved to MobModule.",
            ReplaceWith("MobModule.setMobCapacity(Int)", "com.github.lazoyoung.craftgames.api.module.MobModule"))
    fun setMobCapacity(max: Int)

    /**
     * Set difficulty for every world.
     * (Defaults to [Difficulty.NORMAL])
     *
     * @param difficulty The new difficulty
     */
    fun setDifficulty(difficulty: Difficulty)

    /**
     * Set world weather to [storm] or not.
     *
     * @throws MapNotFound is thrown if world is not generated yet.
     */
    fun setStormyWeather(storm: Boolean)

    /**
     * Get world time.
     *
     * See [World.getTime] for relative time.
     *
     * See [World.getFullTime] for absolute time.
     *
     * @param absolute If true, absolute time is returned. Relative in otherwise.
     * @throws MapNotFound is thrown if world is not generated yet.
     */
    fun getTime(absolute: Boolean): Long

    /**
     * Add up the amount of [time] to this world.
     *
     * @throws MapNotFound is thrown if world is not generated yet.
     */
    fun addTime(time: Long)

    /**
     * Set time for this world.
     *
     * See [World.setTime] for relative time.
     *
     * See [World.setFullTime] for absolute time.
     *
     * @param time New time.
     * @param absolute If true, absolute time is returned. Relative in otherwise.
     * @throws MapNotFound is thrown if world is not generated yet.
     */
    fun setTime(time: Long, absolute: Boolean)

    /**
     * Change Minecraft [GameRule].
     *
     * @param rule The name of rule to change
     * @param value The new value to apply
     * @throws IllegalArgumentException is thrown if [rule] doesn't indicate a GameRule.
     */
    fun setGameRule(rule: String, value: Any)

    /**
     * Fill a [container][Lootable] block with loot table.
     *
     * @param blockTag designates the container location.
     * @param loot describes the contents of items to fill.
     * @throws MapNotFound is thrown if world is not generated yet.
     */
    fun fillContainers(blockTag: String, loot: LootTable)

    /**
     * Fill blocks at the spot pointed by a [coordinate tag][tag].
     *
     * @param tag Name of a Block or Area tag
     * @param material Type of blocks to fill
     * @throws IllegalArgumentException is thrown if tag is irrelevant.
     */
    fun fillBlocks(tag: String, material: Material)

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
     * @throws MapNotFound is thrown if world is not generated yet.
     */
    fun placeSchematics(tag: String, path: String, biomes: Boolean, entities: Boolean, ignoreAir: Boolean)

}