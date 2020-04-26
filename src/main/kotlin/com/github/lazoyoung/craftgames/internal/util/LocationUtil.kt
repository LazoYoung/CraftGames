package com.github.lazoyoung.craftgames.internal.util

import com.github.lazoyoung.craftgames.Main
import org.bukkit.Bukkit
import org.bukkit.Location

class LocationUtil {

    companion object {
        fun getExitFallback(currentWorld: String): Location {
            val fallback = Main.getConfig()?.getConfigurationSection("exit-policy.fallback")
            val fWorld = fallback?.getString("world")
            var world = if (fWorld != null) {
                Bukkit.getWorld(fWorld)
            } else {
                null
            }
            val loc = if (world != null && fallback != null) {
                val fX = fallback.getDouble("x")
                val fY = fallback.getDouble("y")
                val fZ = fallback.getDouble("z")
                val fYaw = fallback.getDouble("yaw").toFloat()
                val fPitch = fallback.getDouble("pitch").toFloat()

                Location(world, fX, fY, fZ, fYaw, fPitch)
            } else {
                world = Bukkit.getWorlds().filter { it.name != currentWorld }.random()
                world.spawnLocation
            }

            if (!loc.block.isPassable || !loc.add(0.0, 1.0, 0.0).block.isPassable) {
                loc.y = world!!.getHighestBlockYAt(loc).toDouble()
            }

            return loc
        }
    }
}