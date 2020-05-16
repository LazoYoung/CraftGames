package com.github.lazoyoung.craftgames.api.module

import com.github.lazoyoung.craftgames.internal.exception.DependencyNotFound
import com.github.lazoyoung.craftgames.internal.exception.FaultyConfiguration
import com.github.lazoyoung.craftgames.internal.exception.MapNotFound
import org.bukkit.NamespacedKey
import org.bukkit.entity.EntityType
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Mob
import org.bukkit.loot.LootTable
import java.util.concurrent.CompletableFuture
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
     * @param type Type of [Mob]s to be spawned.
     * @param name Custom name.
     * @param loot The [LootTable] which defines the items to drop upon death.
     * Use [ItemModule.getLootTable] to get a loot table.
     * @param tagName The name of coordinate tag which designates their spawnpoint.
     * @throws FaultyConfiguration is thrown if [tagName] is not a valid Tag.
     * @throws IllegalArgumentException is thrown if [type] doesn't indicate any type of Mob.
     * @throws RuntimeException is thrown if the specified Mob is not spawn-able.
     * @throws MapNotFound is thrown if world is not yet loaded.
     */
    fun spawnMob(type: String, name: String?, loot: LootTable?, tagName: String): CompletableFuture<Int>

    /**
     * Spawn MythicMobs.
     *
     * @param name Name of the MythicMob(s) to be spawned.
     * @param level Initial level of the MythicMob(s).
     * @param tagName The name of coordinate tag which designates their spawnpoint.
     * @throws FaultyConfiguration is thrown if [tagName] is not a valid Spawnpoint Tag.
     * @throws IllegalArgumentException is thrown if [name] doesn't indicate any type of MythicMob.
     * @throws MapNotFound is thrown if world is not yet loaded.
     * @throws DependencyNotFound is thrown if MythicMobs is not installed.
     */
    fun spawnMythicMob(name: String, level: Int, tagName: String): CompletableFuture<Int>

    /**
     * Spawn NPC with specific [type] at the position where [tag][tagName] indicates.
     *
     * @param name Name of this NPC.
     * @param type Entity type of this NPC.
     * @param assignment (Optional) Name of Denizen script assignment.
     * @param tagName Name of the coordinate tag which designates their spawnpoint.
     * @throws FaultyConfiguration is thrown if [tagName] is not a valid Spawnpoint Tag.
     * @throws MapNotFound is thrown if world is not yet loaded.
     * @throws DependencyNotFound is thrown if Citizens is not installed.
     */
    fun spawnNPC(name: String, type: EntityType, assignment: String?, tagName: String): CompletableFuture<Int>

    /**
     * Spawn Player NPC at the position where [tag][tagName] indicates.
     *
     * @param name Name of this NPC.
     * @param skinURL (Optional) URL of skin file. Link must be available for download.
     * @param assignment (Optional) Name of Denizen script assignment.
     * @param tagName Name of the coordinate tag which designates their spawnpoint.
     * @throws FaultyConfiguration is thrown if [tagName] is not a valid Spawnpoint Tag.
     * @throws MapNotFound is thrown if world is not yet loaded.
     * @throws DependencyNotFound is thrown if Citizens is not installed.
     */
    fun spawnPlayerNPC(name: String, skinURL: String?, assignment: String?, tagName: String): CompletableFuture<Int>

    /**
     * Despawn specific [type][EntityType] of entities.
     *
     * @return Number of entities despawned.
     * @throws MapNotFound is thrown if world is not yet loaded.
     */
    fun despawnEntities(type: EntityType): Int

    /**
     * Despawn MythicMobs matching with [name].
     *
     * @return Number of entities despawned.
     * @throws MapNotFound is thrown if world is not yet loaded.
     * @throws DependencyNotFound is thrown if MythicMobs is not installed.
     */
    fun despawnMythicMobs(name: String): Int

}