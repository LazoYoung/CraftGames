package com.github.lazoyoung.craftgames.game.module

import com.destroystokyo.paper.Title
import com.github.lazoyoung.craftgames.Main
import com.github.lazoyoung.craftgames.api.ActionbarTask
import com.github.lazoyoung.craftgames.api.PlayerType
import com.github.lazoyoung.craftgames.api.TimeUnit
import com.github.lazoyoung.craftgames.api.Timer
import com.github.lazoyoung.craftgames.api.module.PlayerModule
import com.github.lazoyoung.craftgames.command.RESET_FORMAT
import com.github.lazoyoung.craftgames.coordtag.tag.CoordTag
import com.github.lazoyoung.craftgames.coordtag.tag.TagMode
import com.github.lazoyoung.craftgames.game.Game
import com.github.lazoyoung.craftgames.game.player.GamePlayer
import com.github.lazoyoung.craftgames.game.player.PlayerData
import com.github.lazoyoung.craftgames.game.player.Spectator
import net.md_5.bungee.api.ChatColor
import net.md_5.bungee.api.chat.ComponentBuilder
import net.md_5.bungee.api.chat.TextComponent
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.attribute.Attribute
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitRunnable
import java.util.*
import java.util.function.Consumer
import java.util.function.Supplier
import kotlin.collections.HashMap

class PlayerModuleService internal constructor(private val game: Game) : PlayerModule {

    internal var personal: CoordTag? = null
    internal var editor: CoordTag? = null
    internal var spectator: CoordTag? = null
    internal val killTriggers = HashMap<UUID, Consumer<LivingEntity>>()
    internal val deathTriggers = HashMap<UUID, Supplier<Boolean>>()
    private var respawnTimer = HashMap<UUID, Timer>()
    private val script = game.resource.script

    override fun getLivingPlayers(): List<Player> {
        return game.getPlayers().filter { PlayerData.get(it) is GamePlayer }
    }

    override fun getDeadPlayers(): List<Player> {
        return game.getPlayers().filter { PlayerData.get(it) is Spectator }
    }

    override fun isOnline(player: Player): Boolean {
        return game.getPlayers().contains(player)
    }

    override fun eliminate(player: Player) {
        val gamePlayer = PlayerData.get(player.uniqueId) as? GamePlayer
                ?: throw IllegalArgumentException("Player ${player.name} is not online.")
        val title = ComponentBuilder("YOU DIED").color(ChatColor.RED).create()
        val subTitle = ComponentBuilder("Type ").color(ChatColor.GRAY)
                .append("/leave").color(ChatColor.WHITE).bold(true).append(" to exit.", RESET_FORMAT).color(ChatColor.GRAY).create()

        player.gameMode = GameMode.SPECTATOR
        player.sendTitle(Title(title, subTitle, 20, 80, 20))
        gamePlayer.toSpectator()
    }

    override fun setKillTrigger(killer: Player, trigger: Consumer<LivingEntity>?) {
        val name = killer.name
        val uid = killer.uniqueId

        if (trigger == null) {
            if (killTriggers.containsKey(uid)) {
                killTriggers.remove(uid)
                script.getLogger()?.println("A Kill trigger is un-bound from: $name")
            }
        } else {
            killTriggers[uid] = Consumer { livingEntity ->
                try {
                    trigger.accept(livingEntity)
                } catch (e: Exception) {
                    script.writeStackTrace(e)
                    script.getLogger()?.println("Error occurred in Kill trigger: $name")
                }
            }
            script.getLogger()?.println("A kill trigger is bound to $name.")
        }
    }

    override fun setDeathTrigger(player: Player, respawn: Boolean, trigger: Runnable?) {
        val name = player.name
        val uid = player.uniqueId

        if (trigger == null) {
            if (deathTriggers.containsKey(uid)) {
                deathTriggers.remove(uid)
                script.getLogger()?.println("A Death trigger is un-bound from: $name")
            }
        } else {
            deathTriggers[uid] = Supplier {
                try {
                    trigger.run()
                } catch (e: Exception) {
                    script.writeStackTrace(e)
                    script.getLogger()?.println("Error occurred in Death trigger: ${player.name}")
                }
                respawn
            }
            script.getLogger()?.println("A death trigger is bound to ${player.name}.")
            script.getLogger()?.println("Respawn for ${player.name}: $respawn")
        }
    }

    override fun setRespawnTimer(player: Player, timer: Timer) {
        this.respawnTimer[player.uniqueId] = timer
    }

    override fun setSpawn(type: PlayerType, spawnTag: String) {
        val tag = Module.getRelevantTag(game, spawnTag, TagMode.SPAWN)

        when (type) {
            PlayerType.PLAYER -> personal = tag
            PlayerType.EDITOR -> editor = tag
            PlayerType.SPECTATOR -> spectator = tag
        }
    }

    override fun setSpawn(type: String, spawnTag: String) {
        setSpawn(PlayerType.valueOf(type), spawnTag)
    }

    override fun sendMessage(player: Player, message: String) {
        player.sendMessage(*TextComponent.fromLegacyText(message.replace('&', '\u00A7')))
    }

    fun restore(player: Player, leave: Boolean = false) {
        val maxHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH)
        val flySpeed = player.getAttribute(Attribute.GENERIC_FLYING_SPEED)
        val walkSpeed = player.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED)

        // Reset player attribute
        player.gameMode = Module.getGameModule(game).defaultGameMode
        maxHealth?.defaultValue?.let { maxHealth.baseValue = it }
        flySpeed?.defaultValue?.let { flySpeed.baseValue = it }
        walkSpeed?.defaultValue?.let { walkSpeed.baseValue = it }
        player.foodLevel = 20
        player.saturation = 5.0f
        player.exhaustion = 0.0f

        // Clear potion effects
        player.activePotionEffects.forEach{ e -> player.removePotionEffect(e.type) }

        if (leave) {
            player.inventory.clear()
            // TODO Restore inventory
        } else {
            // Apply kit
            Module.getItemModule(game).applyKit(player)
        }
    }

    internal fun respawn(gamePlayer: GamePlayer) {
        val player = gamePlayer.player
        val plugin = Main.instance
        val scheduler = Bukkit.getScheduler()
        val timer = respawnTimer[player.uniqueId]?.clone()
                ?: Module.getGameModule(game).respawnTimer.clone()
        val gracePeriod = Main.getConfig()?.getLong("spawn.invincible", 60L)
                ?: 60L

        player.gameMode = GameMode.SPECTATOR

        object : BukkitRunnable() {
            override fun run() {
                if (!Module.getPlayerModule(game).isOnline(player)) {
                    this.cancel()
                    return
                }

                val frame = Timer(TimeUnit.SECOND, 1)
                val format = timer.format(true)

                ActionbarTask(player, period = frame, text = *arrayOf("&eRespawning in $format."))
                        .start()

                if (timer.subtract(frame).toSecond() < 0L) {
                    // Rollback to spawnpoint with default GameMode
                    restore(gamePlayer.player)
                    Module.getWorldModule(game).teleportSpawn(gamePlayer, null)
                    ActionbarTask(player, period = frame, text = *arrayOf("&9&l> &a&lRESPAWN &9&l<"))
                            .start()

                    // Damage protection
                    player.isInvulnerable = true
                    scheduler.runTaskLater(plugin, Runnable {
                        player.isInvulnerable = false
                    }, Timer(TimeUnit.TICK, gracePeriod).toTick())

                    this.cancel()
                }
            }
        }.runTaskTimer(plugin, 0L, 20L)
    }

}