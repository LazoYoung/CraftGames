package com.github.lazoyoung.craftgames.event

import com.github.lazoyoung.craftgames.game.Game
import com.github.lazoyoung.craftgames.game.player.PlayerData
import org.bukkit.entity.LivingEntity
import org.bukkit.event.HandlerList

class GamePlayerKillEvent(
        playerData: PlayerData,
        private val victim: LivingEntity,
        game: Game
) : GamePlayerEvent(playerData, game) {

    companion object {
        private val handlerList = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList {
            return this.handlerList
        }
    }

    override fun getHandlers(): HandlerList {
        return getHandlerList()
    }

    fun getVictim(): LivingEntity {
        return victim
    }
}