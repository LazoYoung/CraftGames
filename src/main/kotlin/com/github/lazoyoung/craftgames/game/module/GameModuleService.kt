package com.github.lazoyoung.craftgames.game.module

import com.github.lazoyoung.craftgames.Main
import com.github.lazoyoung.craftgames.api.GameResult
import com.github.lazoyoung.craftgames.api.TimeUnit
import com.github.lazoyoung.craftgames.api.Timer
import com.github.lazoyoung.craftgames.api.module.GameModule
import com.github.lazoyoung.craftgames.coordtag.capture.SpawnCapture
import com.github.lazoyoung.craftgames.event.GameFinishEvent
import com.github.lazoyoung.craftgames.event.GameStartEvent
import com.github.lazoyoung.craftgames.game.Game
import com.github.lazoyoung.craftgames.game.player.GameEditor
import com.github.lazoyoung.craftgames.game.player.GamePlayer
import com.github.lazoyoung.craftgames.game.player.PlayerData
import com.github.lazoyoung.craftgames.game.player.Spectator
import com.github.lazoyoung.craftgames.internal.exception.MapNotFound
import com.github.lazoyoung.craftgames.internal.exception.UndefinedCoordTag
import net.md_5.bungee.api.ChatColor
import net.md_5.bungee.api.chat.ComponentBuilder
import net.md_5.bungee.api.chat.TextComponent
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.boss.BarColor
import org.bukkit.boss.BarStyle
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scoreboard.Team
import java.util.function.Consumer

class GameModuleService internal constructor(private val game: Game) : GameModule {

    internal var defaultGameMode = GameMode.ADVENTURE
    internal var canJoinAfterStart = false
    internal var minPlayer = 1
    internal var maxPlayer = 10
    private var timer = Timer(TimeUnit.MINUTE, 3)
    private var fullTime = timer

    /* Service handling bossbar and timer */
    private var serviceTask: BukkitRunnable? = null

    // Bossbar facility
    private val bossBarKey = NamespacedKey(Main.instance, "timer-${game.id}")
    internal val bossBar = Bukkit.createBossBar(bossBarKey, "TIME - 00:00:00", BarColor.WHITE, BarStyle.SOLID)

    private val notFound = ComponentBuilder("Unable to locate spawnpoint!")
            .color(ChatColor.RED).create().first() as TextComponent

    override fun getTimer(): Timer {
        return timer
    }

    override fun setTimer(timer: Timer) {
        this.fullTime = timer
        this.timer = timer
    }

    override fun setPlayerCapacity(min: Int, max: Int) {
        this.minPlayer = min
        this.maxPlayer = max
    }

    override fun setCanJoinAfterStart(boolean: Boolean) {
        this.canJoinAfterStart = boolean
    }

    override fun setGameMode(mode: GameMode) {
        this.defaultGameMode = mode
    }

    override fun setPVP(pvp: Boolean) {
        val world = game.map.world ?: throw MapNotFound()

        world.pvp = pvp
    }

    override fun broadcast(message: String) {
        game.getPlayers().forEach {
            it.sendMessage(*TextComponent.fromLegacyText(message.replace('&', '\u00A7')))
        }
    }

    override fun finishGame(winner: Team, timer: Timer) {
        val winners = Module.getTeamModule(game).getPlayers(winner)

        // Fire event
        Bukkit.getPluginManager().callEvent(GameFinishEvent(game, GameResult.TEAM_WIN, winner, winners))

        // Ceremony and close
        broadcast("&6Congratulations, &r${winner.displayName} &6won the game!")
        Bukkit.getScheduler().runTaskLater(Main.instance, Runnable { game.close() }, timer.toTick())
    }

    override fun finishGame(winner: Player, timer: Timer) {
        // Fire event
        Bukkit.getPluginManager().callEvent(GameFinishEvent(game, GameResult.SOLO_WIN, null, listOf(winner)))

        // Ceremony and close
        broadcast("&6Congratulations, &r${winner.displayName} &6won the game!")
        Bukkit.getScheduler().runTaskLater(Main.instance, Runnable { game.close() }, timer.toTick())
    }

    override fun drawGame(timer: Timer) {
        // Fire event
        Bukkit.getPluginManager().callEvent(GameFinishEvent(game, GameResult.DRAW, null, null))

        // Ceremony and close
        broadcast("&6Time out! The game ended in a draw...")
        Bukkit.getScheduler().runTaskLater(Main.instance, Runnable { game.close() }, timer.toTick())
    }

    /**
     * Teleport [player][playerData] to the relevant spawnpoint
     * matching with its [type][PlayerData].
     *
     * @throws UndefinedCoordTag If spawnpoint is not captured in this map, this is thrown.
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
        val log = game.resource.script.getLogger()
        val location: Location

        if (tag == null) {
            location = world.spawnLocation
            location.y = world.getHighestBlockYAt(location).toDouble()
            player.sendMessage(notFound)
            log?.println("Spawn tag \'$tag\' is not defined.")
        } else {
            val mapID = game.map.id
            val captures = tag.getCaptures(mapID)

            if (captures.isEmpty()) {
                location = world.spawnLocation
                location.y = world.getHighestBlockYAt(location).toDouble()
                player.sendMessage(notFound)
                log?.println("Spawn tag \'${tag.name}\' is not captured in: $mapID")
            } else {
                val c = captures.random() as SpawnCapture
                location = Location(world, c.x, c.y, c.z, c.yaw, c.pitch)
            }
        }

        fun protect() {
            val gracePeriod = Main.getConfig()?.getLong("spawn.invincible", 60L) ?: 60L

            player.isInvulnerable = true
            scheduler.runTaskLater(plugin, Runnable {
                player.isInvulnerable = false
            }, Timer(TimeUnit.TICK, gracePeriod).toTick())
        }

        if (asyncCallback == null) {
            player.teleport(location)
            protect()
        } else {
            scheduler.runTaskAsynchronously(plugin, Runnable {
                player.teleportAsync(location, PlayerTeleportEvent.TeleportCause.PLUGIN)
                        .thenAccept(asyncCallback::accept)
                        .thenAccept { protect() }
                        .exceptionally { it.printStackTrace(); return@exceptionally null }
            })
        }
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
                val livingPlayers = playerModule.getLivingPlayers()

                // FIXME timer is 0 after a game ends.
                val format = timer.format(false)
                val title = StringBuilder("\u00A76GAME TIME \u00A77- ")
                val progress = timer.toSecond().toDouble() / fullTime.toSecond()

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

                if (livingPlayers.size == 1) {
                    val player = livingPlayers.first()
                    val team = Module.getTeamModule(game).getPlayerTeam(player)
                    val timer = Timer(TimeUnit.SECOND, 5)

                    if (team != null) {
                        finishGame(team, timer)
                    } else {
                        finishGame(player, timer)
                    }
                    this.cancel()
                } else {
                    timer.subtract(TimeUnit.SECOND, 1)

                    if (livingPlayers.isEmpty() || timer.toSecond() < 1) {
                        drawGame(Timer(TimeUnit.SECOND, 5))
                        this.cancel()
                        return
                    }
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

}