package com.github.lazoyoung.craftgames.module.service

import com.github.lazoyoung.craftgames.Main
import com.github.lazoyoung.craftgames.MessageTask
import com.github.lazoyoung.craftgames.coordtag.CoordTag
import com.github.lazoyoung.craftgames.coordtag.SpawnCapture
import com.github.lazoyoung.craftgames.game.Game
import com.github.lazoyoung.craftgames.module.Module
import com.github.lazoyoung.craftgames.module.Timer
import com.github.lazoyoung.craftgames.module.api.GameModule
import com.github.lazoyoung.craftgames.player.GameEditor
import com.github.lazoyoung.craftgames.player.GamePlayer
import com.github.lazoyoung.craftgames.player.PlayerData
import com.github.lazoyoung.craftgames.player.Spectator
import net.md_5.bungee.api.ChatColor
import net.md_5.bungee.api.ChatMessageType
import net.md_5.bungee.api.chat.ComponentBuilder
import net.md_5.bungee.api.chat.TextComponent
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.boss.BarColor
import org.bukkit.boss.BarStyle
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.scheduler.BukkitRunnable
import java.util.function.Consumer

class GameModuleImpl(val game: Game) : GameModule {

    /** Default GameMode **/
    internal var defGameMode = GameMode.ADVENTURE
    private var timer: Long = Timer(Timer.Unit.MINUTE, 3).toSecond()
    private var respawnTimer: Long = Timer(Timer.Unit.SECOND, 20).toTick()
    private var personal: CoordTag? = null
    private var editor: CoordTag? = null
    private var spectator: CoordTag? = null

    /* BossBar, Game timer, TODO Script scheduler service */
    private var serviceTask: BukkitRunnable? = null

    private val bossBarKey = NamespacedKey(Main.instance, "timer-${game.id}")
    private val bossBar = Bukkit.createBossBar(bossBarKey, "TIME - 00:00:00", BarColor.WHITE, BarStyle.SOLID)
    private val notFound = ComponentBuilder("Unable to locate spawnpoint!")
            .color(ChatColor.RED).create().first() as TextComponent

    override fun getGameTimer(): Timer {
        return Timer(Timer.Unit.TICK, timer)
    }

    override fun setGameTimer(timer: Timer) {
        this.timer = timer.toSecond()

        if (game.phase == Game.Phase.PLAYING) {
            // TODO Player Module: inform players about this change.
        }
    }

    override fun setRespawnTimer(timer: Timer) {
        this.respawnTimer = timer.toTick()
    }

    override fun setPlayerSpawn(type: Int, spawnTag: String) {
        val tag = Module.getSpawnTag(game, spawnTag)

        when (type) {
            GameModule.PERSONAL -> personal = tag
            GameModule.EDITOR -> editor = tag
            GameModule.SPECTATOR -> spectator = tag
        }
    }

    override fun setDefaultGameMode(mode: GameMode) {
        this.defGameMode = mode
    }

    /**
     * Teleport [player][playerData] to the relevant spawnpoint
     * matching with its [type][PlayerData].
     */
    fun teleport(playerData: PlayerData, asyncCallback: Consumer<Boolean>? = null) {
        val world = game.map.world!!
        val scheduler = Bukkit.getScheduler()
        val plugin = Main.instance
        val player = playerData.player
        val tag = when (playerData) {
            is GameEditor -> editor
            is GamePlayer -> personal
            is Spectator -> spectator
            else -> null
        }
        val location: Location

        if (tag == null) {
            location = world.spawnLocation
            player.sendMessage(notFound)
        } else {
            val c = tag.getLocalCaptures().random() as SpawnCapture
            location = Location(world, c.x, c.y, c.z, c.yaw, c.pitch)
        }

        if (asyncCallback == null) {
            player.teleport(location)
        } else {
            scheduler.runTaskAsynchronously(plugin, Runnable {
                player.teleportAsync(location, PlayerTeleportEvent.TeleportCause.PLUGIN)
                        .thenAccept(asyncCallback::accept)
                        .exceptionally { it.printStackTrace(); return@exceptionally null }
            })
        }
    }

    internal fun finish() {
        fun doCeremony() {
            /* TODO Ceremony & Reward */
        }

        // ...then suspend it
        game.close()
    }

    internal fun startService() {
        val playerModule = game.module.playerModule

        // Setup players
        game.players.mapNotNull { PlayerData.get(it) }.forEach { p ->
            teleport(p)
            playerModule.restore(p.player)
            bossBar.addPlayer(p.player)
        }

        serviceTask = object : BukkitRunnable() {
            override fun run() {
                val timeFrame = Timer(Timer.Unit.SECOND, timer)
                bossBar.setTitle(
                        StringBuilder("TIME - ")
                                .append(timeFrame.format(false))
                                .toString()
                )

                if (timer-- < 1) {
                    finish()
                    this.cancel()
                    return
                }
            }
        }
        serviceTask!!.runTaskTimer(Main.instance, 0L, 20L)
    }

    internal fun endService() {
        // TODO Suspend all schedulers from script.
        bossBar.removeAll()
        Bukkit.removeBossBar(bossBarKey)
        serviceTask?.cancel()
    }

    internal fun respawn(gamePlayer: GamePlayer) {
        val player = gamePlayer.player
        val actionBar = MessageTask(
                player = player,
                type = ChatMessageType.ACTION_BAR,
                textCases = listOf(
                        "You will respawn in a moment.",
                        "&eYou will respawn in a moment."
                ),
                interval = Timer(Timer.Unit.TICK, 30)
        )

        // Temporarily spectate
        player.gameMode = GameMode.SPECTATOR
        actionBar.start()

        Bukkit.getScheduler().runTaskLater(Main.instance, Runnable {
            // Rollback to spawnpoint with default GameMode
            teleport(gamePlayer)
            game.module.playerModule.restore(gamePlayer.player)
            actionBar.clear()
            player.sendActionBar('&', "&aRESPAWN")
        }, respawnTimer)
    }
}