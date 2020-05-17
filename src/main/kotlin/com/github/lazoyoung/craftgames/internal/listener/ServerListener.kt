package com.github.lazoyoung.craftgames.internal.listener

import com.destroystokyo.paper.event.player.PlayerStartSpectatingEntityEvent
import com.github.lazoyoung.craftgames.Main
import com.github.lazoyoung.craftgames.event.*
import com.github.lazoyoung.craftgames.game.Game
import com.github.lazoyoung.craftgames.game.GamePhase
import com.github.lazoyoung.craftgames.game.player.*
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Mob
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.entity.EntitySpawnEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.inventory.InventoryAction
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.player.*
import org.bukkit.event.world.WorldInitEvent
import org.bukkit.inventory.PlayerInventory

class ServerListener : Listener {

    @EventHandler(priority = EventPriority.LOWEST)
    fun onWorldLoad(event: WorldInitEvent) {
        for (game in Game.find()) {
            val name = event.world.name

            if (name == game.map.worldName) {
                event.world.keepSpawnInMemory = false

                Bukkit.getScheduler().runTaskLater(Main.instance, Runnable {
                    Bukkit.getWorld(name)?.keepSpawnInMemory = true
                }, 40L)
                break
            }
        }
    }

    @EventHandler
    fun onEntitySpawn(event: EntitySpawnEvent) {
        val mob = event.entity as? Mob ?: return
        val game = Game.getByWorld(mob.world) ?: return

        if (game.phase == GamePhase.PLAYING) {
            if (mob.world.entityCount >= game.getMobService().mobCap) {
                event.isCancelled = true
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onPlayerInteract(event: PlayerInteractEvent) {
        val pdata = PlayerData.get(event.player) ?: return
        val game = pdata.getGame()
        val action = event.action
        val block = event.clickedBlock

        if (pdata is GameEditor && block != null) {

            if (action != Action.LEFT_CLICK_BLOCK && action != Action.RIGHT_CLICK_BLOCK)
                return

            if (event.isBlockInHand || event.player.isSneaking)
                return

            if (pdata.callBlockPrompt(block) || pdata.callAreaPrompt(block, event.action)) {
                event.isCancelled = true
            }
        }

        if (game.phase == GamePhase.PLAYING) {
            if (pdata is GamePlayer) {
                val relayEvent = GamePlayerInteractEvent(pdata, game, event)
                Bukkit.getPluginManager().callEvent(relayEvent)
            }

            if (event.player.gameMode == GameMode.SPECTATOR) {
                event.setUseItemInHand(Event.Result.DENY)
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onInventoryClick(event: InventoryClickEvent) {

        if (event.clickedInventory !is PlayerInventory) {
            return
        }

        val pdata = PlayerData.get(event.whoClicked.uniqueId) ?: return
        val game = pdata.getGame()

        if (pdata is GamePlayer && game.phase == GamePhase.PLAYING) {
            if (event.action != InventoryAction.NOTHING) {
                event.isCancelled = game.getItemService().lockInventory
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onInventoryDrag(event: InventoryDragEvent) {
        val pdata = PlayerData.get(event.whoClicked.uniqueId) ?: return
        val game = pdata.getGame()

        if (pdata is GamePlayer && game.phase == GamePhase.PLAYING) {
            event.isCancelled = game.getItemService().lockInventory
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onItemDrop(event: PlayerDropItemEvent) {
        val pdata = PlayerData.get(event.player) ?: return
        val game = pdata.getGame()

        if (pdata is GamePlayer && game.phase == GamePhase.PLAYING) {
            event.isCancelled = !game.getItemService().allowItemDrop
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlayerMove(event: PlayerMoveEvent) {
        val player = event.player
        val pdata = PlayerData.get(player) ?: return

        if (pdata is GamePlayer && player.gameMode != GameMode.SPECTATOR) {
            val worldModule = pdata.getGame().getWorldService()
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

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player

        try {
            PlayerData.getOffline(player)?.restore(RestoreMode.LEAVE)
        } catch (e: Exception) {
            e.printStackTrace()
            Main.logger.severe("Failed to read data for ${player.name}")
        }
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        PlayerData.get(event.player)?.leaveGame()
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPlayerDeath(event: PlayerDeathEvent) {
        val player = event.entity
        val playerData = PlayerData.get(player) ?: return
        val game = playerData.getGame()

        if (game.editMode) {
            game.getWorldService().teleportSpawn(playerData, null)
        }
        else if (playerData is GamePlayer && game.phase == GamePhase.PLAYING) {
            val playerService = game.getPlayerService()
            val relayEvent = GamePlayerDeathEvent(playerData, game, event)

            // Call GamePlayerDeathEvent
            Bukkit.getPluginManager().callEvent(relayEvent)
            event.isCancelled = relayEvent.isCancelled

            if (!event.isCancelled) {
                val keep = relayEvent.keepInventory
                val drop = relayEvent.dropItems
                val deathMessage = event.deathMessage
                event.keepInventory = keep
                playerData.keepInventory = keep
                player.health = player.getAttribute(Attribute.GENERIC_MAX_HEALTH)?.value ?: 20.0

                if (deathMessage != null) {
                    event.deathMessage = null
                    game.getGameService().broadcast(deathMessage)
                }

                if (keep || !drop) {
                    event.drops.clear()
                }

                Bukkit.getScheduler().runTask(Main.instance, Runnable {
                    if (!playerData.isOnline())
                        return@Runnable

                    val location = player.location

                    if (location.y < -16.0) {
                        location.y = location.world.getHighestBlockYAt(location).toDouble()
                        player.teleport(location)
                    }

                    if (relayEvent.canRespawn) {
                        playerService.respawn(playerData)
                    } else {
                        playerService.eliminate(player)
                    }
                })
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onSpectateEntity(event: PlayerStartSpectatingEntityEvent) {
        val entity = event.newSpectatorTarget
        val playerData = PlayerData.get(event.player)
                ?: return

        if (playerData is Spectator
                && entity is Player
                && PlayerData.get(entity) is GamePlayer)
            return

        event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onEntityDeath(event: EntityDeathEvent) {
        val entity = event.entity
        val killer = entity.killer
                ?.let { PlayerData.get(it) } as? GamePlayer
                ?: return
        val game = killer.getGame()

        if (game.phase == GamePhase.PLAYING) {
            Bukkit.getPluginManager().callEvent(
                    GamePlayerKillEvent(killer, entity, game)
            )
        }
    }

}