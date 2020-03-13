package com.github.lazoyoung.craftgames

import com.github.lazoyoung.craftgames.game.GameFactory
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.world.WorldInitEvent

class EventListener : Listener {

    @EventHandler
    fun onWorldLoad(event: WorldInitEvent) {
        val name = event.world.name

        for (game in GameFactory.get()) {
            if (name == game.map.worldName) {
                event.world.keepSpawnInMemory = false
                break
            }
        }
    }

}