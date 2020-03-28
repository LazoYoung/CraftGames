package com.github.lazoyoung.craftgames.module.api

import org.bukkit.WorldBorder
import org.bukkit.entity.Player
import java.util.function.Consumer

interface WorldModule {

    fun setAreaTrigger(tag: String, task: Consumer<Player>?)

    fun getWorldBorder(): WorldBorder

    /**
     * Place blocks by reading a schematic file where the [path] points to.
     * The blocks will be __placed relative to the coordinate of [tag]__.
     *
     * @param tag The target(s) of blocks to be placed.
     *  If tag has multiple captures, the same amount of clones will be generated.
     * @param path Path to schematic file. Root directory is same as where layout.yml resides.
     * @param biomes Whether or not to copy biomes.
     * @param entities Whether or not to copy entities.
     * @param ignoreAir Whether or not to ignore air blocks.
     */
    fun placeSchematics(tag: String, path: String, biomes: Boolean, entities: Boolean, ignoreAir: Boolean)

}