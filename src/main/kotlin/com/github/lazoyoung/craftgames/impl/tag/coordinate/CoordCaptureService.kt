package com.github.lazoyoung.craftgames.impl.tag.coordinate

import com.github.lazoyoung.craftgames.api.Timer
import com.github.lazoyoung.craftgames.api.event.GameEditorSaveEvent
import com.github.lazoyoung.craftgames.api.tag.coordinate.CoordCapture
import com.github.lazoyoung.craftgames.api.tag.coordinate.CoordTag
import com.github.lazoyoung.craftgames.impl.Main
import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.world.WorldSaveEvent
import org.bukkit.event.world.WorldUnloadEvent
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import java.util.concurrent.CompletableFuture

abstract class CoordCaptureService(
        override val mapID: String,
        override val index: Int?
) : CoordCapture {
    private var displayTask: BukkitTask? = null

    override fun teleport(player: Player, callback: Runnable) {
        val world = player.world
        val maxAttempt = Main.getConfig()?.getInt("optimization.safezone-calculation.player-throttle", 10) ?: 10
        val future = when (this) {
            is AreaCaptureService -> this.toLocation(player.world, maxAttempt)
            is BlockCaptureService -> CompletableFuture.completedFuture(this.toLocation(world))
            is SpawnCaptureService -> CompletableFuture.completedFuture(this.toLocation(world))
            else -> null
        }

        if (future != null) {
            future.thenAcceptAsync {
                Bukkit.getScheduler().runTask(Main.instance, Runnable {
                    player.teleport(it)
                    callback.run()
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
        check(displayTask == null) {
            "Display is in progress."
        }

        val worldName = world.name
        val listener = object : Listener {
            @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
            fun onWorldUnload(event: WorldUnloadEvent) {
                if (worldName == event.world.name) {
                    terminateDisplay(world, this)
                }
            }

            @EventHandler(priority = EventPriority.HIGH)
            fun onGameSave(event: GameEditorSaveEvent) {
                if (worldName == event.getGame().map.worldName) {
                    terminateDisplay(world, this)
                }
            }

            @EventHandler(priority = EventPriority.HIGH)
            fun onWorldSave(event: WorldSaveEvent) {
                if (worldName == event.world.name) {
                    terminateDisplay(world, this)
                }
            }
        }

        generateBorder(world)
        Bukkit.getPluginManager().registerEvents(listener, Main.instance)

        displayTask = object : BukkitRunnable() {
            override fun run() {
                terminateDisplay(world, listener)
            }

            override fun cancel() {
                super.cancel()
                terminateDisplay(world, listener)
            }
        }.runTaskLater(Main.instance, duration.toTick())
    }

    protected abstract fun generateBorder(world: World)

    protected abstract fun destroyBorder(world: World)

    protected abstract fun serialize(): String

    private fun terminateDisplay(world: World, listener: Listener) {
        destroyBorder(world)
        HandlerList.unregisterAll(listener)
        displayTask = null
    }
}