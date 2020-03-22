package com.github.lazoyoung.craftgames.module

import com.github.lazoyoung.craftgames.Main
import com.github.lazoyoung.craftgames.coordtag.CoordTag
import com.github.lazoyoung.craftgames.coordtag.SpawnCapture
import com.github.lazoyoung.craftgames.exception.DependencyNotFound
import com.github.lazoyoung.craftgames.game.Game
import com.github.lazoyoung.craftgames.player.GameEditor
import com.github.lazoyoung.craftgames.player.GamePlayer
import com.github.lazoyoung.craftgames.player.PlayerData
import com.github.lazoyoung.craftgames.player.Spectator
import io.lumine.xikage.mythicmobs.MythicMobs
import net.md_5.bungee.api.ChatColor
import net.md_5.bungee.api.chat.ComponentBuilder
import net.md_5.bungee.api.chat.TextComponent
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.EntityType
import org.bukkit.event.player.PlayerTeleportEvent
import java.util.function.Consumer

class SpawnModuleImpl(val game: Game) : SpawnModule {

    private var personal: CoordTag? = null
    private var editor: CoordTag? = null
    private var spectator: CoordTag? = null
    private val notFound = ComponentBuilder("Unable to locate spawnpoint!")
            .color(ChatColor.RED).create().first() as TextComponent

    fun spawnPlayer(playerData: PlayerData, asyncCallback: Consumer<Boolean>? = null) {
        spawnPlayer(game.map.world!!, playerData, asyncCallback)
    }

    fun spawnPlayer(world: World, playerData: PlayerData, asyncCallback: Consumer<Boolean>? = null) {
        val scheduler = Bukkit.getScheduler()
        val plugin = Main.instance
        val player = playerData.player
        val tag = when (playerData) {
            is GameEditor -> editor
            is GamePlayer -> personal
            is Spectator -> spectator
            else -> null
        }
        val location: Location

        if (tag == null) {
            location = world.spawnLocation
            player.sendMessage(notFound)
        } else {
            val c = tag.getLocalCaptures().random() as SpawnCapture
            location = Location(world, c.x, c.y, c.z, c.yaw, c.pitch)
        }

        if (asyncCallback == null) {
            player.teleport(location)
        } else {
            scheduler.runTaskAsynchronously(plugin, Runnable {
                player.teleportAsync(location, PlayerTeleportEvent.TeleportCause.PLUGIN)
                        .thenAccept(asyncCallback::accept).exceptionally { it.printStackTrace(); return@exceptionally null }
            })
        }
    }

    override fun setSpawn(type: Int, spawnTag: String) {
        val tag = Module.getSpawnTag(game, spawnTag)

        when (type) {
            PERSONAL -> personal = tag
            EDITOR -> editor = tag
            SPECTATOR -> spectator = tag
        }
    }

    override fun spawnMob(type: String, spawnTag: String) {
        val c = Module.getSpawnTag(game, spawnTag).getLocalCaptures().random() as SpawnCapture
        val entity = EntityType.valueOf(type.toUpperCase().replace(' ', '_'))
        val loc = Location(game.map.world!!, c.x, c.y, c.z, c.yaw, c.pitch)

        game.map.world!!.spawnEntity(loc, entity)
    }

    override fun spawnMythicMob(name: String, level: Int, spawnTag: String) {
        if (Bukkit.getPluginManager().getPlugin("MythicMobs") == null)
            throw DependencyNotFound("MythicMobs plugin is required to use this function.")

        val c = Module.getSpawnTag(game, spawnTag).getLocalCaptures().random() as SpawnCapture
        val loc = Location(game.map.world!!, c.x, c.y, c.z, c.yaw, c.pitch)
        val mmAPI = MythicMobs.inst().apiHelper
        mmAPI.spawnMythicMob(name, loc, level)
    }
}