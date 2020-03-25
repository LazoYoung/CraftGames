package com.github.lazoyoung.craftgames.module.api

import com.github.lazoyoung.craftgames.util.Timer
import org.bukkit.GameMode

interface GameModule {

    companion object {
        const val PERSONAL = 0
        const val EDITOR = 1
        const val SPECTATOR = 2
    }

    fun getGameTimer(): Timer

    fun setGameTimer(timer: Timer)

    fun setRespawnTimer(timer: Timer)

    fun setPlayerSpawn(type: Int, spawnTag: String)

    fun setDefaultGameMode(mode: GameMode)

    fun broadcast(message: String)

}