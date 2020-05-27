package com.github.lazoyoung.craftgames.api.event

import com.github.lazoyoung.craftgames.impl.game.Game
import org.bukkit.block.Block
import org.bukkit.entity.Entity
import org.bukkit.event.Cancellable
import org.bukkit.event.HandlerList
import org.bukkit.event.entity.EntityDamageByBlockEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent

class GameEntityDamageEvent(
        game: Game,
        entity: Entity,
        private val damageEvent: EntityDamageEvent
) : GameEntityEvent(game, entity), Cancellable {

    private var cancel = false

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

    fun getCause(): EntityDamageEvent.DamageCause {
        return damageEvent.cause
    }

    fun getDamagerBlock(): Block? {
        return if (damageEvent is EntityDamageByBlockEvent) {
            damageEvent.damager
        } else {
            null
        }
    }

    fun getDamagerEntity(): Entity? {
        return if (damageEvent is EntityDamageByEntityEvent) {
            damageEvent.damager
        } else {
            null
        }
    }

    fun getDamage(): Double {
        return damageEvent.finalDamage
    }

    fun setDamage(amount: Double) {
        damageEvent.damage = amount
    }

    override fun setCancelled(cancel: Boolean) {
        this.cancel = cancel
    }

    override fun isCancelled(): Boolean {
        return cancel
    }

}