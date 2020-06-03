package com.github.lazoyoung.craftgames.api.tag.coordinate

import com.github.lazoyoung.craftgames.api.Timer
import com.github.lazoyoung.craftgames.impl.Main
import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.entity.Player
import java.util.concurrent.CompletableFuture

abstract class CoordCapture(
        val mapID: String?,
        val index: Int?
) {
    private var display: Boolean = false

    fun teleport(player: Player): CompletableFuture<Void> {
        val world = player.world
        val maxAttempt = Main.getConfig()?.getInt("optimization.safezone-calculation.player-throttle", 10) ?: 10
        val future = when (this) {
            is AreaCapture -> this.toLocation(player.world, maxAttempt)
            is BlockCapture -> CompletableFuture.completedFuture(this.toLocation(world))
            is SpawnCapture -> CompletableFuture.completedFuture(this.toLocation(world))
            else -> null
        }

        return if (future != null) {
            future.thenAcceptAsync {
                Bukkit.getScheduler().runTask(Main.instance, Runnable {
                    player.teleport(it)
                })
            }
        } else {
            error("This CoordCapture is not identifiable.")
        }
    }

    /**
     * Add this capture into the tag. You will have to manually save the config.
     *
     * @param tag a tag for which this capture is saved.
     * @throws RuntimeException Thrown if mapID is undefined for this instance.
     */
    internal fun add(tag: CoordTag) {
        checkNotNull(mapID) {
            "This CoordCapture is not assigned to any map."
        }

        try {
            val registry = tag.registry
            val key = registry.getCoordCaptureStreamKey(tag.name, mapID)
            val stream = registry.getCoordCaptureStream(tag.name, mapID)
                    .toMutableList()

            stream.add(serialize())
            registry.ctagConfig.set(key, stream)
            registry.reloadCoordTags(tag)
        } catch (e: NullPointerException) {
            throw IllegalArgumentException(e)
        }
    }

    /**
     * Display border of this capture.
     *
     * @param world The world where this capture belongs to.
     * @param duration Duration of display.
     * @throws IllegalStateException is raised if display is in progress.
     */
    open fun displayBorder(world: World, duration: Timer) {
        check(!display) {
            "Display is in progress."
        }

        display = true

        Bukkit.getScheduler().runTaskLater(Main.instance, Runnable {
            display = false
        }, duration.toTick())
    }

    protected abstract fun serialize(): String
}