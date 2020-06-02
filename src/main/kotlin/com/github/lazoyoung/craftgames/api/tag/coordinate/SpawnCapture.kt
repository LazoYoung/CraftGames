package com.github.lazoyoung.craftgames.api.tag.coordinate

import com.github.lazoyoung.craftgames.api.Timer
import com.github.lazoyoung.craftgames.impl.Main
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.EntityType
import org.bukkit.inventory.EquipmentSlot
import java.math.RoundingMode

class SpawnCapture(
        val x: Double,
        val y: Double,
        val z: Double,
        private val yaw: Float,
        private val pitch: Float,
        mapID: String,
        index: Int? = null
) : CoordCapture(mapID, index) {

    override fun displayBorder(world: World, duration: Timer) {
        super.displayBorder(world, duration)

        val armorStand = world.spawnEntity(toLocation(world), EntityType.ARMOR_STAND) as ArmorStand
        armorStand.isMarker = true
        armorStand.removeWhenFarAway = false
        armorStand.isGlowing = true
        armorStand.setDisabledSlots(*EquipmentSlot.values())
        armorStand.setArms(true)
        armorStand.setBasePlate(false)
        armorStand.setGravity(false)

        Bukkit.getScheduler().runTaskLater(Main.instance, Runnable {
            armorStand.remove()
        }, duration.toTick())
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

    fun toLocation(world: World): Location {
        return Location(world, x, y, z, yaw, pitch)
    }
}