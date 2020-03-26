package com.github.lazoyoung.craftgames.module.service

import com.github.lazoyoung.craftgames.Main
import com.github.lazoyoung.craftgames.coordtag.CoordTag
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
    private var timer: Long = Timer(Timer.Unit.MINUTE, 3).toSecond()
    private var timerLength = timer
    private var respawnTimer: Long = Timer(Timer.Unit.SECOND, 20).toTick()
    private var personal: CoordTag? = null
    private var editor: CoordTag? = null
    private var spectator: CoordTag? = null

    /* Service handling bossbar and timer */
    private var serviceTask: BukkitRunnable? = null

    // Bossbar facility
    private val bossBarKey = NamespacedKey(Main.instance, "timer-${game.id}")
    internal val bossBar = Bukkit.createBossBar(bossBarKey, "TIME - 00:00:00", BarColor.WHITE, BarStyle.SOLID)

    private val notFound = ComponentBuilder("Unable to locate spawnpoint!")
            .color(ChatColor.RED).create().first() as TextComponent

    override fun getGameTimer(): Timer {
        return Timer(Timer.Unit.TICK, timer)
    }

    override fun setGameTimer(timer: Timer) {
        if (game.phase == Game.Phase.LOBBY) {
            this.timerLength = timer.toSecond()
        }

        this.timer = timer.toSecond()
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
            if (tag.getLocalCaptures().isEmpty())
                throw UndefinedCoordTag("${tag.name} has no capture in map: ${game.map.mapID}")

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

        // Fire event
        Bukkit.getPluginManager().callEvent(GameStartEvent(game))

        // Setup players
        game.players.mapNotNull { PlayerData.get(it) }.forEach { p ->
            teleport(p)
            playerModule.restore(p.player)
            bossBar.addPlayer(p.player)
        }

        serviceTask = object : BukkitRunnable() {
            override fun run() {
                val format = Timer(Timer.Unit.SECOND, timer).format(false)
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
                    finish()
                    this.cancel()
                    return
                }
            }
        }
        serviceTask!!.runTaskTimer(Main.instance, 0L, 20L)
    }

    internal fun endService() {
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
            if (!Module.getPlayerModule(game).isOnline(player))
                return@Runnable

            // Rollback to spawnpoint with default GameMode
            teleport(gamePlayer)
            game.module.playerModule.restore(gamePlayer.player)
            actionBar.clear()
            player.sendActionBar('&', "&aRESPAWN")
        }, respawnTimer)
    }

}