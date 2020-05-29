package com.github.lazoyoung.craftgames.api.tag.coordinate

import com.github.lazoyoung.craftgames.impl.Main
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.concurrent.CompletableFuture

abstract class CoordCapture(
        val mapID: String?,
        val index: Int?
) {

    fun teleport(player: Player): CompletableFuture<Void> {
        val world = player.world
        val maxAttempt = Main.getConfig()?.getInt("optimization.safezone-calculation.player-throttle", 10) ?: 10
        val future = when (this) {
            is AreaCapture -> this.toLocation(player.world, maxAttempt)
            is BlockCapture -> CompletableFuture.completedFuture(this.toLocation(world))
            is SpawnCapture -> CompletableFuture.completedFuture(this.toLocation(world))
            else -> null
        }

        return if (future == null) {
            val failure = CompletableFuture<Void>()
            failure.completeExceptionally(IllegalStateException("This CoordCapture has unknown type."))
            failure
        } else {
            future.thenAcceptAsync {
                Bukkit.getScheduler().runTask(Main.instance, Runnable {
                    player.teleport(it)
                })
            }
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

    protected abstract fun serialize(): String
}