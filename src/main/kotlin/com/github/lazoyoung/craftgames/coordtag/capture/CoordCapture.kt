package com.github.lazoyoung.craftgames.coordtag.capture

import com.github.lazoyoung.craftgames.Main
import com.github.lazoyoung.craftgames.coordtag.tag.CoordTag
import org.bukkit.Location
import org.bukkit.entity.Player
import java.util.concurrent.CompletableFuture

abstract class CoordCapture(
        val mapID: String?,
        val index: Int?
) {

    /**
     * Add this capture into the tag. You will have to manually save the config.
     *
     * @param tag a tag for which this capture is saved.
     * @throws RuntimeException Thrown if mapID is undefined for this instance.
     */
    fun add(tag: CoordTag) {
        try {
            val key = CoordTag.getKeyToCaptureStream(tag.name, mapID!!)
            val config = tag.registry.config
            val stream = config.getStringList(key)
            stream.add(serialize())
            config.set(key, stream)
            tag.registry.reload()
        } catch (e: NullPointerException) {
            throw IllegalArgumentException(e)
        }
    }

    fun teleport(player: Player): CompletableFuture<Location> {
        val world = player.world
        val maxAttempt = Main.getConfig()?.getInt("optimization.safezone-calculation.player-throttle", 10) ?: 10
        val future = when (this) {
            is AreaCapture -> this.toLocation(player.world, maxAttempt)
            is BlockCapture -> CompletableFuture.completedFuture(this.toLocation(world))
            is SpawnCapture -> CompletableFuture.completedFuture(this.toLocation(world))
            else -> error("Unknown tag mode.")
        }

        future.thenAccept { player.teleport(it) }
        return future
    }

    abstract fun serialize(): String
}