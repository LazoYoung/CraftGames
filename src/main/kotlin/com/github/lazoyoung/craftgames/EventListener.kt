package com.github.lazoyoung.craftgames

import com.github.lazoyoung.craftgames.game.GameFactory
import com.github.lazoyoung.craftgames.player.GameEditor
import com.github.lazoyoung.craftgames.player.PlayerData
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.world.WorldInitEvent

class EventListener : Listener {

    @EventHandler
    fun onWorldLoad(event: WorldInitEvent) {
        val name = event.world.name

        for (game in GameFactory.find()) {
            if (name == game.map.worldName) {
                event.world.keepSpawnInMemory = false
                break
            }
        }
    }

    @EventHandler
    fun onBlockClick(event: PlayerInteractEvent) {
        event.clickedBlock?.let {
            val playerData = PlayerData.get(event.player) ?: return

            if (playerData is GameEditor) {
                if (playerData.callBlockPrompt(it))
                    event.isCancelled = true
            }
        }
    }

}