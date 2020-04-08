package com.github.lazoyoung.craftgames.internal.listener

import com.destroystokyo.paper.event.player.PlayerStartSpectatingEntityEvent
import com.github.lazoyoung.craftgames.Main
import com.github.lazoyoung.craftgames.event.GameAreaEnterEvent
import com.github.lazoyoung.craftgames.event.GameAreaExitEvent
import com.github.lazoyoung.craftgames.event.GamePlayerDeathEvent
import com.github.lazoyoung.craftgames.event.GamePlayerKillEvent
import com.github.lazoyoung.craftgames.game.Game
import com.github.lazoyoung.craftgames.game.module.Module
import com.github.lazoyoung.craftgames.game.player.GameEditor
import com.github.lazoyoung.craftgames.game.player.GamePlayer
import com.github.lazoyoung.craftgames.game.player.PlayerData
import com.github.lazoyoung.craftgames.game.player.Spectator
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.world.WorldInitEvent

class ServerListener : Listener {

    @EventHandler(priority = EventPriority.HIGH)
    fun onPlayerMove(event: PlayerMoveEvent) {
        val player = event.player
        val pdata = PlayerData.get(player) ?: return

        if (pdata is GamePlayer && player.gameMode != GameMode.SPECTATOR) {
            val worldModule = Module.getWorldModule(pdata.getGame())
            val from = worldModule.getAreaNameAt(event.from)
            val to = worldModule.getAreaNameAt(event.to)

            if (from == to)
                return

            if (from != null) {
                Bukkit.getPluginManager().callEvent(
                        GameAreaExitEvent(pdata.getGame(), from, player)
                )
            }

            if (to != null) {
                Bukkit.getPluginManager().callEvent(
                        GameAreaEnterEvent(pdata.getGame(), to, player)
                )
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

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player

        try {
            PlayerData.getOffline(player)?.restore(true)
        } catch (e: Exception) {
            e.printStackTrace()
            Main.logger.severe("Failed to read data for ${player.name}")
        }
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        PlayerData.get(event.player)?.leaveGame()
    }

    @EventHandler
    fun onEntityDeath(event: EntityDeathEvent) {
        val entity = event.entity
        val killer = entity.killer
                ?.let { PlayerData.get(it) } as? GamePlayer
                ?: return
        val player = killer.getPlayer()
        val game = killer.getGame()
        val service = Module.getPlayerModule(game)

        if (game.phase == Game.Phase.PLAYING) {
            Bukkit.getPluginManager().callEvent(
                    GamePlayerKillEvent(killer, entity, game)
            )
            service.killTriggers[player.uniqueId]?.accept(entity)
        }
    }

    @EventHandler
    fun onPlayerDeath(event: PlayerDeathEvent) {
        val player = event.entity
        val gamePlayer = PlayerData.get(player) as? GamePlayer ?: return
        val game = gamePlayer.getGame()

        if (game.phase != Game.Phase.PLAYING)
            return

        val playerModule = Module.getPlayerModule(game)
        val relayEvent = GamePlayerDeathEvent(gamePlayer, game)

        Bukkit.getPluginManager().callEvent(relayEvent)

        if (!relayEvent.isCancelled) {
            player.health = player.getAttribute(Attribute.GENERIC_MAX_HEALTH)?.value ?: 20.0
            event.isCancelled = true

            // React to the trigger result
            Bukkit.getScheduler().runTask(Main.instance, Runnable {
                if (!gamePlayer.isOnline())
                    return@Runnable

                if (relayEvent.canRespawn()) {
                    playerModule.respawn(gamePlayer)
                } else {
                    playerModule.eliminate(player)
                }
            })
        }
    }

    @EventHandler
    fun onSpectateEntity(event: PlayerStartSpectatingEntityEvent) {
        val playerData = PlayerData.get(event.player)
                ?: return
        val entity = event.newSpectatorTarget

        if (playerData is Spectator
                && entity is Player
                && PlayerData.get(entity) is GamePlayer)
            return

        event.isCancelled = true
    }

}