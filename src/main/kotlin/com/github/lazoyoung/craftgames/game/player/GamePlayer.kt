package com.github.lazoyoung.craftgames.game.player

import com.github.lazoyoung.craftgames.internal.exception.ConcurrentPlayerState
import com.github.lazoyoung.craftgames.game.Game
import org.bukkit.entity.Player

class GamePlayer private constructor(
        player: Player,
        game: Game
): PlayerData(player, game) {

    companion object {
        internal fun register(player: Player, game: Game): GamePlayer {
            if (get(player) != null)
                throw ConcurrentPlayerState(null)

            val instance = GamePlayer(player, game)
            registry[player.uniqueId] = instance
            return instance
        }
    }

    fun toSpectator() {
        Spectator.register(player, game)
    }
}