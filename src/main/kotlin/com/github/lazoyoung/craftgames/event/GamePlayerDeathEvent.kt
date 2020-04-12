package com.github.lazoyoung.craftgames.event

import com.github.lazoyoung.craftgames.api.TimeUnit
import com.github.lazoyoung.craftgames.api.Timer
import com.github.lazoyoung.craftgames.api.module.GameModule
import com.github.lazoyoung.craftgames.api.module.PlayerModule
import com.github.lazoyoung.craftgames.game.Game
import com.github.lazoyoung.craftgames.game.module.Module
import com.github.lazoyoung.craftgames.game.module.PlayerModuleService
import com.github.lazoyoung.craftgames.game.player.PlayerData
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
     * @return The tag name of the player's spawnpoint.
     */
    fun getSpawnpoint(): String? {
        return getPlayerModule().getSpawnpoint(getPlayerData())?.name
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
     * Set the next spawnpoint of this player.
     *
     * Defaults to [PlayerModule.setSpawnpoint]
     *
     * @param tagName Name of the coordinate tag designating the spawnpoint.
     */
    fun setSpawnpoint(tagName: String) {
        return getPlayerModule().setSpawnpoint(getPlayer(), tagName)
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