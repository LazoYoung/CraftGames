package com.github.lazoyoung.craftgames.api.module

import com.github.lazoyoung.craftgames.internal.exception.FaultyConfiguration
import com.github.lazoyoung.craftgames.internal.exception.MapNotFound
import org.bukkit.NamespacedKey
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Mob
import org.bukkit.loot.LootTable

interface MobModule {

    /**
     * Spawn vanilla mobs.
     *
     * @param type Type of [mobs][Mob]s to be spawned.
     * @param spawnTag The name of coordinate tag which designates their spawnpoint.
     * @throws FaultyConfiguration is thrown if [spawnTag] is not a valid Spawnpoint Tag.
     * @throws IllegalArgumentException is thrown if [type] doesn't indicate any type of Mob.
     * @throws RuntimeException is thrown if the specified Mob is not spawn-able.
     * @throws MapNotFound is thrown if world is not yet loaded.
     */
    fun spawnMob(type: String, spawnTag: String): List<Mob>

    /**
     * Spawn vanilla mobs.
     *
     * @param type Type of [mobs][Mob]s to be spawned.
     * @param loot The [LootTable] which defines the items to drop upon death.
     * Use [ItemModule.getLootTable] to get a loot table.
     * @param spawnTag The name of coordinate tag which designates their spawnpoint.
     * @throws FaultyConfiguration is thrown if [spawnTag] is not a valid Spawnpoint Tag.
     * @throws IllegalArgumentException is thrown if [type] doesn't indicate any type of Mob.
     * @throws RuntimeException is thrown if the specified Mob is not spawn-able.
     * @throws MapNotFound is thrown if world is not yet loaded.
     */
    fun spawnMob(type: String, loot: LootTable, spawnTag: String): List<Mob>

    /**
     * Spawn MythicMobs.
     *
     * @param name Name of the MythicMob(s) to be spawned.
     * @param level Initial level of the MythicMob(s).
     * @param spawnTag The name of coordinate tag which designates their spawnpoint.
     * @throws FaultyConfiguration is thrown if [spawnTag] is not a valid Spawnpoint Tag.
     * @throws IllegalArgumentException is thrown if [name] doesn't indicate any type of MythicMob.
     * @throws RuntimeException is thrown if plugin fails to access MythicMobs API.
     * @throws MapNotFound is thrown if world is not yet loaded.
     */
    fun spawnMythicMob(name: String, level: Int, spawnTag: String): List<Mob>

    /**
     * Spawn MythicMobs.
     *
     * @param name Name of the MythicMob(s) to be spawned.
     * @param level Initial level of the MythicMob(s).
     * @param loot The [LootTable] which defines the items to drop upon death.
     * Use [ItemModule.getLootTable] to get a loot table.
     * @param spawnTag The name of coordinate tag which designates their spawnpoint.
     * @throws FaultyConfiguration is thrown if [spawnTag] is not a valid Spawnpoint Tag.
     * @throws IllegalArgumentException is thrown if [name] doesn't indicate any type of MythicMob.
     * @throws MapNotFound is thrown if world is not yet loaded.
     */
    fun spawnMythicMob(name: String, level: Int, loot: LootTable, spawnTag: String): List<Mob>

    /**
     * Returns the [NamespacedKey] assigned to this [livingEntity].
     *
     * [See wiki about NamespacedKey](https://minecraft.gamepedia.com/Java_Edition_data_values).
     */
    fun getNamespacedKey(livingEntity: LivingEntity): NamespacedKey

}