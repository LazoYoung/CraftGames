package com.github.lazoyoung.craftgames.game.player

import com.github.lazoyoung.craftgames.internal.exception.ConcurrentPlayerState
import com.github.lazoyoung.craftgames.game.Game
import org.bukkit.entity.Player

class Spectator private constructor(
        player: Player,
        game: Game
): PlayerData(player, game) {

    companion object {
        internal fun register(player: Player, game: Game): Spectator {
            val pid = player.uniqueId
            val legacy = get(player)
            val new = Spectator(player, game)

            if (legacy?.game != game)
                throw ConcurrentPlayerState(null)

            registry[pid] = new
            return new
        }
    }

}