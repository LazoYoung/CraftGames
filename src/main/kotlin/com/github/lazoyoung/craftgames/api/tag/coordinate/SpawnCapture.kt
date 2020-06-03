package com.github.lazoyoung.craftgames.api.tag.coordinate

import org.bukkit.Location
import org.bukkit.World

interface SpawnCapture {

    fun toLocation(world: World): Location

}