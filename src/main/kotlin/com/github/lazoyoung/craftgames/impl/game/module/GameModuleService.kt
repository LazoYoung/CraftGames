package com.github.lazoyoung.craftgames.impl.game.module

import com.github.lazoyoung.craftgames.api.GameResult
import com.github.lazoyoung.craftgames.api.TimeUnit
import com.github.lazoyoung.craftgames.api.Timer
import com.github.lazoyoung.craftgames.api.event.GameFinishEvent
import com.github.lazoyoung.craftgames.api.event.GameStartEvent
import com.github.lazoyoung.craftgames.api.event.GameTimeoutEvent
import com.github.lazoyoung.craftgames.api.module.GameModule
import com.github.lazoyoung.craftgames.impl.Main
import com.github.lazoyoung.craftgames.impl.exception.DependencyNotFound
import com.github.lazoyoung.craftgames.impl.exception.MapNotFound
import com.github.lazoyoung.craftgames.impl.game.Game
import com.github.lazoyoung.craftgames.impl.game.GamePhase
import com.github.lazoyoung.craftgames.impl.game.player.GamePlayer
import com.github.lazoyoung.craftgames.impl.game.player.PlayerData
import com.github.lazoyoung.craftgames.impl.game.player.RestoreMode
import com.github.lazoyoung.craftgames.impl.util.DependencyUtil
import net.md_5.bungee.api.chat.TextComponent
import net.milkbowl.vault.economy.Economy
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.NamespacedKey
import org.bukkit.boss.BarColor
import org.bukkit.boss.BarStyle
import org.bukkit.entity.Player
import org.bukkit.loot.LootTable
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scoreboard.Team
import java.util.*
import java.util.concurrent.CompletableFuture

class GameModuleService internal constructor(private val game: Game) : GameModule, Service {

    internal var lastManStanding = false
    internal var keepInventory = false
    internal var dropItems = false
    internal var defaultGameMode = GameMode.ADVENTURE
    internal var canJoinAfterStart = false
    internal var canRespawn = false
    internal var respawnTimer = Timer(TimeUnit.SECOND, 5)
    internal var minPlayer = 1
    internal var maxPlayer = 10
    private var timer = Timer(TimeUnit.MINUTE, 3)
    private var fullTime = Timer(TimeUnit.MINUTE, 3)
    private val script = game.resource.mainScript

    /* Service handling bossbar and timer */
    private var serviceTask: BukkitRunnable? = null

    // Bossbar facility
    private val bossBarKey = NamespacedKey(Main.instance, "timer-${game.id}")
    internal val bossBar = Bukkit.createBossBar(bossBarKey, "TIME - 00:00:00", BarColor.WHITE, BarStyle.SOLID)

    override fun getTimer(): Timer {
        return timer
    }

    override fun setTimer(timer: Timer) {
        this.fullTime = timer.clone()
        this.timer = timer.clone()
    }

    override fun setPlayerCapacity(min: Int, max: Int) {
        this.minPlayer = min
        this.maxPlayer = max
    }

    override fun setCanJoinAfterStart(boolean: Boolean) {
        this.canJoinAfterStart = boolean
    }

    override fun setCanRespawn(boolean: Boolean) {
        this.canRespawn = boolean
    }

    override fun setKeepInventory(keep: Boolean, drop: Boolean) {
        if (keep && drop) {
            throw IllegalArgumentException()
        }

        this.keepInventory = keep
        this.dropItems = drop
    }

    override fun setRespawnTimer(timer: Timer) {
        this.respawnTimer = timer
    }

    override fun setLastManStanding(enable: Boolean) {
        this.lastManStanding = enable
    }

    override fun setGameMode(mode: GameMode) {
        this.defaultGameMode = mode
    }

    override fun setPVP(pvp: Boolean) {
        val world = game.map.world ?: throw MapNotFound()

        world.pvp = pvp
    }

    override fun setMoneyReward(player: Player, amount: Double) {
        if (!DependencyUtil.VAULT_ECONOMY.isLoaded())
            throw DependencyNotFound("Vault & Economy plugin is required to reward money.")

        val playerData = PlayerData.get(player)
                ?: throw IllegalArgumentException("Player ${player.name} isn't playing this game.")
        val economy = DependencyUtil.VAULT_ECONOMY.getService() as Economy
        val format = economy.format(amount)

        playerData.moneyReward = amount
        script.printDebug("Reward $format is assigned to ${player.name}.")
    }

