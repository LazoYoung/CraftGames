package com.github.lazoyoung.craftgames.module.service

import com.github.lazoyoung.craftgames.Main
import com.github.lazoyoung.craftgames.coordtag.CoordTag
import com.github.lazoyoung.craftgames.coordtag.SpawnCapture
import com.github.lazoyoung.craftgames.exception.MapNotFound
import com.github.lazoyoung.craftgames.game.Game
import com.github.lazoyoung.craftgames.module.Module
import com.github.lazoyoung.craftgames.module.api.LobbyModule
import com.github.lazoyoung.craftgames.util.Timer
import net.md_5.bungee.api.ChatColor
import net.md_5.bungee.api.chat.ComponentBuilder
import net.md_5.bungee.api.chat.TextComponent
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitRunnable
import java.util.*
import kotlin.collections.HashMap


class LobbyModuleService(val game: Game) : LobbyModule {

    private var tag: CoordTag? = null
    private var timer = Timer(Timer.Unit.SECOND, 30)
    private val voted = ArrayList<UUID>()
    private val votes = HashMap<String, Int>()
    private val notFound = ComponentBuilder("Unable to locate lobby position!")
            .color(ChatColor.RED).create().first() as TextComponent
    private var serviceTask: BukkitRunnable? = null

    override fun setSpawn(spawnTag: String) {
        tag = Module.getSpawnTag(game, spawnTag)
    }

    override fun setTimer(timer: Timer) {
        this.timer = timer
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
        if (game.phase != Game.Phase.LOBBY)
            return false

        if (voted.contains(player.uniqueId))
            return false

        if (!Game.getMapNames(game.name, false).contains(mapName))
            throw MapNotFound("Map $mapName does not exist.")

        votes[mapName] = votes[mapName]?.plus(vote) ?: 1
        voted.add(player.uniqueId)
        return true
    }

    internal fun teleport(player: Player) {
        val world = game.map.world!!
        val c = tag?.getLocalCaptures()?.random() as SpawnCapture?

        if (c != null) {
            player.teleport(Location(world, c.x, c.y, c.z, c.yaw, c.pitch))
        } else {
            player.teleport(world.spawnLocation)
            player.sendMessage(notFound)
        }
    }

    internal fun startService() {
        val plugin = Main.instance
        var sec = this.timer.toTick().toInt() / 20

        serviceTask = object : BukkitRunnable() {
            override fun run() {
                if (--sec <= 0) {
                    val list = LinkedList(votes.entries)

                    Collections.sort(list, Comparator { o1, o2 ->
                        val comp = (o1.value - o2.value) * -1

                        return@Comparator if (comp != 0) {
                            comp
                        } else {
                            o1.key.compareTo(o2.key)
                        }
                    })

                    try {
                        val entry = list.firstOrNull()

                        if (entry != null) {
                            // This entry has received top votes.
                            game.start(entry.key, entry.value)
                        } else {
                            // No one has voted.
                            game.start(null)
                        }
                    } catch (e: MapNotFound) {
                        game.forceStop(async = false, error = true)
                        e.printStackTrace()
                    }
                    this.cancel()
                    return
                }

                val valid = arrayOf(30, 20, 10, 5, 4, 3, 2, 1)

                if (sec % 60 == 0 || sec < 60 && valid.contains(sec)) {
                    val format = Timer(Timer.Unit.SECOND, sec.toLong()).format(true)

                    // TODO Player UI Module: Replace with dedicated function
                    game.getPlayers().forEach {
                        it.sendMessage("Game starts in $format.")
                    }
                }
            }
        }

        serviceTask!!.runTaskTimer(plugin, 0L, 20L)
    }

    internal fun endService() {
        serviceTask?.cancel()
        voted.clear()
        votes.clear()
    }

}