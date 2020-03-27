package com.github.lazoyoung.craftgames.module.api

import org.bukkit.entity.LivingEntity

interface MobModule {

    fun spawnMob(type: String, spawnTag: String)

    fun spawnMythicMob(name: String, level: Int, spawnTag: String)

    /**
     * Returns [String] representing the NamespacedKey of this [livingEntity].
     *
     * For instance, __minecraft:wither_skeleton__ implies that mob is a Wither skeleton.
     *
     * [See wiki about NamespacedKey](https://minecraft.gamepedia.com/Java_Edition_data_values).
     */
    fun getType(livingEntity: LivingEntity): String

}