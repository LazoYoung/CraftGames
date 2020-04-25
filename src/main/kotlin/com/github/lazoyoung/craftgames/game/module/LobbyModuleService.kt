package com.github.lazoyoung.craftgames.game.module

import com.github.lazoyoung.craftgames.Main
import com.github.lazoyoung.craftgames.api.TimeUnit
import com.github.lazoyoung.craftgames.api.Timer
import com.github.lazoyoung.craftgames.api.module.LobbyModule
import com.github.lazoyoung.craftgames.game.Game
import com.github.lazoyoung.craftgames.internal.exception.MapNotFound
import net.md_5.bungee.api.ChatColor
import net.md_5.bungee.api.chat.ComponentBuilder
import net.md_5.bungee.api.chat.TextComponent
import org.bukkit.Bukkit
import org.bukkit.Difficulty
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitRunnable
import java.util.*
import kotlin.collections.HashMap


class LobbyModuleService internal constructor(private val game: Game) : LobbyModule {

    internal var exitLoc: Location? = null
    internal var exitServer: String? = null
    private var loc: Location? = null
    private var constTimer = Timer(TimeUnit.SECOND, 30).toSecond()
    private var timer = constTimer
    private val voted = ArrayList<UUID>()
    private val votes = HashMap<String, Int>()
    private val notFound = ComponentBuilder("Unable to locate lobby position!")
            .color(ChatColor.RED).create().first() as TextComponent
    private var ticking = false
    private var serviceTask: BukkitRunnable? = null
    private var minimum = 1

    override fun setSpawnpoint(x: Double, y: Double, z: Double) {
        loc = Location(null, x, y, z)
    }

    override fun setSpawnpoint(x: Double, y: Double, z: Double, yaw: Float, pitch: Float) {
        loc = Location(null, x, y, z, yaw, pitch)
    }

    override fun setTimer(timer: Timer) {
        this.constTimer = timer.toSecond()
        this.timer = constTimer
    }

    override fun setExitWorld(world: String, x: Double, y: Double, z: Double) {
        val bukkitWorld = Bukkit.getWorld(world)
                ?: throw IllegalArgumentException("World $world does not exist.")

        this.exitLoc = Location(bukkitWorld, x, y, z)
    }

    override fun setExitServer(server: String) {
        this.exitServer = server
    }

    /**
     * Declare that [player] inside lobby has voted for a map.
     *
     * @param player is who decided to vote.
     * @param vote How many points are counted for this vote? (1 by default)
     * @param mapName The map name. You can obtain map instances via Game.getMapList()
     * @return whether or not the player can vote now.
     * @throws MapNotFound is thrown if mapName doesn't indicate any existing map
     */
    fun voteMap(player: Player, vote: Int = 1, mapName: String): Boolean {
        if (game.editMode || game.phase != Game.Phase.LOBBY || voted.contains(player.uniqueId))
            return false

        if (!Game.getMapNames(game.name, false).contains(mapName))
            throw MapNotFound("Map $mapName does not exist.")

        votes[mapName] = votes[mapName]?.plus(vote) ?: 1
        voted.add(player.uniqueId)
        return true
    }

    internal fun teleportSpawn(player: Player) {
        val world = game.resource.lobbyMap.world
                ?: throw MapNotFound("Lobby world is not loaded!")

        if (loc != null) {
            player.teleport(Location(world, loc!!.x, loc!!.y, loc!!.z, loc!!.yaw, loc!!.pitch))
        } else {
            player.teleport(world.spawnLocation)
            player.sendMessage(notFound)
        }
    }

    internal fun terminate() {
        serviceTask?.cancel()
    }

    internal fun start() {
        val world = game.resource.lobbyMap.world
                ?: throw MapNotFound("Lobby world is not loaded!")

        world.pvp = false
        world.difficulty = Difficulty.PEACEFUL
        this.minimum = Module.getGameModule(game).minPlayer

        startTimer()
        serviceTask!!.runTaskTimer(Main.instance, 0L, 20L)
    }

    private fun startTimer() {
        serviceTask = object : BukkitRunnable() {
            override fun run() {
                val count = Module.getPlayerModule(game).getLivingPlayers().size

                if (count == 0) {
                    game.forceStop(error = false)
                    this.cancel()
                    return
                }

                if (!ticking) {
                    if (count < minimum) {
                        return
                    } else {
                        ticking = true
                    }
                }

                if (--timer <= 0) {
                    val playerCount = Module.getPlayerModule(game).getLivingPlayers().size
                    val minimum = Module.getGameModule(game).minPlayer
                    val voteList = LinkedList(votes.entries)

                    if (playerCount < minimum) {
                        Module.getGameModule(game).broadcast("&eNot enough players to start! Waiting for more...")
                    } else {
                        Collections.sort(voteList, Comparator { o1, o2 ->
                            val comp = (o1.value - o2.value) * -1

                            return@Comparator if (comp != 0) {
                                comp
                            } else {
                                o1.key.compareTo(o2.key)
                            }
                        })

                        try {
                            val entry = voteList.firstOrNull()

                            if (entry != null) {
                                // This entry has received top votes.
                                game.start(entry.key, entry.value)
                            } else {
                                // No one has voted.
                                game.start(null)
                            }
                        } catch (e: Exception) {
                            game.forceStop(async = false, error = true)
                            e.printStackTrace()
                        }
                    }

                    timer = constTimer
                    ticking = false
                    this.cancel()
                    return
                }

                val valid = arrayOf(30, 20, 10, 5, 4, 3, 2, 1)

                if (timer.toInt() % 60 == 0 || timer < 60 && valid.contains(timer.toInt())) {
                    val format = Timer(TimeUnit.SECOND, timer).format(true)

                    Module.getGameModule(game).broadcast("&6Game starts in $format.")
                }
            }
        }
    }

}