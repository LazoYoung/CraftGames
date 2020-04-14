package com.github.lazoyoung.craftgames.coordtag.capture

import com.github.lazoyoung.craftgames.Main
import com.github.lazoyoung.craftgames.api.Timer
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitRunnable
import kotlin.random.Random
import kotlin.random.nextInt

class AreaCapture(
        val x1: Int,
        val x2: Int,
        val y1: Int,
        val y2: Int,
        val z1: Int,
        val z2: Int,
        mapID: String,
        index: Int? = null
) : CoordCapture(mapID, index) {

    override fun serialize(): String {
        val builder = StringBuilder()
        val x = intArrayOf(x1, x2)
        val y = intArrayOf(y1, y2)
        val z = intArrayOf(z1, z2)
        x.sort(); y.sort(); z.sort()

        for (e in intArrayOf(*x, *y, *z)) {
            builder.append(e.toBigDecimal()).append(",")
        }

        return builder.removeSuffix(",").toString()
    }

    override fun toLocation(world: World): Location {
        val x = Random.nextInt(x1..x2)
        val y = Random.nextInt(y1..y2)
        val z = Random.nextInt(z1..z2)

        return Location(world, x.toDouble(), y.toDouble(), z.toDouble())
    }

    override fun teleport(player: Player) {}

    /**
     * Spawn particles inside [world] to depict the border of this area.
     *
     * @param timer How long will particles shows up?
     */
    fun displayBorder(world: World, res: Int, timer: Timer) {
        val scheduler = Bukkit.getScheduler()
        val plugin = Main.instance
        val builder = Particle.REDSTONE.builder()
                .count(1).offset(0.0, 0.0, 0.0)

        object : BukkitRunnable() {
            var counter = timer.toTick() / 5L

            override fun run() {
                if (counter-- < 1) {
                    this.cancel()
                    return
                }

                builder.color(Random.nextInt(256), Random.nextInt(256), Random.nextInt(256))

                // Generate particles
                scheduler.runTask(plugin, Runnable {
                    for (x in x1*res..x2*res) {
                        arrayOf(
                                intArrayOf(y1, z1), intArrayOf(y1, z2),
                                intArrayOf(y2, z1), intArrayOf(y2, z2)
                        ).forEach {
                            builder.location(
                                    world, x.toDouble() / res + 0.5,
                                    it[0].toDouble() + 0.5, it[1].toDouble() + 0.5
                            ).spawn()
                        }
                    }

                    scheduler.runTask(plugin, Runnable {
                        for (y in y1*res..y2*res) {
                            arrayOf(
                                    intArrayOf(x1, z1), intArrayOf(x1, z2),
                                    intArrayOf(x2, z1), intArrayOf(x2, z2)
                            ).forEach {
                                builder.location(
                                        world, it[0].toDouble() + 0.5,
                                        y.toDouble() / res + 0.5, it[1].toDouble() + 0.5
                                ).spawn()
                            }
                        }

                        scheduler.runTask(plugin, Runnable {
                            for (z in z1*res..z2*res) {
                                arrayOf(
                                        intArrayOf(x1, y1), intArrayOf(x1, y2),
                                        intArrayOf(x2, y1), intArrayOf(x2, y2)
                                ).forEach {
                                    builder.location(
                                            world, it[0].toDouble() + 0.5,
                                            it[1].toDouble() + 0.5, z.toDouble() / res + 0.5
                                    ).spawn()
                                }
                            }
                        })
                    })
                })
            }

        }.runTaskTimer(plugin, 0L, 5L)
    }

    fun isInside(loc: Location): Boolean {
        val x = loc.blockX
        val y = loc.blockY
        val z = loc.blockZ
        val inX = x == x.coerceIn(x1, x2)
        val inY = y == y.coerceIn(y1, y2)
        val inZ = z == z.coerceIn(z1, z2)

        return inX && inY && inZ
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AreaCapture

        if (x1 != other.x1) return false
        if (x2 != other.x2) return false
        if (y1 != other.y1) return false
        if (y2 != other.y2) return false
        if (z1 != other.z1) return false
        if (z2 != other.z2) return false

        return true
    }

    override fun hashCode(): Int {
        var result = x1
        result = 31 * result + x2
        result = 31 * result + y1
        result = 31 * result + y2
        result = 31 * result + z1
        result = 31 * result + z2
        return result
    }

}