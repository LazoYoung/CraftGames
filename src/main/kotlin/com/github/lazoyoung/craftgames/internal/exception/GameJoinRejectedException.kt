package com.github.lazoyoung.craftgames.internal.exception

import com.github.lazoyoung.craftgames.game.player.PlayerData
import net.md_5.bungee.api.ChatColor
import net.md_5.bungee.api.chat.ComponentBuilder
import org.bukkit.entity.Player

class GameJoinRejectedException(
        private val player: Player,
        val rejectCause: Cause,
        playerData: PlayerData? = null
) : Exception() {

    enum class Cause(val message: String) {
        PLAYING_THIS("You're already playing this game."),
        PLAYING_OTHER("You're already in game."),
        FULL("Game is full."),
        GAME_IN_PROGRESS("Game is already started."),
        TERMINATING("Game is closing."),
        NO_PERMISSION("You don't have permission."),
        ERROR("Error occurred."),
        UNKNOWN("Failed to join this game.")
    }

    init {
        if (playerData != null) {
            PlayerData.registry.remove(player.uniqueId)
        }
    }

    override val message = "${player.name}: ${rejectCause.message}"
    private val text = ComponentBuilder(rejectCause.message)
            .color(ChatColor.RED)
            .create()

    fun informPlayer() {
        player.sendMessage(*text)
    }

}