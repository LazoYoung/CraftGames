package com.github.lazoyoung.craftgames.internal.listener

import com.github.lazoyoung.craftgames.Main
import com.github.lazoyoung.craftgames.game.Game
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
            when (event.cause!!) {
                Cause.CREATED -> {
                    if (shopkeeper !is RegularAdminShopkeeper) {
                        shopkeeper.delete()
                        Main.logger.warning("Unauthorized shopkeeper creation is halted in ${world.name}.")
                        return
                    }

                    // FIXME UniqueID cannot be used to identify save file and restore it back.
                    /*
                     * Save resolution for each ShopObjectType.
                     * 1) Citizen: Citizen's name is the unique identifier.
                     * 2) Mob: Location is the unique identifier as it cannot move.
                     * 3) Block: Location is the unique identifier.
                     */

                    game.resource.loadShopkeeper(shopkeeper)
                    game.getMobService().shopkeeperList.add(shopkeeper.uniqueId)
                }
                Cause.LOADED -> {
                    shopkeeper.delete()
                    Main.logger.warning("Unauthorized shopkeeper creation is halted in ${world.name}.")
                }
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