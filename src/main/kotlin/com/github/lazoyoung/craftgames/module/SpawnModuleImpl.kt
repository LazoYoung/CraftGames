package com.github.lazoyoung.craftgames.module

import com.github.lazoyoung.craftgames.Main
import com.github.lazoyoung.craftgames.coordtag.CoordTag
import com.github.lazoyoung.craftgames.coordtag.TagMode
import com.github.lazoyoung.craftgames.exception.FaultyConfiguration
import com.github.lazoyoung.craftgames.game.Game
import com.github.lazoyoung.craftgames.player.GameEditor
import com.github.lazoyoung.craftgames.player.GamePlayer
import com.github.lazoyoung.craftgames.player.PlayerData
import com.github.lazoyoung.craftgames.player.Spectator
import net.md_5.bungee.api.ChatColor
import net.md_5.bungee.api.chat.TextComponent
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.Mob

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
            tag.getCaptures(null).random().let {
                player.teleport(Location(world, it.x, it.y, it.z))
            }
        }
    }

    override fun setPersonalSpawn(spawnTag: String) {
        // TODO Exception message is less accurate.
        val tag = CoordTag.get(game, spawnTag)

        if (tag == null) {
            Main.logger.warning("Unable to identify $spawnTag tag.")
            return
        }

        if (tag.mode != TagMode.SPAWN)
            throw FaultyConfiguration("SpawnModule#setPersonalSpawn() parameter does not accept block tag.")

        personal = tag
    }

    override fun setEditorSpawn(spawnTag: String) {
        val tag = CoordTag.get(game, spawnTag)

        if (tag == null) {
            Main.logger.warning("Unable to identify $spawnTag tag.")
            return
        }

        if (tag.mode != TagMode.SPAWN)
            throw FaultyConfiguration("SpawnModule#setEditorSpawn() parameter does not accept block tag.")

        editor = tag
    }

    override fun setSpectatorSpawn(spawnTag: String) {
        val tag = CoordTag.get(game, spawnTag)

        if (tag == null) {
            // TODO Inform to players or editor?
            Main.logger.warning("Unable to identify $spawnTag tag.")
            return
        }

        if (tag.mode != TagMode.SPAWN)
            throw FaultyConfiguration("SpawnModule#setSpectatorSpawn() parameter does not accept block tag.")

        spectator = tag
    }

    override fun spawnEntity(type: Mob, spawnTag: String) {
        TODO("Not yet implemented")
    }
}