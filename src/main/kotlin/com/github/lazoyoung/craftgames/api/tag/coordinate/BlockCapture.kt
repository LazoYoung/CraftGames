package com.github.lazoyoung.craftgames.api.tag.coordinate

import com.github.lazoyoung.craftgames.api.Timer
import com.github.lazoyoung.craftgames.impl.Main
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.block.Block

class BlockCapture(
        val x: Int,
        val y: Int,
        val z: Int,
        mapID: String,
        index: Int? = null
) : CoordCapture(mapID, index) {

    override fun displayBorder(world: World, duration: Timer) {
        super.displayBorder(world, duration)

        val block = getBlock(world)
        val blockData = block.blockData.clone()
        val loc = block.location.add(0.5, 0.1, 0.5)
        val configPath = "rendering.capture-display.falling-block.material-replacement.".plus(block.type.name)
        val material = Material.getMaterial(Main.getConfig()?.getString(configPath) ?: "")
        val fallingBlock = if (material != null) {
            world.spawnFallingBlock(loc, material.createBlockData())
        } else {
            world.spawnFallingBlock(loc, blockData)
        }

        block.type = Material.AIR
        fallingBlock.isInvulnerable = true
        fallingBlock.isGlowing = true
        fallingBlock.dropItem = false
        fallingBlock.setHurtEntities(false)
        fallingBlock.setGravity(false)

        Bukkit.getScheduler().runTaskLater(Main.instance, Runnable {
            fallingBlock.remove()
            block.blockData = blockData
        }, duration.toTick())
    }

    override fun serialize() : String {
        val builder = StringBuilder()

        for (e in arrayOf(x, y, z)) {
            builder.append(e.toBigDecimal()).append(",")
        }

        return builder.removeSuffix(",").toString()
    }

    fun toLocation(world: World): Location {
        return Location(world, x + 0.5, y + 0.5, z + 0.5)
    }

    fun getBlock(world: World): Block {
        return world.getBlockAt(x, y, z)
    }

}