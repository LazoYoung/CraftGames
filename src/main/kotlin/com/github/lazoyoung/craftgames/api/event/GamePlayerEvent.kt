package com.github.lazoyoung.craftgames.api.event

import com.github.lazoyoung.craftgames.impl.game.Game
import com.github.lazoyoung.craftgames.impl.game.player.PlayerData

abstract class GamePlayerEvent(
        val playerData: PlayerData,
        game: Game
) : GameEvent(game) {
    val player = playerData.getPlayer()
}