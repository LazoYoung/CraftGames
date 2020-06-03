package com.github.lazoyoung.craftgames.api.tag.coordinate

import org.bukkit.entity.Player

interface CoordCapture {

    val mapID: String?
    val index: Int?

    fun teleport(player: Player, callback: Runnable)

}