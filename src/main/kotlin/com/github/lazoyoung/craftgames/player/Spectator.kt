package com.github.lazoyoung.craftgames.player

import com.github.lazoyoung.craftgames.exception.ConcurrentPlayerState
import com.github.lazoyoung.craftgames.game.Game
import org.bukkit.entity.Player

class Spectator private constructor(
        player: Player,
        game: Game
): PlayerData(player, game) {

    companion object {
        fun register(player: Player, game: Game): Spectator {
            val pid = player.uniqueId

            if (get(player) != null)
                throw ConcurrentPlayerState(null)

            val instance = Spectator(player, game)
            registry[pid] = instance
            return instance
        }
    }

}