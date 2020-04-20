package com.github.lazoyoung.craftgames.internal.listener

import com.github.lazoyoung.craftgames.game.Game
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.world.WorldInitEvent

class WorldListener : Listener {

    @EventHandler(priority = EventPriority.LOWEST)
    fun onWorldLoad(event: WorldInitEvent) {
        for (game in Game.find()) {
            if (event.world.name == game.map.worldName) {
                event.world.keepSpawnInMemory = false
                break
            }
        }
    }

}