package com.github.lazoyoung.craftgames.player

import com.github.lazoyoung.craftgames.exception.ConcurrentPlayerState
import com.github.lazoyoung.craftgames.exception.MapNotFound
import com.github.lazoyoung.craftgames.game.Game
import com.github.lazoyoung.craftgames.game.GameFactory
import com.github.lazoyoung.craftgames.game.GameMap
import org.bukkit.entity.Player
import java.util.*
import java.util.function.Consumer

class GameEditor(
        val playerID: UUID,
        val map: GameMap
) {
    companion object {
        private val registry = HashMap<UUID, GameEditor>()

        fun get(playerID: UUID): GameEditor? {
            if (PlayerState.get(playerID) != PlayerState.NONE)
                throw ConcurrentPlayerState(null)

            return registry[playerID]
        }

        /**
         * Make the player in editor mode.
         *
         * @param consumer Consumes the new instance. (null if game fails to start)
         * @throws ConcurrentPlayerState Thrown if the duplicate instance were found.
         * @throws MapNotFound Thrown if map is not found.
         */
        fun startEdit(player: Player, gameID: String, mapID: String, consumer: Consumer<GameEditor?>) {
            val pid = player.uniqueId
            val succeed: Boolean

            if (registry.containsKey(pid))
                throw ConcurrentPlayerState("Concurrent GameEditor instances are not allowed.")

            val game: Game = GameFactory.openNew(gameID)

            if (!game.map.getMapList().contains(mapID))
                throw MapNotFound("Map not found: $mapID")

            // Start game
            succeed = game.start(mapID, Consumer {
                val instance = GameEditor(pid, game.map)
                registry[pid] = instance
                PlayerState.set(pid, PlayerState.EDITING)
                player.teleport(it!!.spawnLocation) // TODO Module: editor spawnpoint
                consumer.accept(instance)
            })

            if (!succeed)
                consumer.accept(null)
        }
    }

    fun getGame() : Game? {
        return GameFactory.findByID(map.game.id)
    }

    fun saveAndLeave() {
        TODO()
    }
}