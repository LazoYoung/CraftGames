package com.github.lazoyoung.craftgames.api.module

import com.github.lazoyoung.craftgames.internal.exception.MapNotFound
import io.lumine.xikage.mythicmobs.api.exceptions.InvalidMobTypeException
import org.bukkit.NamespacedKey
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Mob
import org.bukkit.loot.LootTable

interface MobModule {

    /**
     * @throws MapNotFound is thrown if world is not yet loaded.
     */
    fun spawnMob(type: String, spawnTag: String): List<Mob>

    fun spawnMob(type: String, loot: LootTable, spawnTag: String): List<Mob>

    /**
     * @throws InvalidMobTypeException is thrown if [name] doesn't indicate any type of MythicMob.
     */
    fun spawnMythicMob(name: String, level: Int, spawnTag: String): List<Mob>

    fun spawnMythicMob(name: String, level: Int, loot: LootTable, spawnTag: String): List<Mob>

    /**
     * Returns the [NamespacedKey] assigned to this [livingEntity].
     *
     * [See wiki about NamespacedKey](https://minecraft.gamepedia.com/Java_Edition_data_values).
     */
    fun getNamespacedKey(livingEntity: LivingEntity): NamespacedKey

}