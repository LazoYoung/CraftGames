package com.github.lazoyoung.craftgames.module.service

import com.github.lazoyoung.craftgames.coordtag.SpawnCapture
import com.github.lazoyoung.craftgames.exception.DependencyNotFound
import com.github.lazoyoung.craftgames.game.Game
import com.github.lazoyoung.craftgames.module.Module
import com.github.lazoyoung.craftgames.module.api.MobModule
import io.lumine.xikage.mythicmobs.MythicMobs
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.EntityType

class MobModuleImpl(val game: Game) : MobModule {

    override fun spawnMob(type: String, spawnTag: String) {
        val c = Module.getSpawnTag(game, spawnTag).getLocalCaptures().random() as SpawnCapture
        val entity = EntityType.valueOf(type.toUpperCase().replace(' ', '_'))
        val loc = Location(game.map.world!!, c.x, c.y, c.z, c.yaw, c.pitch)

        game.map.world!!.spawnEntity(loc, entity)
    }

    override fun spawnMythicMob(name: String, level: Int, spawnTag: String) {
        if (Bukkit.getPluginManager().getPlugin("MythicMobs") == null)
            throw DependencyNotFound("MythicMobs plugin is required to use this function.")

        // TODO MythicMobs should be referred via Reflection to eliminate local dependency
        val c = Module.getSpawnTag(game, spawnTag).getLocalCaptures().random() as SpawnCapture
        val loc = Location(game.map.world!!, c.x, c.y, c.z, c.yaw, c.pitch)
        val mmAPI = MythicMobs.inst().apiHelper
        mmAPI.spawnMythicMob(name, loc, level)
    }

}