    override fun setItemReward(player: Player, lootTable: LootTable) {
        if (!DependencyUtil.LOOT_TABLE_FIX.isLoaded())
            throw DependencyNotFound("LootTableFix plugin is required.")

        val playerData = PlayerData.get(player)
                ?: throw IllegalArgumentException("Player ${player.name} isn't playing this game.")

        playerData.itemReward = lootTable
        script.printDebug("Reward ${lootTable.key} is assigned to ${player.name}.")
    }

    override fun broadcast(message: String) {
        game.getPlayers().forEach {
            it.sendMessage(*TextComponent.fromLegacyText(message.replace('&', '\u00A7')))
        }
    }

    override fun finishGame(winner: Team, timer: Timer) {
        if (game.phase != GamePhase.PLAYING)
            return

        val winners = game.getTeamService().getPlayers(winner)

        // Fire event
        Bukkit.getPluginManager().callEvent(GameFinishEvent(game, GameResult.TEAM_WIN, winner, winners))

        // Ceremony and close
        broadcast("&6Congratulations, &r${winner.color}${winner.displayName} &6won the game!")
        game.close(timer = timer)
    }

    override fun finishGame(winner: Player, timer: Timer) {
        if (game.phase != GamePhase.PLAYING)
            return

        // Fire event
        Bukkit.getPluginManager().callEvent(GameFinishEvent(game, GameResult.SOLO_WIN, null, listOf(winner)))

        // Ceremony and close
        broadcast("&6Congratulations, &r${winner.displayName} &6won the game!")
        game.close(timer = timer)
    }

    @Deprecated("This is for internal use.")
    override fun drawGame(timer: Timer) {
        if (game.phase != GamePhase.PLAYING)
            return

        // Fire event
        Bukkit.getPluginManager().callEvent(GameFinishEvent(game, GameResult.DRAW, null, null))

        // Ceremony and close
        broadcast("&6Time out! The game ended in a draw...")
        game.close(timer = timer)
    }

    @Suppress("DEPRECATION")
    override fun start() {
        val playerModule = game.getPlayerService()
        val worldModule = game.getWorldService()
        val teleportFutures = LinkedList<CompletableFuture<Boolean>>()
        var index = 0

        // Setup players
        game.getPlayers().mapNotNull { PlayerData.get(it) }.forEach { p ->

            val future = if (p is GamePlayer) {
                p.restore(RestoreMode.RESPAWN)
                worldModule.teleportSpawn(p, index++)
            } else {
                p.restore(RestoreMode.JOIN)
                worldModule.teleportSpawn(p, null)
            }

            teleportFutures.add(future)
            bossBar.addPlayer(p.getPlayer())
        }

        val preparation = CompletableFuture.allOf(*teleportFutures.toTypedArray())

        preparation.whenCompleteAsync { _, t ->
            if (t != null) {
                t.printStackTrace()
                game.getGameService().broadcast("&cFailed to teleport into field.")
                game.forceStop(error = true)
                return@whenCompleteAsync
            }

            // Trigger GameStartEvent
            Bukkit.getScheduler().runTask(Main.instance, Runnable {
                Bukkit.getPluginManager().callEvent(GameStartEvent(game))
            })

            serviceTask = object : BukkitRunnable() {
                override fun run() {
                    val livingPlayers = playerModule.getLivingPlayers()
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
                    timer.subtract(TimeUnit.SECOND, 1)

                    if (livingPlayers.isEmpty() || timer.toSecond() < 0) {
                        Bukkit.getPluginManager().callEvent(GameTimeoutEvent(game))

                        if (game.phase != GamePhase.FINISH) {
                            drawGame(Timer(TimeUnit.SECOND, 5))
                        }

                        this.cancel()
                        return
                    }
                }
            }
            serviceTask!!.runTaskTimer(Main.instance, 0L, 20L)
        }
    }

    override fun terminate() {
        bossBar.removeAll()
        Bukkit.removeBossBar(bossBarKey)
        serviceTask?.cancel()
    }

}