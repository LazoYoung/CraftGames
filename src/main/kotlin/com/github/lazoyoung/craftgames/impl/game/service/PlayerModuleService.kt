package com.github.lazoyoung.craftgames.impl.game.service

import com.destroystokyo.paper.Title
import com.github.lazoyoung.craftgames.api.ActionbarTask
import com.github.lazoyoung.craftgames.api.PlayerType
import com.github.lazoyoung.craftgames.api.TimeUnit
import com.github.lazoyoung.craftgames.api.Timer
import com.github.lazoyoung.craftgames.api.tag.coordinate.AreaCapture
import com.github.lazoyoung.craftgames.api.tag.coordinate.SpawnCapture
import com.github.lazoyoung.craftgames.api.tag.coordinate.CoordTag
import com.github.lazoyoung.craftgames.api.tag.coordinate.TagMode
import com.github.lazoyoung.craftgames.api.module.PlayerModule
import com.github.lazoyoung.craftgames.impl.Main
import com.github.lazoyoung.craftgames.impl.command.RESET_FORMAT
import com.github.lazoyoung.craftgames.impl.exception.DependencyNotFound
import com.github.lazoyoung.craftgames.impl.exception.MapNotFound
import com.github.lazoyoung.craftgames.impl.game.Game
import com.github.lazoyoung.craftgames.impl.game.GamePhase
import com.github.lazoyoung.craftgames.impl.game.player.*
import com.github.lazoyoung.craftgames.impl.util.DependencyUtil
import me.libraryaddict.disguise.DisguiseAPI
import me.libraryaddict.disguise.disguisetypes.*
import net.md_5.bungee.api.ChatColor
import net.md_5.bungee.api.chat.ComponentBuilder
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitRunnable
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer
import kotlin.collections.HashMap

class PlayerModuleService internal constructor(private val game: Game) : PlayerModule, Service {

    internal var respawnTimer = HashMap<UUID, Timer>()
    private val personalSpawn = HashMap<UUID, Location>()
    private var playerSpawn: CoordTag? = null
    private var editorSpawn: CoordTag? = null
    private var spectatorSpawn: CoordTag? = null
    private val disguises = HashMap<UUID, Disguise>()
    private val maxAttempt = Main.getConfig()?.getInt("optimization.safezone-calculation.player-throttle", 10) ?: 10
    private val script = game.resource.mainScript

    override fun getLivingPlayers(): List<Player> {
        return game.getPlayers().filter { PlayerData.get(it) is GamePlayer }
    }

    override fun getDeadPlayers(): List<Player> {
        return game.getPlayers().filter { PlayerData.get(it) is Spectator }
    }

    override fun getPlayersInside(areaTag: String, callback: Consumer<List<Player>>) {
        error("Deprecated function.")
    }

    override fun getPlayersInside(areaTag: CoordTag, callback: Consumer<List<Player>>) {
        game.getWorldService().getEntitiesInside(areaTag, callback)
    }

    override fun isOnline(player: Player): Boolean {
        return game.getPlayers().contains(player)
    }

    @Suppress("DEPRECATION")
    override fun eliminate(player: Player) {
        val gamePlayer = PlayerData.get(player) as? GamePlayer
                ?: throw IllegalArgumentException("Player ${player.name} is not online.")
        val title = ComponentBuilder("YOU DIED").color(ChatColor.RED).create()
        val subTitle = ComponentBuilder("Type ").color(ChatColor.GRAY)
                .append("/leave").color(ChatColor.WHITE).bold(true).append(" to exit.", RESET_FORMAT).color(ChatColor.GRAY).create()

        player.gameMode = GameMode.SPECTATOR
        player.sendTitle(Title(title, subTitle, 20, 80, 20))
        gamePlayer.toSpectator()

        if (game.getGameService().lastManStanding) {
            val gameService = game.getGameService()
            val teamService = game.getTeamService()
            val survivors = getLivingPlayers().minus(player)
            val firstSurvivor = survivors.firstOrNull()
            val firstSurvivorTeam = firstSurvivor?.let { teamService.getPlayerTeam(it) }

            if (survivors.size > 1) {
                if (firstSurvivorTeam == null) {
                    return // Solo mode
                }

                survivors.minus(firstSurvivor).forEach {
                    if (firstSurvivorTeam.name != teamService.getPlayerTeam(it)?.name) {
                        return // More than 2 teams alive
                    }
                }
            }

            val timer = Timer(TimeUnit.SECOND, 5)

            when {
                firstSurvivorTeam != null -> {
                    gameService.finishGame(firstSurvivorTeam, timer)
                }
                firstSurvivor != null -> {
                    gameService.finishGame(firstSurvivor, timer)
                }
                else -> {
                    gameService.drawGame(timer)
                }
            }
        }
    }

