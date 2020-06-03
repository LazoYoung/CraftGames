package com.github.lazoyoung.craftgames.impl.tag.coordinate

import com.github.lazoyoung.craftgames.api.tag.coordinate.SpawnCapture
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Entity
import org.bukkit.entity.EntityType
import org.bukkit.inventory.EquipmentSlot
import java.math.RoundingMode

class SpawnCaptureService(
        val x: Double,
        val y: Double,
        val z: Double,
        private val yaw: Float,
        private val pitch: Float,
        mapID: String,
        index: Int? = null
) : SpawnCapture, CoordCaptureService(mapID, index) {

    override fun generateBorder(world: World): Entity {
        val armorStand = world.spawnEntity(toLocation(world), EntityType.ARMOR_STAND) as ArmorStand
        armorStand.isCustomNameVisible = true
        armorStand.customName = index?.toString() ?: "<New Capture>"
        armorStand.removeWhenFarAway = false
        armorStand.isGlowing = true
        armorStand.isCollidable = false
        armorStand.isInvulnerable = true
        armorStand.setDisabledSlots(*EquipmentSlot.values())
        armorStand.setArms(true)
        armorStand.setBasePlate(false)
        armorStand.setGravity(false)
        armorStand.setCanMove(false)
        return armorStand
    }

    override fun destroyBorder(entity: Entity) {
        entity.remove()
    }

    override fun serialize() : String {
        val r = RoundingMode.HALF_UP
        val x = x.toBigDecimal().setScale(1, r)
        val y = y.toBigDecimal().setScale(1, r)
        val z = z.toBigDecimal().setScale(1, r)
        val yaw = this.yaw.toBigDecimal().setScale(1, r)
        val pitch = this.pitch.toBigDecimal().setScale(1, r)
        val builder = StringBuilder()

        for (e in arrayOf(x, y, z, yaw, pitch)) {
            builder.append(e).append(",")
        }

        return builder.removeSuffix(",").toString()
    }

    override fun toLocation(world: World): Location {
        return Location(world, x, y, z, yaw, pitch)
    }
}