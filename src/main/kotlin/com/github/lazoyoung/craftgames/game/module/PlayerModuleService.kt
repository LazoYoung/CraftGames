package com.github.lazoyoung.craftgames.game.module

import com.destroystokyo.paper.Title
import com.github.lazoyoung.craftgames.Main
import com.github.lazoyoung.craftgames.api.ActionbarTask
import com.github.lazoyoung.craftgames.api.PlayerType
import com.github.lazoyoung.craftgames.api.TimeUnit
import com.github.lazoyoung.craftgames.api.Timer
import com.github.lazoyoung.craftgames.api.module.PlayerModule
import com.github.lazoyoung.craftgames.command.RESET_FORMAT
import com.github.lazoyoung.craftgames.coordtag.capture.AreaCapture
import com.github.lazoyoung.craftgames.coordtag.capture.SpawnCapture
import com.github.lazoyoung.craftgames.coordtag.tag.CoordTag
import com.github.lazoyoung.craftgames.coordtag.tag.TagMode
import com.github.lazoyoung.craftgames.game.Game
import com.github.lazoyoung.craftgames.game.player.*
import com.github.lazoyoung.craftgames.internal.exception.DependencyNotFound
import com.github.lazoyoung.craftgames.internal.exception.MapNotFound
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

class PlayerModuleService internal constructor(private val game: Game) : PlayerModule {

    internal var respawnTimer = HashMap<UUID, Timer>()
    private val personalSpawn = HashMap<UUID, Location>()
    private var playerSpawn: CoordTag? = null
    private var editorSpawn: CoordTag? = null
    private var spectatorSpawn: CoordTag? = null
    private val disguises = HashMap<UUID, Disguise>()
    private val maxAttempt = Main.getConfig()?.getInt("optimization.safezone-calculation.player-throttle", 10) ?: 10
    private val script = game.resource.script

    override fun getLivingPlayers(): List<Player> {
        return game.getPlayers().filter { PlayerData.get(it) is GamePlayer }
    }

    override fun getDeadPlayers(): List<Player> {
        return game.getPlayers().filter { PlayerData.get(it) is Spectator }
    }

    override fun getPlayersInside(areaTag: String, callback: Consumer<List<Player>>) {
        Module.getWorldModule(game).getEntitiesInside(areaTag, Consumer<List<Player>> {
            callback.accept(it)
            script.printDebug("Found ${it.size} players inside area: $areaTag")
        })
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

        if (Module.getGameModule(game).lastManStanding) {
            val gameModule = Module.getGameModule(game)
            val teamModule = Module.getTeamModule(game)
            val survivors = getLivingPlayers().minus(player)
            val firstSurvivor = survivors.firstOrNull()
            val firstSurvivorTeam = firstSurvivor?.let { teamModule.getPlayerTeam(it) }

            if (survivors.size > 1) {
                if (firstSurvivorTeam == null) {
                    return // Solo mode
                }

                survivors.minus(firstSurvivor).forEach {
                    if (firstSurvivorTeam.name != teamModule.getPlayerTeam(it)?.name) {
                        return // More than 2 teams alive
                    }
                }
            }

            val timer = Timer(TimeUnit.SECOND, 5)

            when {
                firstSurvivorTeam != null -> {
                    gameModule.finishGame(firstSurvivorTeam, timer)
                }
                firstSurvivor != null -> {
                    gameModule.finishGame(firstSurvivor, timer)
                }
                else -> {
                    gameModule.drawGame(timer)
                }
            }
        }
    }

    override fun disguiseAsPlayer(player: Player, skinName: String, selfVisible: Boolean): PlayerDisguise {
        if (!Main.libsDisguises) {
            throw DependencyNotFound("LibsDisguises is required.")
        }

        val disguise = PlayerDisguise(skinName)

        disguise.entity = player
        disguise.isSelfDisguiseVisible = selfVisible
        startDisguise(disguise, player, skinName)
        return disguise
    }

    override fun disguiseAsMob(player: Player, type: EntityType, isAdult: Boolean, selfVisible: Boolean): MobDisguise {
        if (!Main.libsDisguises) {
            throw DependencyNotFound("LibsDisguises is required.")
        }

        val disguiseType = DisguiseType.getType(type)
        val disguise = MobDisguise(disguiseType, isAdult)

        disguise.entity = player
        disguise.isSelfDisguiseVisible = selfVisible
        startDisguise(disguise, player, type.key.toString())
        return disguise
    }

