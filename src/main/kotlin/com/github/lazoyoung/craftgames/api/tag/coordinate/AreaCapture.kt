package com.github.lazoyoung.craftgames.api.tag.coordinate

import org.bukkit.Location
import org.bukkit.World
import java.util.function.Consumer

interface AreaCapture : CoordCapture {

    fun toLocation(world: World, maxAttempt: Int, offsetY: Double, callback: Consumer<Location>)

}