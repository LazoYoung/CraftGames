package com.github.lazoyoung.craftgames.api.module

import com.github.lazoyoung.craftgames.api.shopkeepers.GameShopkeeper
import com.github.lazoyoung.craftgames.api.tag.coordinate.CoordTag
import com.github.lazoyoung.craftgames.impl.exception.DependencyNotFound
import com.github.lazoyoung.craftgames.impl.exception.MapNotFound
import com.nisovin.shopkeepers.api.shopkeeper.ShopkeeperCreateException
import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.entity.Entity
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
     * @param areaTag Coordinate tag which designates the area.
     * @param callback Callback function that will accept the result
     * ([List] of mobs inside) once the process is completed.
     */
    @Deprecated("Redundant function. Use WorldModule.getEntitiesInside()")
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