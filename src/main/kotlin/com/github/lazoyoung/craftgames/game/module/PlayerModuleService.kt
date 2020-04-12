package com.github.lazoyoung.craftgames.game.module

import com.destroystokyo.paper.Title
import com.github.lazoyoung.craftgames.Main
import com.github.lazoyoung.craftgames.api.ActionbarTask
import com.github.lazoyoung.craftgames.api.PlayerType
import com.github.lazoyoung.craftgames.api.TimeUnit
import com.github.lazoyoung.craftgames.api.Timer
import com.github.lazoyoung.craftgames.api.module.PlayerModule
import com.github.lazoyoung.craftgames.command.RESET_FORMAT
import com.github.lazoyoung.craftgames.coordtag.capture.SpawnCapture
import com.github.lazoyoung.craftgames.coordtag.tag.CoordTag
import com.github.lazoyoung.craftgames.coordtag.tag.TagMode
import com.github.lazoyoung.craftgames.game.Game
import com.github.lazoyoung.craftgames.game.player.GameEditor
import com.github.lazoyoung.craftgames.game.player.GamePlayer
import com.github.lazoyoung.craftgames.game.player.PlayerData
import com.github.lazoyoung.craftgames.game.player.Spectator
import com.github.lazoyoung.craftgames.internal.exception.MapNotFound
import net.md_5.bungee.api.ChatColor
import net.md_5.bungee.api.chat.ComponentBuilder
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitRunnable
import java.util.*
import java.util.function.Consumer
import kotlin.collections.HashMap

class PlayerModuleService internal constructor(private val game: Game) : PlayerModule {

    internal var respawnTimer = HashMap<UUID, Timer>()
    private val personalSpawn = HashMap<UUID, Location>()
    private var playerSpawn: CoordTag? = null
    private var editorSpawn: CoordTag? = null
    private var spectatorSpawn: CoordTag? = null
    internal val killTriggers = HashMap<UUID, Consumer<LivingEntity>>()
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
        val gamePlayer = PlayerData.get(player) as? GamePlayer
                ?: throw IllegalArgumentException("Player ${player.name} is not online.")
        val title = ComponentBuilder("YOU DIED").color(ChatColor.RED).create()
        val subTitle = ComponentBuilder("Type ").color(ChatColor.GRAY)
                .append("/leave").color(ChatColor.WHITE).bold(true).append(" to exit.", RESET_FORMAT).color(ChatColor.GRAY).create()

        player.gameMode = GameMode.SPECTATOR
        player.sendTitle(Title(title, subTitle, 20, 80, 20))
        gamePlayer.toSpectator()
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

    override fun overrideSpawnpoint(player: Player, tagName: String, index: Int?) {
        val tag = Module.getRelevantTag(game, tagName, TagMode.SPAWN)
        val location = getSpawnpointByTag(tag, index)

        overrideSpawnpoint(player, location)
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

    override fun setRespawnTimer(player: Player, timer: Timer) {
        this.respawnTimer[player.uniqueId] = timer
    }

    override fun setKillTrigger(killer: Player, trigger: Consumer<LivingEntity>?) {
        val name = killer.name
        val uid = killer.uniqueId

        if (trigger == null) {
            if (killTriggers.containsKey(uid)) {
                killTriggers.remove(uid)
                script.printDebug("A Kill trigger is un-bound from: $name")
            }
        } else {
            killTriggers[uid] = Consumer { livingEntity ->
                try {
                    trigger.accept(livingEntity)
                } catch (e: Exception) {
                    script.writeStackTrace(e)
                    script.print("Error occurred in Kill trigger: $name")
                }
            }
            script.printDebug("A kill trigger is bound to $name.")
        }
    }

    override fun setDeathTrigger(player: Player, respawn: Boolean, trigger: Runnable?) {}

    override fun setSpawn(type: String, spawnTag: String) {
        setSpawnpoint(type, spawnTag)
    }

    override fun sendMessage(player: Player, message: String) {}

    fun getSpawnpoint(playerData: PlayerData, index: Int?): Location {
        val uid = playerData.getPlayer().uniqueId
        val script = game.resource.script
        val world = game.map.world ?: throw MapNotFound()
        var location = personalSpawn[uid]
        val tag = when (playerData) {
            is GameEditor -> editorSpawn
            is Spectator -> spectatorSpawn
            is GamePlayer -> {
                if (location == null) {
                    Module.getTeamModule(game).getSpawnpoint(playerData.getPlayer()) ?: playerSpawn
                } else {
                    null
                }
            }
            else -> null
        }

        if (tag != null) {
            location = getSpawnpointByTag(tag, index)
        } else if (location == null) {
            location = world.spawnLocation
            location.y = world.getHighestBlockYAt(location).toDouble()
            script.print("Spawn tag is not defined for ${playerData.getPlayer().name}!")
        }

        return location
    }

    internal fun respawn(gamePlayer: GamePlayer) {
        val player = gamePlayer.getPlayer()
        val plugin = Main.instance
        val scheduler = Bukkit.getScheduler()
        val timer = respawnTimer[player.uniqueId]?.clone()
                ?: Module.getGameModule(game).respawnTimer.clone()
        val gracePeriod = Main.getConfig()?.getLong("spawn-invincible", 60L)
                ?: 60L

        gamePlayer.restore(false)
        player.gameMode = GameMode.SPECTATOR

        object : BukkitRunnable() {
            override fun run() {
                if (!Module.getPlayerModule(game).isOnline(player)) {
                    this.cancel()
                    return
                }

                val location = player.location
                val frame = Timer(TimeUnit.SECOND, 1)
                val format = timer.format(true)

                if (location.y < -16.0) {
                    location.y = -16.0
                    player.teleport(location)
                }

                ActionbarTask(player, period = frame, text = *arrayOf("&eRespawning in $format."))
                        .start()

                if (timer.subtract(frame).toSecond() < 0L) {
                    // Rollback to spawnpoint and gear up
                    gamePlayer.restore(false)
                    Module.getItemModule(game).applyKit(player)
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

    private fun getSpawnpointByTag(tag: CoordTag, index: Int?): Location {
        val world = game.map.world ?: throw MapNotFound()
        val mapID = game.map.id
        val captures = tag.getCaptures(mapID)
        val location: Location

        if (captures.isEmpty()) {
            location = world.spawnLocation
            location.y = world.getHighestBlockYAt(location).toDouble()
            script.print("Spawn tag \'${tag.name}\' is not captured in: $mapID")
        } else {
            val c = if (index != null) {
                captures[index % captures.size] as SpawnCapture
            } else {
                captures.random() as SpawnCapture
            }

            location = Location(world, c.x, c.y, c.z, c.yaw, c.pitch)
        }

        return location
    }

}