package com.github.lazoyoung.craftgames.module

import com.github.lazoyoung.craftgames.Main
import com.github.lazoyoung.craftgames.coordtag.CoordTag
import com.github.lazoyoung.craftgames.coordtag.TagMode
import com.github.lazoyoung.craftgames.exception.FaultyConfiguration
import com.github.lazoyoung.craftgames.game.Game
import net.md_5.bungee.api.ChatColor
import net.md_5.bungee.api.chat.TextComponent
import org.bukkit.entity.Mob
import org.bukkit.entity.Player

class SpawnModuleImpl(val game: Game) : SpawnModule {

    var personal: CoordTag? = null
    var editor: CoordTag? = null
    var spectator: CoordTag? = null
    private val notFound: TextComponent = TextComponent("Unable to locate spawnpoint!")

    init {
        notFound.color = ChatColor.YELLOW
    }

    fun spawnPersonal(player: Player) {
        if (personal == null) {
            game.map.world?.spawnLocation?.let { player.teleport(it) }
            player.sendMessage(notFound)
        } else {
            personal?.getLocalCaptures()?.random()?.teleport(player)
        }
    }

    fun spawnEditor(player: Player) {
        if (editor == null) {
            game.map.world?.spawnLocation?.let { player.teleport(it) }
            player.sendMessage(notFound)
        } else {
            editor?.getLocalCaptures()?.random()?.teleport(player)
        }
    }

    fun spawnSpectator(player: Player) {
        if (spectator == null) {
            game.map.world?.spawnLocation?.let { player.teleport(it) }
            player.sendMessage(notFound)
        } else {
            spectator?.getLocalCaptures()?.random()?.teleport(player)
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