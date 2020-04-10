package com.github.lazoyoung.craftgames.game.module

import com.github.lazoyoung.craftgames.api.module.MobModule
import com.github.lazoyoung.craftgames.coordtag.capture.SpawnCapture
import com.github.lazoyoung.craftgames.coordtag.tag.TagMode
import com.github.lazoyoung.craftgames.game.Game
import com.github.lazoyoung.craftgames.internal.exception.DependencyNotFound
import com.github.lazoyoung.craftgames.internal.exception.FaultyConfiguration
import com.github.lazoyoung.craftgames.internal.exception.MapNotFound
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.entity.Entity
import org.bukkit.entity.EntityType
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Mob
import org.bukkit.loot.LootTable
import kotlin.random.Random

class MobModuleService internal constructor(private val game: Game) : MobModule {

    private val script = game.resource.script

    override fun spawnMob(type: String, spawnTag: String): List<Mob> {
        val mapID = game.map.id
        val world = game.map.world ?: throw MapNotFound()
        val capture = Module.getRelevantTag(game, spawnTag, TagMode.SPAWN).getCaptures(mapID)
        val mobList = ArrayList<Mob>()
        var typeKey: NamespacedKey? = null

        if (capture.isEmpty()) {
            throw FaultyConfiguration("Tag $spawnTag has no capture in map: $mapID")
        }

        capture.forEach {
            it as SpawnCapture
            val entityType = EntityType.valueOf(type.toUpperCase().replace(' ', '_'))
            typeKey = entityType.key
            val loc = Location(game.map.world!!, it.x, it.y, it.z, it.yaw, it.pitch)
            val entity: Entity

            if (!entityType.isSpawnable) {
                throw RuntimeException("Entity is not spawn-able: $typeKey")
            }

            entity = world.spawnEntity(loc, entityType)

            if (entity !is Mob) {
                entity.remove()
                throw IllegalArgumentException("This is not a Mob: $typeKey")
            }

            mobList.add(entity)
        }

        script.printDebug("Spawned ${mobList.size} $typeKey")
        return mobList
    }

    override fun spawnMob(type: String, loot: LootTable, spawnTag: String): List<Mob> {
        val mobList = spawnMob(type, spawnTag)

        mobList.forEach { it.setLootTable(loot, Random.nextLong()) }
        return mobList
    }

    override fun spawnMythicMob(name: String, level: Int, spawnTag: String): List<Mob> {

        if (Bukkit.getPluginManager().getPlugin("MythicMobs") == null) {
            throw DependencyNotFound("MythicMobs is required to spawn custom mobs.")
        }

        val world = game.map.world ?: throw MapNotFound()
        val mapID = game.map.id

        val mythicMobsClass = Class.forName("io.lumine.xikage.mythicmobs.MythicMobs")
        val bukkitAPIHelperClass = Class.forName("io.lumine.xikage.mythicmobs.api.bukkit.BukkitAPIHelper")
        val mythicMobs = mythicMobsClass.getMethod("inst").invoke(null, null)
        val bukkitAPIHelper = mythicMobsClass.getMethod("getAPIHelper").invoke(mythicMobs, null)
        val spawnMethod = bukkitAPIHelperClass.getMethod("spawnMythicMob", String::class.java, Location::class.java, Int::class.java)
        val capture = Module.getRelevantTag(game, spawnTag, TagMode.SPAWN).getCaptures(mapID)
        val mobList = ArrayList<Mob>()
        var typeKey: NamespacedKey? = null

        if (capture.isEmpty()) {
            throw FaultyConfiguration("Tag $spawnTag has no capture in map: $mapID")
        }

        capture.forEach {
            it as SpawnCapture
            val loc = Location(world, it.x, it.y, it.z, it.yaw, it.pitch)
            val entity = spawnMethod.invoke(bukkitAPIHelper, name, loc, level) as Entity
            typeKey = entity.type.key

            if (entity !is Mob) {
                entity.remove()
                throw IllegalArgumentException("This is not a Mob: $typeKey")
            }

            mobList.add(entity)
        }

        script.printDebug("Spawned ${mobList.size} $typeKey")
        return mobList
    }

    override fun spawnMythicMob(name: String, level: Int, loot: LootTable, spawnTag: String): List<Mob> {
        val mobList = spawnMythicMob(name, level, spawnTag)

        mobList.forEach { it.setLootTable(loot, Random.nextLong()) }
        return mobList
    }

        override fun getNamespacedKey(livingEntity: LivingEntity): NamespacedKey {
        return livingEntity.type.key
    }

}