    override fun disguiseAsBlock(player: Player, material: Material, selfVisible: Boolean): MiscDisguise {
        if (!Main.libsDisguises) {
            throw DependencyNotFound("LibsDisguises is required.")
        }

        val disguise = MiscDisguise(DisguiseType.FALLING_BLOCK, material)

        disguise.entity = player
        disguise.isSelfDisguiseVisible = selfVisible
        startDisguise(disguise, player, material.key.toString())
        return disguise
    }

    override fun undisguise(player: Player) {
        if (!Main.libsDisguises) {
            throw DependencyNotFound("LibsDisguises is required.")
        }

        disguises.computeIfPresent(player.uniqueId) { _, disguise ->
            disguise.stopDisguise()
            script.printDebug("Undisguised: ${player.name}")
            null
        }
    }

    override fun setSpawnpoint(type: PlayerType, spawnTag: String) {
        val tag = Module.getRelevantTag(game, spawnTag, TagMode.SPAWN)

        when (type) {
            PlayerType.PLAYER -> playerSpawn = tag
            PlayerType.EDITOR -> editorSpawn = tag
            PlayerType.SPECTATOR -> spectatorSpawn = tag
        }
    }

    override fun setSpawnpoint(type: String, spawnTag: String) {
        setSpawnpoint(PlayerType.valueOf(type), spawnTag)
    }

    override fun overrideSpawnpoint(player: Player, tagName: String, index: Int) {
        val tag = Module.getRelevantTag(game, tagName, TagMode.SPAWN, TagMode.AREA)

        getSpawnpointByTag(tag, index).handle { location, t ->
            if (location == null || t != null) {
                t?.printStackTrace()
                script.print("Failed to override ${player.name}'s spawnpoint to $tagName/$index.")
            } else {
                overrideSpawnpoint(player, location)
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
        val script = game.resource.script
        val world = Module.getWorldModule(game).getWorld()
        val personalSpawn = personalSpawn[uid]
        var future = CompletableFuture.supplyAsync {
            personalSpawn
        }

        val tag = when (playerData) {
            is GameEditor -> editorSpawn
            is Spectator -> spectatorSpawn
            is GamePlayer -> {
                if (future.get() == null) {
                    Module.getTeamModule(game).getSpawnpoint(playerData.getPlayer()) ?: playerSpawn
                } else {
                    null
                }
            }
            else -> null
        }

        if (tag != null) {
            try {
                future = getSpawnpointByTag(tag, index)
            } catch (e: IllegalStateException) {
                script.print("Failed to calculate safezone for ${playerData.getPlayer().name}'s spawnpoint $index.")
                script.print("See error stacktrace for details.")
                script.writeStackTrace(e)
            }
        }

        return future.thenCompose {
            val loc =  if (it == null) {
                val location = world.spawnLocation
                location.y = world.getHighestBlockYAt(location).toDouble()
                location
            } else {
                it
            }

            CompletableFuture.supplyAsync { loc }
        }
    }

    internal fun respawn(gamePlayer: GamePlayer) {
        val player = gamePlayer.getPlayer()
        val plugin = Main.instance
        val scheduler = Bukkit.getScheduler()
        val playerModule = Module.getPlayerModule(game)
        val timer = respawnTimer[player.uniqueId]?.clone()
                ?: Module.getGameModule(game).respawnTimer.clone()
        val gracePeriod = Main.getConfig()?.getLong("spawn-invincible", 60L)
                ?: 60L

        player.gameMode = GameMode.SPECTATOR

        object : BukkitRunnable() {
            override fun run() {
                if (game.phase != Game.Phase.PLAYING || !playerModule.isOnline(player)) {
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

    internal fun terminate() {
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
     * @throws IllegalArgumentException is thrown if [tag] mode is not relevant.
     */
    private fun getSpawnpointByTag(tag: CoordTag, index: Int?): CompletableFuture<Location?> {
        val world = game.map.world ?: throw MapNotFound()
        val mapID = game.map.id
        val captures = tag.getCaptures(mapID)
        val future: CompletableFuture<Location?>

        if (captures.isEmpty()) {
            val loc = world.spawnLocation
            future = CompletableFuture<Location?>()

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
                    future = CompletableFuture<Location?>()
                    future.complete(capture.toLocation(world))
                }
                else -> {
                    throw IllegalArgumentException("Tag mode is irrelevant.")
                }
            }
        }

        return future
    }

}