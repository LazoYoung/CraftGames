package com.github.lazoyoung.craftgames.api.event

import com.github.lazoyoung.craftgames.api.TimeUnit
import com.github.lazoyoung.craftgames.api.Timer
import com.github.lazoyoung.craftgames.api.module.GameModule
import com.github.lazoyoung.craftgames.impl.game.Game
import com.github.lazoyoung.craftgames.impl.game.player.PlayerData
import com.github.lazoyoung.craftgames.impl.game.module.PlayerModuleService
import org.bukkit.Location
import org.bukkit.block.Block
import org.bukkit.entity.Entity
import org.bukkit.event.Cancellable
import org.bukkit.event.HandlerList
import org.bukkit.event.entity.EntityDamageByBlockEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.PlayerDeathEvent

class GamePlayerDeathEvent(
        playerData: PlayerData,
        game: Game,
        private val bukkitEvent: PlayerDeathEvent
) : GamePlayerEvent(playerData, game), Cancellable {

    private var cancel = false

    /**
     * Determines if this player can respawn or not.
     *
     * Defaults to [GameModule.setCanRespawn]
     */
    var canRespawn = game.getGameService().canRespawn

    /**
     * Decide to keep the items in inventory upon death.
     */
    var keepInventory = game.getGameService().keepInventory
        private set

    /**
     * Decide to drop items on ground upon death.
     */
    var dropItems = game.getGameService().dropItems
        private set

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
     * Get the cause of this event.
     *
     * @return [EntityDamageEvent.DamageCause]
     */
    fun getCause(): EntityDamageEvent.DamageCause? {
        val damageEvent = player.lastDamageCause

        return if (damageEvent?.isCancelled == false) {
            damageEvent.cause
        } else {
            null
        }
    }

    /**
     * Get the block which killed this player.
     *
     * @return [Block] if it exists, null otherwise.
     */
    fun getKillerBlock(): Block? {
        val damageEvent = player.lastDamageCause

        return if (damageEvent?.isCancelled == false
                && damageEvent is EntityDamageByBlockEvent) {
            damageEvent.damager
        } else {
            null
        }
    }

    /**
     * Get the entity who killed this player.
     *
     * @return [Entity] if it exists, null otherwise.
     */
    fun getKillerEntity(): Entity? {
        val damageEvent = player.lastDamageCause

        return if (damageEvent?.isCancelled == false
                && damageEvent is EntityDamageByEntityEvent) {
            damageEvent.damager
        } else {
            null
        }
    }

    /**
     * @return The the next spawnpoint of this player.
     */
    fun getSpawnpoint(): Location {
        return getPlayerModule().getSpawnpoint(playerData, null).join()
    }

    /**
     * Get amount of time it takes to respawn this player.
     */
    fun getRespawnTimer(): Timer {
        val emptyTimer = Timer(TimeUnit.TICK, 0)

        return getPlayerModule().respawnTimer[player.uniqueId] ?: emptyTimer
    }

    /**
     * Decide if player should respawn back to the location where he/she died last time.
     *
     * @param rewind If [rewind] is true, previous death location becomes the new spawnpoint.
     * Otherwise, spawnpoint would be reset to default one.
     */
    fun setRewind(rewind: Boolean) {
        if (rewind) {
            getPlayerModule().overrideSpawnpoint(player, player.location.clone())
        } else {
            getPlayerModule().resetSpawnpoint(player)
        }
    }

    /**
     * Decide if player's items should be kept upon death.
     *
     * Defaults to [GameModule.setKeepInventory]
     *
     * @param keep Whether or not to keep items in inventory.
     * @param drop Whether or not to drop items on ground.
     * @throws IllegalArgumentException is thrown if [keep] and [drop] are both true.
     */
    fun setKeepInventory(keep: Boolean, drop: Boolean) {
        this.keepInventory = keep
        this.dropItems = drop
    }

    /**
     * Decide if player's experience level should be kept upon death.
     *
     * @param keep Whether or not to keep experience level.
     * @param drop Whether or not to drop experience on ground.
     * @throws IllegalArgumentException is thrown if [keep] and [drop] are both true.
     */
    fun setKeepExp(keep: Boolean, drop: Boolean) {
        bukkitEvent.keepLevel = keep

        if (keep || !drop) {
            bukkitEvent.droppedExp = 0
        }
    }

    /**
     * Set death message to be announced upon death.
     *
     * @param message The message (Pass null to remove it).
     */
    fun setDeathMessage(message: String?) {
        bukkitEvent.deathMessage = message
    }

    /**
     * Set amount of time it takes to respawn this player.
     *
     * Defaults to [GameModule.setRespawnTimer]
     */
    fun setRespawnTimer(timer: Timer) {
        getPlayerModule().respawnTimer[player.uniqueId] = timer
    }

    private fun getPlayerModule(): PlayerModuleService {
        return getGame().getPlayerService()
    }

}