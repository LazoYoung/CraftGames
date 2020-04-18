package com.github.lazoyoung.craftgames.game.player

import com.github.lazoyoung.craftgames.api.ActionbarTask
import com.github.lazoyoung.craftgames.game.Game
import com.github.lazoyoung.craftgames.internal.exception.ConcurrentPlayerState
import org.bukkit.entity.Player

class Spectator private constructor(
        player: Player,
        private val game: Game
): PlayerData(player, game) {

    companion object {
        /**
         * @param migrate [PlayerData] storing the previous data
         * which should be migrated into this instance (This is optional).
         * @throws RuntimeException is raised if plugin fails to write player's data.
         */
        internal fun register(player: Player, game: Game, migrate: PlayerData? = null): Spectator {
            val pid = player.uniqueId
            val legacy = get(player)
            val instance = Spectator(player, game)

            if (legacy?.isOnline() == true && legacy.getGame() != game) {
                throw ConcurrentPlayerState(null)
            }

            registry[pid] = instance
            instance.captureState(migrate)
            return instance
        }
    }

    /**
     * Returns the [Game] this spectator belongs to.
     */
    override fun getGame(): Game {
        return game
    }

    internal fun updateEditors() {
        val coopList = game.getPlayers().map { get(it) }
                .filterIsInstance(GameEditor::class.java).shuffled()

        coopList.forEach { editor ->
            val memberList = coopList.filterNot { it.getPlayer() == editor.getPlayer() }

            var text = arrayOf(
                    "&b&lEDIT MODE &r&b(&e${editor.mapID} &bin &e${game.name}&b)",
                    "&aType &b/game save &r&ato save changes and exit."
            )

            if (memberList.isNotEmpty()) {
                text = text.plus(memberList.joinToString(
                        prefix = "&aCooperator: &r",
                        limit = 3,
                        transform = { it.getPlayer().displayName }
                ))
            }

            editor.mainActionbar?.clear()
            editor.mainActionbar = ActionbarTask(
                    player = editor.getPlayer(),
                    repeat = true,
                    text = *text
            ).start()
        }
    }

}