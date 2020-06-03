package com.github.lazoyoung.craftgames.api.tag.coordinate

import org.bukkit.Location
import org.bukkit.World
import org.bukkit.block.Block

interface BlockCapture {

    fun toLocation(world: World): Location

    fun getBlock(world: World): Block

}