    override fun disguiseAsPlayer(player: Player, skinName: String, selfVisible: Boolean) {
        if (!DependencyUtil.LIBS_DISGUISES.isLoaded()) {
            throw DependencyNotFound("LibsDisguises is required.")
        }

        val disguise = PlayerDisguise(skinName)

        disguise.entity = player
        disguise.isSelfDisguiseVisible = selfVisible
        startDisguise(disguise, player, skinName)
    }

    override fun disguiseAsMob(player: Player, type: EntityType, isAdult: Boolean, selfVisible: Boolean) {
        if (!DependencyUtil.LIBS_DISGUISES.isLoaded()) {
            throw DependencyNotFound("LibsDisguises is required.")
        }

        val disguiseType = DisguiseType.getType(type)
        val disguise = MobDisguise(disguiseType, isAdult)

        disguise.entity = player
        disguise.isSelfDisguiseVisible = selfVisible
        startDisguise(disguise, player, type.key.toString())
    }

    override fun disguiseAsBlock(player: Player, material: Material, selfVisible: Boolean) {
        if (!DependencyUtil.LIBS_DISGUISES.isLoaded()) {
            throw DependencyNotFound("LibsDisguises is required.")
        }

        val disguise = MiscDisguise(DisguiseType.FALLING_BLOCK, material)

        disguise.entity = player
        disguise.isSelfDisguiseVisible = selfVisible
        startDisguise(disguise, player, material.key.toString())
    }

    override fun disguiseAsCustomPreset(player: Player, name: String, selfVisible: Boolean) {
        if (!DependencyUtil.LIBS_DISGUISES.isLoaded()) {
            throw DependencyNotFound("LibsDisguises is required.")
        }

        val disguise = DisguiseAPI.getCustomDisguise(name) as CustomDisguise
        disguise.entity = player
        disguise.isSelfDisguiseVisible = selfVisible
        startDisguise(disguise, player, name)
    }

    override fun undisguise(player: Player) {
        if (!DependencyUtil.LIBS_DISGUISES.isLoaded()) {
            throw DependencyNotFound("LibsDisguises is required.")
        }

        disguises.computeIfPresent(player.uniqueId) { _, disguise ->
            disguise.stopDisguise()
            script.printDebug("Undisguised: ${player.name}")
            null
        }
    }

    override fun setSpawnpoint(type: PlayerType, spawnTag: String) {
        error("Deprecated function.")
    }

    override fun setSpawnpoint(type: PlayerType, tag: CoordTag) {
        val mode = tag.mode

        require(mode == TagMode.AREA || mode == TagMode.SPAWN) {
            "Illegal tag mode: ${mode.label}"
        }

        when (type) {
            PlayerType.PLAYER -> playerSpawn = tag
            PlayerType.EDITOR -> editorSpawn = tag
            PlayerType.SPECTATOR -> spectatorSpawn = tag
        }
    }

    override fun overrideSpawnpoint(player: Player, tagName: String, index: Int) {
        error("Deprecated function.")
    }

    override fun overrideSpawnpoint(player: Player, tag: CoordTag, index: Int) {
        val mode = tag.mode

        require(mode == TagMode.SPAWN || mode == TagMode.AREA) {
            "Illegal tag mode: ${mode.label}"
        }

        getSpawnpointByTag(tag, index).handleAsync { location, t ->
            if (t != null) {
                t.printStackTrace()
                script.print("Failed to override ${player.name}'s spawnpoint to ${tag.name}/$index.")
            } else {
                Bukkit.getScheduler().runTask(Main.instance, Runnable {
                    overrideSpawnpoint(player, location)
                })
            }
        }
    }

    override fun overrideSpawnpoint(player: Player, location: Location) {
        val x = location.blockX
        val y = location.blockY
        val z = location.blockZ

        personalSpawn[player.uniqueId] = location
        script.printDebug("Spawnpoint ($x, $y, $z) is set for ${player.name}.")
    }

    override fun resetSpawnpoint(player: Player) {
        if (personalSpawn.remove(player.uniqueId) != null) {
            script.printDebug("Spawnpoint for ${player.name} is reset to default.")
        }
    }

