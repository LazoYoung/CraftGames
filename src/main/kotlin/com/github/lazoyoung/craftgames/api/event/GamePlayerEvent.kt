package com.github.lazoyoung.craftgames.api.event

import com.github.lazoyoung.craftgames.impl.game.Game
import com.github.lazoyoung.craftgames.impl.game.player.PlayerData
import org.bukkit.entity.Player

abstract class GamePlayerEvent(
        private val playerData: PlayerData,
        game: Game
) : GameEvent(game) {

    fun getPlayer(): Player {
        return getPlayerData().getPlayer()
    }

    fun getPlayerData(): PlayerData {
        return playerData
    }

}