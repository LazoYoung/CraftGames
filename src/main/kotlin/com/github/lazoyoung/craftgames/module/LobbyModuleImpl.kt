package com.github.lazoyoung.craftgames.module

import com.github.lazoyoung.craftgames.Main
import com.github.lazoyoung.craftgames.coordtag.CoordTag
import com.github.lazoyoung.craftgames.coordtag.SpawnCapture
import com.github.lazoyoung.craftgames.exception.MapNotFound
import com.github.lazoyoung.craftgames.game.Game
import net.md_5.bungee.api.ChatColor
import net.md_5.bungee.api.chat.ComponentBuilder
import net.md_5.bungee.api.chat.TextComponent
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitRunnable
import java.util.*
import kotlin.collections.HashMap


class LobbyModuleImpl(val game: Game) : LobbyModule {

    private val voted = ArrayList<UUID>()
    private val votes = HashMap<String, Int>()
    private var tag: CoordTag? = null
    private var timer = Module.getTimer(TimerUnit.SECOND, 30)
    private val notFound = ComponentBuilder("Unable to locate lobby position!")
            .color(ChatColor.RED).create().first() as TextComponent

    override fun setSpawn(spawnTag: String) {
        tag = Module.getSpawnTag(game, spawnTag)
    }

    override fun setTimer(unit: TimerUnit, value: Int) {
        timer = Module.getTimer(unit, value)
    }

    /**
     * Vote a map.
     *
     * @param player is who decided to vote.
     * @param vote How many points are counted for this vote? (1 by default)
     * @param mapName The map name. You can obtain map instances via Game.getMapList()
     */
    fun voteMap(player: Player, vote: Int = 1, mapName: String): Boolean {
        if (voted.contains(player.uniqueId))
            return false

        votes[mapName] = votes[mapName]?.plus(vote) ?: 1
        return true
    }

    internal fun spawn(player: Player) {
        val world = this.game.map.world!!
        val c = this.tag?.getLocalCaptures()?.random() as SpawnCapture?

        if (c != null) {
            player.teleport(Location(world, c.x, c.y, c.z, c.yaw, c.pitch))
        } else {
            player.teleport(world.spawnLocation)
            player.sendMessage(notFound)
        }
    }

    internal fun startTimer() {
        val plugin = Main.instance
        this.timer /= 20

        val task = object : BukkitRunnable() {
            override fun run() {
                if (--timer <= 0) {
                    // TODO Start game with the map that is top voted.
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
                        game.stop(async = false, error = true)
                        e.printStackTrace()
                    }
                    this.cancel()
                    return
                }

                val time = if (timer > 60) {
                    if (timer % 60 == 0) {
                        "$timer minutes."
                    } else {
                        return
                    }
                } else when (timer) {
                    60 -> "$timer minute."
                    30, 20, 10 -> "$timer seconds."
                    5, 4, 3, 2 -> "$timer seconds!"
                    1 -> "$timer second!"
                    else -> return
                }

                game.getPlayers().forEach {
                    it.sendMessage("The game starts in $time")
                }
            }
        }

        task.runTaskTimer(plugin, 0L, 20L)
        game.module.tasks["lobby"] = task
    }

    internal fun stopTimer() {
        game.module.tasks["lobby"]?.cancel()
    }

}