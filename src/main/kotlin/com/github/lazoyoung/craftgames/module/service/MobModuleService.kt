package com.github.lazoyoung.craftgames.module.service

import com.github.lazoyoung.craftgames.coordtag.SpawnCapture
import com.github.lazoyoung.craftgames.exception.DependencyNotFound
import com.github.lazoyoung.craftgames.exception.FaultyConfiguration
import com.github.lazoyoung.craftgames.game.Game
import com.github.lazoyoung.craftgames.module.Module
import com.github.lazoyoung.craftgames.module.api.MobModule
import io.lumine.xikage.mythicmobs.MythicMobs
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.EntityType
import org.bukkit.entity.LivingEntity

class MobModuleService internal constructor(val game: Game) : MobModule {

    override fun spawnMob(type: String, spawnTag: String) {
        val capture = Module.getSpawnTag(game, spawnTag).getLocalCaptures()

        if (capture.isEmpty())
            throw FaultyConfiguration("Tag $spawnTag has no capture in map: ${game.map.id}")

        capture.forEach {
            it as SpawnCapture
            val entity = EntityType.valueOf(type.toUpperCase().replace(' ', '_'))
            val loc = Location(game.map.world!!, it.x, it.y, it.z, it.yaw, it.pitch)

            game.map.world!!.spawnEntity(loc, entity)
        }
    }

    override fun spawnMythicMob(name: String, level: Int, spawnTag: String) {
        if (Bukkit.getPluginManager().getPlugin("MythicMobs") == null)
            throw DependencyNotFound("MythicMobs plugin is required to use this function.")

        // TODO MythicMobs should be referred via Reflection to eliminate local dependency
        val capture = Module.getSpawnTag(game, spawnTag).getLocalCaptures()

        if (capture.isEmpty())
            throw FaultyConfiguration("Tag $spawnTag has no capture in map: ${game.map.id}")

        capture.forEach {
            it as SpawnCapture
            val loc = Location(game.map.world!!, it.x, it.y, it.z, it.yaw, it.pitch)
            val mmAPI = MythicMobs.inst().apiHelper
            mmAPI.spawnMythicMob(name, loc, level)
        }
    }

    override fun getType(livingEntity: LivingEntity): String {
        return livingEntity.type.key.toString()
    }

}