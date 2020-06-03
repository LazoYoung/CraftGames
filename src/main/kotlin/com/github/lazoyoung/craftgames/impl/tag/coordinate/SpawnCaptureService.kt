package com.github.lazoyoung.craftgames.impl.tag.coordinate

import com.github.lazoyoung.craftgames.api.tag.coordinate.SpawnCapture
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.EntityType
import org.bukkit.inventory.EquipmentSlot
import java.math.RoundingMode
import java.util.*

class SpawnCaptureService(
        val x: Double,
        val y: Double,
        val z: Double,
        private val yaw: Float,
        private val pitch: Float,
        mapID: String,
        index: Int? = null
) : SpawnCapture, CoordCaptureService(mapID, index) {

    var entityUUID: UUID? = null

    override fun generateBorder(world: World) {
        val armorStand = world.spawnEntity(toLocation(world), EntityType.ARMOR_STAND) as ArmorStand
        armorStand.isMarker = true
        armorStand.removeWhenFarAway = false
        armorStand.isGlowing = true
        armorStand.setDisabledSlots(*EquipmentSlot.values())
        armorStand.setArms(true)
        armorStand.setBasePlate(false)
        armorStand.setGravity(false)
        this.entityUUID = armorStand.uniqueId
    }

    override fun destroyBorder(world: World) {
        if (entityUUID == null) {
            return
        }

        val armorStand = world.getEntity(entityUUID!!) as? ArmorStand
        armorStand?.remove()
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