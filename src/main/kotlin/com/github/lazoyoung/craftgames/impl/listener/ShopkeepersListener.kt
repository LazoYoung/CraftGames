package com.github.lazoyoung.craftgames.impl.listener

import com.github.lazoyoung.craftgames.impl.Main
import com.github.lazoyoung.craftgames.impl.game.Game
import com.nisovin.shopkeepers.api.events.ShopkeeperAddedEvent
import com.nisovin.shopkeepers.api.events.ShopkeeperAddedEvent.Cause
import com.nisovin.shopkeepers.api.events.ShopkeeperRemoveEvent
import com.nisovin.shopkeepers.api.shopkeeper.admin.regular.RegularAdminShopkeeper
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener

class ShopkeepersListener : Listener {

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onCreation(event: ShopkeeperAddedEvent) {
        val shopkeeper = event.shopkeeper
        val world = shopkeeper.location?.world
        val game = world?.let {
            Game.getByWorld(it)
        }

        if (game != null) {
            when (val cause = event.cause) {
                Cause.CREATED -> {
                    if (shopkeeper !is RegularAdminShopkeeper) {
                        shopkeeper.delete()
                        Main.logger.warning("Unauthorized shopkeeper creation is halted in ${world.name}.")
                        return
                    }

                    // TODO Block this event if overlapping object is found.
                    game.resource.loadShopkeeper(shopkeeper)
                    game.getMobService().shopkeeperList.add(shopkeeper.uniqueId)
                }
                Cause.LOADED -> {
                    shopkeeper.delete()
                    Main.logger.warning("Unauthorized shopkeeper creation is halted in ${world.name}.")
                }
                else -> error("Invalid cause: $cause")
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onRemove(event: ShopkeeperRemoveEvent) {
        val shopkeeper = event.shopkeeper
        val game = shopkeeper.location?.world?.let {
            Game.getByWorld(it)
        }

        if (game != null) {
            val mobService = game.getMobService()
            val uid = shopkeeper.uniqueId

            if (shopkeeper is RegularAdminShopkeeper &&
                    mobService.shopkeeperList.contains(uid)) {
                game.resource.discardShopkeeper(shopkeeper)
            }
        }
    }

}