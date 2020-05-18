package com.github.lazoyoung.craftgames.impl.game.player

import com.github.lazoyoung.craftgames.impl.game.Game
import com.github.lazoyoung.craftgames.impl.exception.ConcurrentPlayerState
import org.bukkit.entity.Player

class GamePlayer private constructor(
        player: Player,
        private val game: Game
): PlayerData(player, game, game.getGameService().defaultGameMode) {

    companion object {
        /**
         * @throws RuntimeException is raised if plugin fails to write player's data.
         */
        internal fun register(player: Player, game: Game): GamePlayer {
            if (get(player) != null)
                throw ConcurrentPlayerState(null)

            val instance = GamePlayer(player, game)
            registry[player.uniqueId] = instance

            instance.captureState()
            return instance
        }
    }

    /**
     * Returns the [Game] this player belongs to.
     */
    override fun getGame(): Game {
        return game
    }

    fun toSpectator() {
        try {
            Spectator.register(getPlayer(), game, this)
        } catch(e: RuntimeException) {
            e.printStackTrace()
            game.leave(this)
        }
    }
}