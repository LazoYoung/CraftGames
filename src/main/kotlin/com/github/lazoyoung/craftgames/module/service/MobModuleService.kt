package com.github.lazoyoung.craftgames.module.service

import com.github.lazoyoung.craftgames.coordtag.SpawnCapture
import com.github.lazoyoung.craftgames.coordtag.TagMode
import com.github.lazoyoung.craftgames.exception.DependencyNotFound
import com.github.lazoyoung.craftgames.exception.FaultyConfiguration
import com.github.lazoyoung.craftgames.exception.MapNotFound
import com.github.lazoyoung.craftgames.game.Game
import com.github.lazoyoung.craftgames.module.Module
import com.github.lazoyoung.craftgames.module.api.MobModule
import io.lumine.xikage.mythicmobs.MythicMobs
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.entity.Entity
import org.bukkit.entity.EntityType
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Mob
import org.bukkit.loot.LootTable
import kotlin.random.Random

class MobModuleService internal constructor(val game: Game) : MobModule {

    override fun spawnMob(type: String, spawnTag: String): List<Mob> {
        val mapID = game.map.id
        val world = game.map.world ?: throw MapNotFound("World is not loaded yet.")
        val capture = Module.getRelevantTag(game, spawnTag, TagMode.SPAWN).getCaptures(mapID)
        val mobList = ArrayList<Mob>()
        var typeKey: NamespacedKey? = null

        if (capture.isEmpty())
            throw FaultyConfiguration("Tag $spawnTag has no capture in map: $mapID")

        capture.forEach {
            it as SpawnCapture
            val entityType = EntityType.valueOf(type.toUpperCase().replace(' ', '_'))
            typeKey = entityType.key
            val loc = Location(game.map.world!!, it.x, it.y, it.z, it.yaw, it.pitch)
            val entity: Entity

            if (!entityType.isSpawnable) {
                throw IllegalArgumentException("Unable to spawn entity: $typeKey")
            }

            entity = world.spawnEntity(loc, entityType)

            if (entity !is Mob) {
                entity.remove()
                throw IllegalArgumentException("This is not a Mob: $typeKey")
            }

            mobList.add(entity)
        }

        game.resource.script.getLogger()?.println("Spawned ${mobList.size} $typeKey")
        return mobList
    }

    override fun spawnMob(type: String, loot: LootTable, spawnTag: String): List<Mob> {
        val mobList = spawnMob(type, spawnTag)

        mobList.forEach { it.setLootTable(loot, Random.nextLong()) }
        return mobList
    }

    override fun spawnMythicMob(name: String, level: Int, spawnTag: String): List<Mob> {
        if (Bukkit.getPluginManager().getPlugin("MythicMobs") == null)
            throw DependencyNotFound("MythicMobs is required to spawn custom mobs.")

        // TODO MythicMobs should be referred via Reflection to eliminate local dependency
        val mapID = game.map.id
        val capture = Module.getRelevantTag(game, spawnTag, TagMode.SPAWN).getCaptures(mapID)
        val mobList = ArrayList<Mob>()
        var typeKey: NamespacedKey? = null

        if (capture.isEmpty())
            throw FaultyConfiguration("Tag $spawnTag has no capture in map: $mapID")

        capture.forEach {
            it as SpawnCapture
            val loc = Location(game.map.world!!, it.x, it.y, it.z, it.yaw, it.pitch)
            val mmAPI = MythicMobs.inst().apiHelper
            val entity = mmAPI.spawnMythicMob(name, loc, level)
            typeKey = entity.type.key

            if (entity !is Mob) {
                entity.remove()
                throw IllegalArgumentException("This is not a Mob: $typeKey")
            }

            mobList.add(entity)
        }

        game.resource.script.getLogger()?.println("Spawned ${mobList.size} $typeKey")
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