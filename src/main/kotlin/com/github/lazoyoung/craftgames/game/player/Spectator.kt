package com.github.lazoyoung.craftgames.game.player

import com.github.lazoyoung.craftgames.game.Game
import com.github.lazoyoung.craftgames.internal.exception.ConcurrentPlayerState
import org.bukkit.entity.Player

class Spectator private constructor(
        player: Player,
        private val game: Game
): PlayerData(player, game) {

    companion object {
        /**
         * @throws RuntimeException is raised if plugin fails to write player's data.
         */
        internal fun register(player: Player, game: Game): Spectator {
            val pid = player.uniqueId
            val legacy = get(player)
            val new = Spectator(player, game)

            if (legacy?.isOnline() == true && legacy.getGame() != game)
                throw ConcurrentPlayerState(null)

            registry[pid] = new
            return new
        }
    }

    /**
     * Returns the [Game] this spectator belongs to.
     */
    override fun getGame(): Game {
        return game
    }

}