package com.github.lazoyoung.craftgames.event.listener

import com.github.lazoyoung.craftgames.Main
import com.github.lazoyoung.craftgames.game.Game
import com.github.lazoyoung.craftgames.module.Module
import com.github.lazoyoung.craftgames.player.GameEditor
import com.github.lazoyoung.craftgames.player.GamePlayer
import com.github.lazoyoung.craftgames.player.PlayerData
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.world.WorldInitEvent

class ServerListener : Listener {

    @EventHandler(priority = EventPriority.HIGH)
    fun onPlayerMove(event: PlayerMoveEvent) {
        val player = event.player
        val pdata = PlayerData.get(player) ?: return

        if (pdata is GamePlayer && player.gameMode != GameMode.SPECTATOR) {
            val worldModule = Module.getWorldModule(pdata.game)

            worldModule.getAreaNameAt(event.to)?.let {
                worldModule.triggers[it]?.accept(player)
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
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
            val pdata = PlayerData.get(event.player) ?: return
            val action = event.action

            if (pdata is GameEditor) {
                if (action != Action.LEFT_CLICK_BLOCK && action != Action.RIGHT_CLICK_BLOCK)
                    return

                if (event.isBlockInHand || event.player.isSneaking)
                    return

                if (pdata.callBlockPrompt(it) || pdata.callAreaPrompt(it, event.action)) {
                    event.isCancelled = true
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val player = event.player

        PlayerData.get(player)?.leaveGame()
    }

    @EventHandler
    fun onPlayerKill(event: EntityDeathEvent) {
        val entity = event.entity
        val gamePlayer = (entity as? Player)?.killer
                ?.let { PlayerData.get(it.uniqueId) } as? GamePlayer
                ?: return
        val player = gamePlayer.player
        val service = Module.getPlayerModule(gamePlayer.game)

        if (gamePlayer.game.phase == Game.Phase.PLAYING) {
            service.killTriggers[player.uniqueId]?.accept(player, entity)
        }
    }

    @EventHandler
    fun onPlayerDeath(event: PlayerDeathEvent) {
        val player = event.entity
        val gamePlayer = PlayerData.get(player) as? GamePlayer ?: return
        val game = gamePlayer.game

        if (game.phase != Game.Phase.PLAYING)
            return

        // Trigger DeathEvent
        val playerModule = Module.getPlayerModule(game)
        val gameModule = Module.getGameModule(game)
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

}