    fun getSpawnpoint(playerData: PlayerData, index: Int?): CompletableFuture<Location> {
        val uid = playerData.getPlayer().uniqueId
        val script = game.resource.mainScript
        val world = game.getWorldService().getWorld()
        val personalSpawn = personalSpawn[uid]

        val tag: CoordTag? = when (playerData) {
            is GameEditor -> editorSpawn
            is Spectator -> spectatorSpawn
            is GamePlayer -> {
                if (personalSpawn != null) {
                    null
                } else {
                    game.getTeamService().getSpawnpoint(playerData.getPlayer()) ?: playerSpawn
                }
            }
            else -> throw error("Illegal type of PlayerData.")
        }

        fun getFallbackSpawnpoint(): Location {
            val location = world.spawnLocation
            location.y = world.getHighestBlockYAt(location).toDouble()
            return location
        }

        return when {
            tag != null -> {
                getSpawnpointByTag(tag, index).exceptionally {
                    script.print("Failed to calculate safezone for ${playerData.getPlayer().name}'s spawnpoint $index.")
                    script.print("See error stacktrace for details.")
                    script.writeStackTrace(it)
                    getFallbackSpawnpoint()
                }
            }
            personalSpawn != null -> {
                CompletableFuture.completedFuture(personalSpawn)
            }
            else -> {
                CompletableFuture.completedFuture(getFallbackSpawnpoint())
            }
        }
    }

    internal fun respawn(gamePlayer: GamePlayer) {
        val player = gamePlayer.getPlayer()
        val plugin = Main.instance
        val scheduler = Bukkit.getScheduler()
        val playerService = game.getPlayerService()
        val timer = respawnTimer[player.uniqueId]?.clone()
                ?: game.getGameService().respawnTimer.clone()
        val gracePeriod = Main.getConfig()?.getLong("spawn-invincible", 60L)
                ?: 60L

        player.gameMode = GameMode.SPECTATOR

        object : BukkitRunnable() {
            override fun run() {
                if (game.phase != GamePhase.PLAYING || !playerService.isOnline(player)) {
                    this.cancel()
                    return
                }

                val frame = Timer(TimeUnit.SECOND, 1)
                val format = timer.format(true)

                ActionbarTask(player, period = frame, text = *arrayOf("&eRespawning in $format."))
                        .start()

                if (timer.subtract(frame).toSecond() < 0L) {
                    // Return to spawnpoint and gear up
                    gamePlayer.restore(RestoreMode.RESPAWN)
                    game.getWorldService().teleportSpawn(gamePlayer, null)
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

    override fun start() {}

    override fun terminate() {
        disguises.values.forEach {
            it.stopDisguise()
        }
    }

    internal fun startDisguise(disguise: Disguise, player: Player, target: String) {
        disguises.compute(player.uniqueId) { _, presentDisguise ->
            presentDisguise?.stopDisguise()

            if (disguise.startDisguise()) {
                script.printDebug("Disguised ${player.name} as $target.")
            } else {
                script.print("Failed to disguise ${player.name} as $target.")
            }

            disguise
        }
    }

    /**
     * @throws IllegalArgumentException is thrown inside [CompletableFuture]
     * if [tag] mode is not relevant.
     */
    private fun getSpawnpointByTag(tag: CoordTag, index: Int?): CompletableFuture<Location> {
        val world = game.map.world ?: throw MapNotFound()
        val mapID = game.map.id
        val captures = tag.getCaptures(mapID)
        val future: CompletableFuture<Location>

        if (captures.isEmpty()) {
            val loc = world.spawnLocation
            future = CompletableFuture<Location>()

            loc.y = world.getHighestBlockYAt(loc).toDouble()
            future.complete(loc)
            script.print("Spawn tag \'${tag.name}\' is not captured in: $mapID")
        } else {
            val capture = if (index != null) {
                captures[index % captures.size]
            } else {
                captures.random()
            }

            when (capture) {
                is AreaCapture -> {
                    future = capture.toLocation(world, maxAttempt)
                }
                is SpawnCapture -> {
                    future = CompletableFuture<Location>()
                    future.complete(capture.toLocation(world))
                }
                else -> {
                    future = CompletableFuture()
                    future.completeExceptionally(IllegalArgumentException("Tag mode is irrelevant."))
                }
            }
        }

        return future
    }

}