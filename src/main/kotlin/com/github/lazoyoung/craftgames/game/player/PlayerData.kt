package com.github.lazoyoung.craftgames.game.player

import com.github.lazoyoung.craftgames.game.Game
import org.bukkit.entity.Player
import java.util.*
import kotlin.collections.HashMap

open class PlayerData(
        val player: Player,
        val game: Game
) {
    private var online = true

    companion object {
        internal val registry = HashMap<UUID, PlayerData>()

        fun get(player: Player?): PlayerData? {
            return registry[player?.uniqueId]
        }

        fun get(uid: UUID): PlayerData? {
            return registry[uid]
        }
    }

    fun leaveGame() {
        game.leave(this)
    }

    fun isOnline(): Boolean {
        return online
    }

    internal fun unregister() {
        online = false
        registry.remove(player.uniqueId)
    }
}