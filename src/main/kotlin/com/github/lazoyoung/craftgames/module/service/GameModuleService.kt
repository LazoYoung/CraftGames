package com.github.lazoyoung.craftgames.module.service

import com.github.lazoyoung.craftgames.Main
import com.github.lazoyoung.craftgames.coordtag.SpawnCapture
import com.github.lazoyoung.craftgames.event.GameStartEvent
import com.github.lazoyoung.craftgames.exception.UndefinedCoordTag
import com.github.lazoyoung.craftgames.game.Game
import com.github.lazoyoung.craftgames.module.Module
import com.github.lazoyoung.craftgames.module.api.GameModule
import com.github.lazoyoung.craftgames.player.GameEditor
import com.github.lazoyoung.craftgames.player.GamePlayer
import com.github.lazoyoung.craftgames.player.PlayerData
import com.github.lazoyoung.craftgames.player.Spectator
import com.github.lazoyoung.craftgames.util.MessageTask
import com.github.lazoyoung.craftgames.util.TimeUnit
import com.github.lazoyoung.craftgames.util.Timer
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

class GameModuleService internal constructor(val game: Game) : GameModule {

    internal var defaultGameMode = GameMode.ADVENTURE
    internal var canJoinAfterStart = false
    internal var minPlayer = 1
    internal var maxPlayer = 10
    private var timer: Long = Timer(TimeUnit.MINUTE, 3).toSecond()
    private var timerLength = timer

    /* Service handling bossbar and timer */
    private var serviceTask: BukkitRunnable? = null

    // Bossbar facility
    private val bossBarKey = NamespacedKey(Main.instance, "timer-${game.id}")
    internal val bossBar = Bukkit.createBossBar(bossBarKey, "TIME - 00:00:00", BarColor.WHITE, BarStyle.SOLID)

    private val notFound = ComponentBuilder("Unable to locate spawnpoint!")
            .color(ChatColor.RED).create().first() as TextComponent

    override fun getTimer(): Timer {
        return Timer(TimeUnit.TICK, timer)
    }

    override fun setTimer(timer: Timer) {
        this.timerLength = timer.toSecond()
        this.timer = timer.toSecond()
    }

    override fun setPlayerCapacity(min: Int, max: Int) {
        this.minPlayer = min
        this.maxPlayer = max
    }

    override fun setCanJoinAfterStart(boolean: Boolean) {
        this.canJoinAfterStart = boolean
    }

    override fun setDefaultGameMode(mode: GameMode) {
        this.defaultGameMode = mode
    }

    override fun broadcast(message: String) {
        game.getPlayers().forEach {
            it.sendMessage(*TextComponent.fromLegacyText(message.replace('&', '\u00A7')))
        }
    }

    /**
     * Teleport [player][playerData] to the relevant spawnpoint
     * matching with its [type][PlayerData].
     */
    fun teleportSpawn(playerData: PlayerData, asyncCallback: Consumer<Boolean>? = null) {
        val world = game.map.world!!
        val scheduler = Bukkit.getScheduler()
        val plugin = Main.instance
        val player = playerData.player
        val playerModule = Module.getPlayerModule(game)
        val tag = when (playerData) {
            is GameEditor -> playerModule.editor
            is Spectator -> playerModule.spectator
            is GamePlayer -> {
                Module.getTeamModule(game).getSpawn(player) ?: playerModule.personal
            }
            else -> null
        }
        val location: Location

        if (tag == null) {
            location = world.spawnLocation
            player.sendMessage(notFound)
        } else {
            val mapID = game.map.id
            val captures = tag.getCaptures(mapID)

            if (captures.isEmpty())
                throw UndefinedCoordTag("${tag.name} has no capture in map: $mapID")

            val c = captures.random() as SpawnCapture
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

    internal fun finishGame() {
        fun doCeremony() {
            /* TODO Ceremony & Reward */
        }

        // ...then suspend it
        game.close()
    }

    internal fun start() {
        val playerModule = Module.getPlayerModule(game)

        // Fire event
        Bukkit.getPluginManager().callEvent(GameStartEvent(game))

        // Setup players
        game.players.mapNotNull { PlayerData.get(it) }.forEach { p ->
            teleportSpawn(p)
            playerModule.restore(p.player)
            bossBar.addPlayer(p.player)
        }

        serviceTask = object : BukkitRunnable() {
            override fun run() {
                val format = Timer(TimeUnit.SECOND, timer).format(false)
                val title = StringBuilder("\u00A76GAME TIME \u00A77- ")
                val progress = timer.toDouble() / timerLength

                when {
                    progress < 0.1 -> {
                        bossBar.color = BarColor.RED
                        title.append("\u00A7c").append(format)
                    }
                    progress < 0.2 -> {
                        bossBar.color = BarColor.YELLOW
                        title.append("\u00A7e").append(format)
                    }
                    else -> {
                        bossBar.color = BarColor.WHITE
                        title.append("\u00A7f").append(format)
                    }
                }

                bossBar.progress = progress
                bossBar.setTitle(title.toString())

                if (timer-- < 1) {
                    finishGame()
                    this.cancel()
                    return
                }
            }
        }
        serviceTask!!.runTaskTimer(Main.instance, 0L, 20L)
    }

    internal fun terminate() {
        bossBar.removeAll()
        Bukkit.removeBossBar(bossBarKey)
        serviceTask?.cancel()
    }

    internal fun respawn(gamePlayer: GamePlayer) {
        val playerModule = Module.getPlayerModule(game)
        val player = gamePlayer.player
        val actionBar = MessageTask(
                player = player,
                type = ChatMessageType.ACTION_BAR,
                textCases = listOf(
                        "&eYou will respawn in a moment.",
                        "&e&lYou will respawn in a moment."
                ),
                interval = Timer(TimeUnit.TICK, 30)
        )

        // Temporarily spectate
        player.gameMode = GameMode.SPECTATOR
        actionBar.start()

        Bukkit.getScheduler().runTaskLater(Main.instance, Runnable {
            if (!Module.getPlayerModule(game).isOnline(player))
                return@Runnable

            // Rollback to spawnpoint with default GameMode
            teleportSpawn(gamePlayer)
            playerModule.restore(gamePlayer.player)
            actionBar.clear()
            player.sendActionBar('&', "&a&lRESPAWN")
        }, playerModule.respawnTimer)
    }

}