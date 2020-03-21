package com.github.lazoyoung.craftgames.player

import com.github.lazoyoung.craftgames.game.Game
import org.bukkit.entity.Player
import java.util.*
import kotlin.collections.HashMap

open class PlayerData(
        val player: Player,
        val game: Game
) {
    companion object {
        internal val registry = HashMap<UUID, PlayerData>()

        fun get(player: Player?): PlayerData? {
            return registry[player?.uniqueId]
        }

        fun get(uid: UUID): PlayerData? {
            return registry[uid]
        }
    }

    fun unregister() {
        registry.remove(player.uniqueId)
    }
}