package com.github.lazoyoung.craftgames

import com.github.lazoyoung.craftgames.game.Game
import com.github.lazoyoung.craftgames.module.Module
import com.github.lazoyoung.craftgames.module.service.GameModuleImpl
import com.github.lazoyoung.craftgames.module.service.PlayerModuleImpl
import com.github.lazoyoung.craftgames.player.GameEditor
import com.github.lazoyoung.craftgames.player.GamePlayer
import com.github.lazoyoung.craftgames.player.PlayerData
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.world.WorldInitEvent

class EventListener : Listener {

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onWorldLoad(event: WorldInitEvent) {
        for (game in Game.find()) {
            if (event.world.name == game.map.worldName) {
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

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val player = event.player

        PlayerData.get(player)?.game?.leave(player)
    }

    @EventHandler
    fun onMobDeath(event: EntityDeathEvent) {
        val entity = event.entity
        val gamePlayer = (entity as? Player)?.killer
                ?.let { PlayerData.get(it.uniqueId) } as? GamePlayer
                ?: return
        val player = gamePlayer.player
        val triggers = gamePlayer.game.module.playerModule.killTriggers

        // Fire the trigger
        triggers[player.uniqueId]?.accept(player, entity)
    }

    @EventHandler
    fun onPlayerDeath(event: PlayerDeathEvent) {
        val player = event.entity
        val gamePlayer = PlayerData.get(player) as? GamePlayer ?: return
        val playerModule = getPlayerModuleImpl(event.entity) ?: return
        val gameModule = Module.getGameModule(playerModule.game) as GameModuleImpl
        val triggerResult = playerModule.deathTriggers[player.uniqueId]?.test(player)

        event.isCancelled = true

        // React to the trigger result
        Bukkit.getScheduler().runTask(Main.instance, Runnable {
            if (triggerResult == true) {
                gameModule.respawn(gamePlayer)
            } else {
                playerModule.eliminate(player)
            }
        })
    }

    private fun getPlayerModuleImpl(player: Player): PlayerModuleImpl? {
        val game = Game.find().firstOrNull {
            it.phase == Game.Phase.PLAYING && it.map.world == player.world
        } ?: return null

        return Module.getPlayerModule(game) as PlayerModuleImpl
    }

}