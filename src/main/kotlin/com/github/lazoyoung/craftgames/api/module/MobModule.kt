package com.github.lazoyoung.craftgames.api.module

import com.github.lazoyoung.craftgames.api.shopkeepers.GameShopkeeper
import com.github.lazoyoung.craftgames.api.tag.coordinate.CoordTag
import com.github.lazoyoung.craftgames.impl.exception.DependencyNotFound
import com.github.lazoyoung.craftgames.impl.exception.FaultyConfiguration
import com.github.lazoyoung.craftgames.impl.exception.MapNotFound
import com.nisovin.shopkeepers.api.shopkeeper.ShopkeeperCreateException
import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.entity.Entity
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
    @Deprecated("Direct use of CoordTag is encouraged.", ReplaceWith("getMobsInside(CoordTag, Consumer)"))
    fun getMobsInside(areaTag: String, callback: Consumer<List<Mob>>)

    /**
     * Inspect which [Mob]s are inside the area.
     *
     * @param areaTag Coordinate tag which designates the area.
     * @param callback Callback function that will accept the result
     * ([List] of mobs inside) once the process is completed.
     */
    fun getMobsInside(areaTag: CoordTag, callback: Consumer<List<Mob>>)

    /**
     * Get [GameShopkeeper] by [entity].
     *
     * @throws IllegalArgumentException is raised if this [entity] is not a [GameShopkeeper]
     * @throws DependencyNotFound is raised if Shopkeepers is not installed.
     * @see [makeShopkeeper]
     */
    fun getShopkeeper(entity: Entity): GameShopkeeper

    /**
     * Convert this [entity] into a [GameShopkeeper].
     *
     * @throws IllegalArgumentException is raised if this [entity] cannot be converted.
     * @throws DependencyNotFound is raised if Shopkeepers is not installed.
     * @throws ShopkeeperCreateException can be thrown during conversion.
     */
    fun makeShopkeeper(entity: Entity): GameShopkeeper

    /**
     * Set [max] number of mobs that can be spawned.
     * (Defaults to 100)
     */
    @Deprecated("It actually has no use.")
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
    @Deprecated("Direct use of CoordTag is encouraged.",
            ReplaceWith("spawnMob(String, String?, LootTable?, CoordTag)"))
    fun spawnMob(type: String, name: String?, loot: LootTable?, tagName: String): CompletableFuture<Int>

    /**
     * Spawn vanilla mob at [location].
     *
     * @param type Type of [Mob]s to be spawned.
     * @param name Custom name.
     * @param loot The [LootTable] which defines the items to drop upon death.
     * Use [ItemModule.getLootTable] to get a loot table.
     * @param location Location of spawnpoint.
     * @return [Mob] that is spawned.
     * @throws IllegalArgumentException is thrown if [type] doesn't indicate any type of Mob.
     * @throws RuntimeException is thrown if the specified Mob is not spawn-able.
     * @throws MapNotFound is thrown if world is not yet loaded.
     */
    fun spawnMob(type: String, name: String?, loot: LootTable?, location: Location): Mob

    /**
     * Spawn vanilla mob at arbitrary location.
     * Location is randomly picked among the ones captured by [tag].
     *
     * @param type Type of [Mob]s to be spawned.
     * @param name Custom name.
     * @param loot The [LootTable] which defines the items to drop upon death.
     * Use [ItemModule.getLootTable] to get a loot table.
     * @param tag [CoordTag] that captures spawnpoint(s).
     * @return [Mob] that is spawned.
     * @throws IllegalArgumentException is thrown if [type] doesn't indicate any type of Mob.
     * @throws MapNotFound is thrown if world is not yet loaded.
     */
    fun spawnMob(type: String, name: String?, loot: LootTable?, tag: CoordTag): Mob

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
    @Deprecated("Direct use of CoordTag is encouraged.", ReplaceWith("spawnMythicMob(String, Int, CoordTag)"))
    fun spawnMythicMob(name: String, level: Int, tagName: String): CompletableFuture<Int>

    /**
     * Spawn MythicMob at [location].
     *
     * @param name Name of the MythicMob(s) to be spawned.
     * @param level Initial level of the MythicMob(s).
     * @param location Location of spawnpoint.
     * @return [Entity] that is spawned.
     * @throws IllegalArgumentException is thrown if [name] doesn't indicate any type of MythicMob.
     * @throws MapNotFound is thrown if world is not yet loaded.
     * @throws DependencyNotFound is thrown if MythicMobs is not installed.
     * @throws ReflectiveOperationException
     */
    fun spawnMythicMob(name: String, level: Int, location: Location): Entity

    /**
     * Spawn one MythicMob at arbitrary location.
     * Location is randomly picked among the ones captured by [tag].
     *
     * @param name Name of the MythicMob(s) to be spawned.
     * @param level Initial level of the MythicMob(s).
     * @param tag [CoordTag] that captures spawnpoint(s).
     * @return [Entity] that is spawned.
     * @throws IllegalArgumentException is thrown if [name] doesn't indicate any type of MythicMob.
     * @throws MapNotFound is thrown if world is not yet loaded.
     * @throws DependencyNotFound is thrown if MythicMobs is not installed.
     * @throws RuntimeException is thrown if spawn area cannot be resolved.
     * @throws ReflectiveOperationException
     */
    fun spawnMythicMob(name: String, level: Int, tag: CoordTag): Entity

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
    @Deprecated("Direct use of CoordTag is encouraged.", ReplaceWith("spawnNPC(String, EntityType, String?, CoordTag)"))
    fun spawnNPC(name: String, type: EntityType, assignment: String?, tagName: String): CompletableFuture<Int>

    /**
     * Spawn NPC at [location].
     *
     * @param name Name of this NPC.
     * @param type Entity type of this NPC.
     * @param assignment (Optional) Name of Denizen script assignment.
     * @param location Location of spawnpoint.
     * @return [Entity] that is spawned.
     * @throws MapNotFound is thrown if world is not yet loaded.
     * @throws DependencyNotFound is thrown if Citizens is not installed.
     */
    fun spawnNPC(name: String, type: EntityType, assignment: String?, location: Location): Entity

    /**
     * Spawn NPC at arbitrary location.
     * Location is randomly picked among the ones captured by [tag].
     *
     * @param name Name of this NPC.
     * @param type Entity type of this NPC.
     * @param assignment (Optional) Name of Denizen script assignment.
     * @param tag [CoordTag] that captures spawnpoint(s).
     * @return [Entity] that is spawned.
     * @throws IllegalArgumentException is raised if [tag] mode is not relevant.
     * @throws MapNotFound is thrown if world is not yet loaded.
     * @throws RuntimeException is thrown if spawn area cannot be resolved.
     * @throws DependencyNotFound is thrown if Citizens is not installed.
     */
    fun spawnNPC(name: String, type: EntityType, assignment: String?, tag: CoordTag): Entity

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
    @Deprecated("Direct use of CoordTag is encouraged.", ReplaceWith("spawnPlayerNPC(String, String?, String?, CoordTag)"))
    fun spawnPlayerNPC(name: String, skinURL: String?, assignment: String?, tagName: String): CompletableFuture<Int>

    /**
     * Spawn Player-typed NPC at [location].
     *
     * @param name Name of this NPC.
     * @param skinURL (Optional) URL of skin file. Link must be available for download.
     * @param assignment (Optional) Name of Denizen script assignment.
     * @param location Location of spawnpoint.
     * @return [Entity] that is spawned.
     * @throws MapNotFound is thrown if world is not yet loaded.
     * @throws DependencyNotFound is thrown if Citizens is not installed.
     */
    fun spawnPlayerNPC(name: String, skinURL: String?, assignment: String?, location: Location): Entity

    /**
     * Spawn Player-typed NPC at arbitrary location.
     * Location is randomly picked among the ones captured by [tag].
     *
     * @param name Name of this NPC.
     * @param skinURL (Optional) URL of skin file. Link must be available for download.
     * @param assignment (Optional) Name of Denizen script assignment.
     * @param tag [CoordTag] that captures spawnpoint(s).
     * @return [Entity] that is spawned.
     * @throws MapNotFound is thrown if world is not yet loaded.
     * @throws RuntimeException is thrown if spawn area cannot be resolved.
     * @throws DependencyNotFound is thrown if Citizens is not installed.
     */
    fun spawnPlayerNPC(name: String, skinURL: String?, assignment: String?, tag: CoordTag): Entity

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