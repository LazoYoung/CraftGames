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
    private var timer = Timer(Timer.Unit.SECOND, 30)
    private val notFound = ComponentBuilder("Unable to locate lobby position!")
            .color(ChatColor.RED).create().first() as TextComponent

    override fun setSpawn(spawnTag: String) {
        tag = Module.getSpawnTag(game, spawnTag)
    }

    override fun setTimer(timer: Timer) {
        this.timer = timer
    }

    override fun voteMap(player: Player, vote: Int, mapName: String): Boolean {
        if (voted.contains(player.uniqueId))
            return false

        if (!Game.getMapNames(game.name, false).contains(mapName))
            throw MapNotFound("Map $mapName does not exist.")

        votes[mapName] = votes[mapName]?.plus(vote) ?: 1
        voted.add(player.uniqueId)
        return true
    }

    internal fun teleport(player: Player) {
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
        var sec = this.timer.toTick().toInt() / 20

        val task = object : BukkitRunnable() {
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
                        game.stop(async = false, error = true)
                        e.printStackTrace()
                    }
                    this.cancel()
                    return
                }

                val format = if (sec > 60) {
                    if (sec % 60 == 0) {
                        "${sec / 60} minutes."
                    } else {
                        return
                    }
                } else when (sec) {
                    60 -> "1 minute."
                    30, 20, 10 -> "$sec seconds."
                    5, 4, 3, 2 -> "$sec seconds!"
                    1 -> "$sec second!"
                    else -> return
                }

                game.getPlayers().forEach {
                    it.sendMessage("The game starts in $format")
                }
            }
        }

        task.runTaskTimer(plugin, 0L, 20L)
        game.module.tasks["lobby"] = task
    }

    internal fun reset() {
        game.module.tasks["lobby"]?.cancel()
        voted.clear()
        votes.clear()
    }

}