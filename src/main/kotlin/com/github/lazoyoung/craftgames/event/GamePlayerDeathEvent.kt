package com.github.lazoyoung.craftgames.event

import com.github.lazoyoung.craftgames.api.TimeUnit
import com.github.lazoyoung.craftgames.api.Timer
import com.github.lazoyoung.craftgames.api.module.GameModule
import com.github.lazoyoung.craftgames.game.Game
import com.github.lazoyoung.craftgames.game.module.Module
import com.github.lazoyoung.craftgames.game.module.PlayerModuleService
import com.github.lazoyoung.craftgames.game.player.PlayerData
import org.bukkit.Location
import org.bukkit.event.Cancellable
import org.bukkit.event.HandlerList

class GamePlayerDeathEvent(
        playerData: PlayerData,
        game: Game
) : GamePlayerEvent(playerData, game), Cancellable {
    private var cancel = false
    private var canRespawn = Module.getGameModule(game).canRespawn

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

    override fun setCancelled(cancel: Boolean) {
        this.cancel = cancel
    }

    override fun isCancelled(): Boolean {
        return cancel
    }

    /**
     * Get whether this player can respawn or not.
     */
    fun canRespawn(): Boolean {
        return canRespawn
    }

    /**
     * @return The the next spawnpoint of this player.
     */
    fun getSpawnpoint(): Location {
        return getPlayerModule().getSpawnpoint(getPlayerData(), null)
    }

    /**
     * Get amount of time it takes to respawn this player.
     */
    fun getRespawnTimer(): Timer {
        val emptyTimer = Timer(TimeUnit.TICK, 0)

        return getPlayerModule().respawnTimer[getPlayer().uniqueId] ?: emptyTimer
    }

    /**
     * Set whether this player can respawn or not.
     *
     * Defaults to [GameModule.setCanRespawn]
     */
    fun setCanRespawn(canRespawn: Boolean) {
        this.canRespawn = canRespawn
    }

    /**
     * Decide if player should respawn back to the location where he/she died last time.
     *
     * @param rewind If [rewind] is true, previous death location becomes the new spawnpoint.
     * Otherwise, spawnpoint would be reset to default one.
     */
    fun setRewind(rewind: Boolean) {
        val player = getPlayer()

        if (rewind) {
            getPlayerModule().overrideSpawnpoint(player, player.location.clone())
        } else {
            getPlayerModule().resetSpawnpoint(player)
        }
    }

    /**
     * Set amount of time it takes to respawn this player.
     *
     * Defaults to [GameModule.setRespawnTimer]
     */
    fun setRespawnTimer(timer: Timer) {
        getPlayerModule().respawnTimer[getPlayer().uniqueId] = timer
    }

    private fun getPlayerModule(): PlayerModuleService {
        return Module.getPlayerModule(getGame())
    }

}