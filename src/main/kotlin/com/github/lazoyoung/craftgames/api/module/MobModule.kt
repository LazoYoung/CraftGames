package com.github.lazoyoung.craftgames.api.module

import com.github.lazoyoung.craftgames.internal.exception.FaultyConfiguration
import com.github.lazoyoung.craftgames.internal.exception.MapNotFound
import org.bukkit.NamespacedKey
import org.bukkit.entity.EntityType
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Mob
import org.bukkit.loot.LootTable
import java.util.function.Consumer

interface MobModule {

    /**
     * Returns the [NamespacedKey] assigned to this [livingEntity].
     *
     * [See wiki about NamespacedKey](https://minecraft.gamepedia.com/Java_Edition_data_values).
     */
    fun getNamespacedKey(livingEntity: LivingEntity): NamespacedKey

    /**
     * Inspect which [Mob]s are inside the area.
     *
     * @param areaTag Name of the coordinate tag which designates the area.
     * @param callback Callback function that will accept the result
     * ([List] of mobs inside) once the process is completed.
     */
    fun getMobsInside(areaTag: String, callback: Consumer<List<Mob>>)

    /**
     * Set [max] number of mobs that can be spawned.
     * (Defaults to 100)
     */
    fun setMobCapacity(max: Int)

    /**
     * Spawn vanilla mobs.
     *
     * @param type Type of [mobs][Mob]s to be spawned.
     * @param spawnTag Name of the coordinate tag which designates their spawnpoint.
     * @throws FaultyConfiguration is thrown if [spawnTag] is not a valid Tag.
     * @throws IllegalArgumentException is thrown if [type] doesn't indicate any type of Mob.
     * @throws RuntimeException is thrown if the specified Mob is not spawn-able.
     * @throws MapNotFound is thrown if world is not yet loaded.
     */
    fun spawnMob(type: String, spawnTag: String): List<Mob>

    /**
     * Spawn vanilla mobs.
     *
     * @param type Type of [mobs][Mob]s to be spawned.
     * @param name Custom name.
     * @param spawnTag Name of the coordinate tag which designates their spawnpoint.
     * @throws FaultyConfiguration is thrown if [spawnTag] is not a valid Tag.
     * @throws IllegalArgumentException is thrown if [type] doesn't indicate any type of Mob.
     * @throws RuntimeException is thrown if the specified Mob is not spawn-able.
     * @throws MapNotFound is thrown if world is not yet loaded.
     */
    fun spawnMob(type: String, name: String, spawnTag: String): List<Mob>

    /**
     * Spawn vanilla mobs.
     *
     * @param type Type of [mobs][Mob]s to be spawned.
     * @param loot The [LootTable] which defines the items to drop upon death.
     * Use [ItemModule.getLootTable] to get a loot table.
     * @param spawnTag The name of coordinate tag which designates their spawnpoint.
     * @throws FaultyConfiguration is thrown if [spawnTag] is not a valid Tag.
     * @throws IllegalArgumentException is thrown if [type] doesn't indicate any type of Mob.
     * @throws RuntimeException is thrown if the specified Mob is not spawn-able.
     * @throws MapNotFound is thrown if world is not yet loaded.
     */
    fun spawnMob(type: String, loot: LootTable, spawnTag: String): List<Mob>

    /**
     * Spawn vanilla mobs.
     *
     * @param type Type of [mobs][Mob]s to be spawned.
     * @param name Custom name.
     * @param loot The [LootTable] which defines the items to drop upon death.
     * Use [ItemModule.getLootTable] to get a loot table.
     * @param spawnTag The name of coordinate tag which designates their spawnpoint.
     * @throws FaultyConfiguration is thrown if [spawnTag] is not a valid Tag.
     * @throws IllegalArgumentException is thrown if [type] doesn't indicate any type of Mob.
     * @throws RuntimeException is thrown if the specified Mob is not spawn-able.
     * @throws MapNotFound is thrown if world is not yet loaded.
     */
    fun spawnMob(type: String, name: String, loot: LootTable, spawnTag: String): List<Mob>

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
     * Despawn specific [type][EntityType] of entities.
     *
     * @return Number of entities despawned.
     */
    fun despawnEntities(type: EntityType): Int

    /**
     * Despawn MythicMobs matching with [name].
     *
     * @return Number of entities despawned.
     */
    fun despawnMythicMobs(name: String): Int

}