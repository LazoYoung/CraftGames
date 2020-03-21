package com.github.lazoyoung.craftgames.module

import com.github.lazoyoung.craftgames.coordtag.CoordTag
import com.github.lazoyoung.craftgames.coordtag.SpawnCapture
import com.github.lazoyoung.craftgames.coordtag.TagMode
import com.github.lazoyoung.craftgames.exception.DependencyNotFound
import com.github.lazoyoung.craftgames.exception.FaultyConfiguration
import com.github.lazoyoung.craftgames.game.Game
import com.github.lazoyoung.craftgames.player.GameEditor
import com.github.lazoyoung.craftgames.player.GamePlayer
import com.github.lazoyoung.craftgames.player.PlayerData
import com.github.lazoyoung.craftgames.player.Spectator
import io.lumine.xikage.mythicmobs.MythicMobs
import net.md_5.bungee.api.ChatColor
import net.md_5.bungee.api.chat.TextComponent
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.World

class SpawnModuleImpl(val game: Game) : SpawnModule {

    private var personal: CoordTag? = null
    private var editor: CoordTag? = null
    private var spectator: CoordTag? = null
    private val notFound: TextComponent = TextComponent("Unable to locate spawnpoint!")

    init {
        notFound.color = ChatColor.YELLOW
    }

    fun spawnPlayer(world: World, playerData: PlayerData) {
        val player = playerData.player
        val tag = when (playerData) {
            is GameEditor -> editor
            is GamePlayer -> personal
            is Spectator -> spectator
            else -> null
        }

        if (tag == null) {
            world.spawnLocation.let { player.teleport(it) }
            player.sendMessage(notFound)
        } else {
            tag.getLocalCaptures().random().let {
                player.teleport(Location(world, it.x, it.y, it.z))
            }
        }
    }

    override fun setSpawn(type: Int, spawnTag: String) {
        val tag = CoordTag.get(game, spawnTag) ?: throw IllegalArgumentException("Unable to identify $spawnTag tag.")

        if (tag.mode != TagMode.SPAWN)
            throw IllegalArgumentException("Parameter does not accept block tag.")

        if (tag.getLocalCaptures().isEmpty())
            throw FaultyConfiguration("Tag $spawnTag doesn't have any capture in ${game.map.mapID}")

        when (type) {
            PERSONAL -> personal = tag
            EDITOR -> editor = tag
            SPECTATOR -> spectator = tag
        }
    }

    override fun spawnMythicMob(name: String, level: Int, spawnTag: String) {
        val tag = CoordTag.get(game, spawnTag) ?: throw IllegalArgumentException("Unable to identify $spawnTag tag.")

        if (Bukkit.getPluginManager().getPlugin("MythicMobs") == null)
            throw DependencyNotFound("MythicMobs plugin is required to use this function.")

        if (tag.mode != TagMode.SPAWN)
            throw IllegalArgumentException("Parameter does not accept block tag.")

        if (tag.getLocalCaptures().isEmpty())
            throw FaultyConfiguration("Tag $spawnTag doesn't have any capture in ${game.map.mapID}")

        val c = tag.getLocalCaptures().random() as SpawnCapture
        val loc = Location(game.map.world!!, c.x, c.y, c.z, c.yaw, c.pitch)
        val mmAPI = MythicMobs.inst().apiHelper
        mmAPI.spawnMythicMob(MythicMobs.inst().mobManager.getMythicMob(name), loc, level)
    }
}