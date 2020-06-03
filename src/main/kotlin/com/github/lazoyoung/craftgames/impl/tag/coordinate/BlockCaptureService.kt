package com.github.lazoyoung.craftgames.impl.tag.coordinate

import com.github.lazoyoung.craftgames.api.tag.coordinate.BlockCapture
import com.github.lazoyoung.craftgames.impl.Main
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.block.data.BlockData
import org.bukkit.entity.FallingBlock
import java.util.*

class BlockCaptureService(
        val x: Int,
        val y: Int,
        val z: Int,
        mapID: String,
        index: Int? = null
) : BlockCapture, CoordCaptureService(mapID, index) {

    private var entityUUID: UUID? = null
    private var blockData: BlockData? = null

    override fun generateBorder(world: World) {
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

        fallingBlock.isInvulnerable = true
        fallingBlock.isGlowing = true
        fallingBlock.dropItem = false
        fallingBlock.setHurtEntities(false)
        fallingBlock.setGravity(false)
        block.type = Material.AIR
        this.entityUUID = fallingBlock.uniqueId
        this.blockData = blockData
    }

    override fun destroyBorder(world: World) {
        if (entityUUID == null || blockData == null) {
            return
        }

        val fallingBlock = world.getEntity(entityUUID!!) as? FallingBlock
                ?: return
        val block = fallingBlock.location.toBlockLocation().block

        fallingBlock.remove()
        block.blockData = blockData!!
        entityUUID = null
        blockData = null
    }

    override fun serialize() : String {
        val builder = StringBuilder()

        for (e in arrayOf(x, y, z)) {
            builder.append(e.toBigDecimal()).append(",")
        }

        return builder.removeSuffix(",").toString()
    }

    override fun toLocation(world: World): Location {
        return Location(world, x + 0.5, y + 0.5, z + 0.5)
    }

    override fun getBlock(world: World): Block {
        return world.getBlockAt(x, y, z)
    }

}