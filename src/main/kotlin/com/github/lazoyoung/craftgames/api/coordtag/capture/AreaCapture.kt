package com.github.lazoyoung.craftgames.api.coordtag.capture

import com.github.lazoyoung.craftgames.impl.Main
import com.github.lazoyoung.craftgames.api.TimeUnit
import com.github.lazoyoung.craftgames.api.Timer
import org.bukkit.*
import org.bukkit.block.Block
import org.bukkit.entity.Entity
import org.bukkit.scheduler.BukkitRunnable
import java.util.concurrent.CompletableFuture
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

    private var maxAttempt = 0

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

    fun toLocation(world: World, maxAttempt: Int, offsetY: Double = 2.0): CompletableFuture<Location> {
        this.maxAttempt = maxAttempt
        return toLocation(world, offsetY)
    }

    private fun toLocation(world: World, offsetY: Double): CompletableFuture<Location> {
        var x = Random.nextInt(x1..x2)
        var y = y2
        var z = Random.nextInt(z1..z2)
        var block: Block
        var pocket = 0
        var attempt = 1
        val ex = RuntimeException("Unable to find safe-zone. Aborting!")
        val future = CompletableFuture<Location>()
        val task = object : BukkitRunnable() {

            fun nextAttempt() {
                return if (++attempt > maxAttempt) {
                    future.completeExceptionally(ex)
                    this.cancel()
                } else {
                    x = Random.nextInt(x1..x2)
                    y = y2
                    z = Random.nextInt(z1..z2)
                }
            }

            override fun run() {
                loop@ while (true) {
                    block = world.getBlockAt(x, y, z)

                    when (block.type) {
                        Material.LAVA, Material.FIRE, Material.SWEET_BERRY_BUSH, Material.WITHER_ROSE -> {
                            nextAttempt()
                            break@loop
                        }
                        else -> {
                            if (block.isPassable) {
                                pocket++
                            } else if (pocket > 1) {
                                when (block.type) {
                                    Material.CACTUS, Material.MAGMA_BLOCK, Material.CAMPFIRE -> {
                                        nextAttempt()
                                        break@loop
                                    }
                                    else -> {
                                        future.complete(
                                                Location(world, x + 0.5, y + offsetY, z + 0.5)
                                        )
                                        this.cancel()
                                    }
                                }
                            }
                        }
                    }

                    if (--y < y1) {
                        nextAttempt()
                        break@loop
                    }
                }
            }

            override fun cancel() {
                super.cancel()
                future.completeExceptionally(
                        RuntimeException("Task is cancelled unexpectedly.")
                )
            }
        }

        task.runTaskTimer(Main.instance, 0L, 1L)
        return future
    }

    /**
     * Spawn particles inside [world] to depict the border of this area.
     *
     * @param timer How long will particles shows up?
     */
    fun displayBorder(world: World, timer: Timer) {
        val scheduler = Bukkit.getScheduler()
        val plugin = Main.instance
        val res = Main.getConfig()?.getInt("optimization.particle-resolution", 2) ?: 2
        val distance = Main.getConfig()?.getInt("optimization.particle-render-distance", 30) ?: 30
        val interval = Timer(TimeUnit.SECOND, 2)
        val builder = Particle.END_ROD.builder()
                .count(1)
                .offset(0.0, 0.0, 0.0)
                .extra(0.0)
                .force(false)

        object : BukkitRunnable() {
            var counter = timer.toTick() / interval.toTick()

            override fun run() {
                if (counter-- < 1) {
                    this.cancel()
                    return
                }

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
                            ).receivers(distance).spawn()
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
                                ).receivers(distance).spawn()
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
                                    ).receivers(distance).spawn()
                                }
                            }
                        })
                    })
                })
            }

        }.runTaskTimer(plugin, 0L, interval.toTick())
    }

    fun isInside(entity: Entity): Boolean {
        return isInside(entity.location)
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