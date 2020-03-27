package com.github.lazoyoung.craftgames.module.api

import com.github.lazoyoung.craftgames.util.Timer
import org.bukkit.GameMode

interface GameModule {

    fun getGameTimer(): Timer

    fun setGameTimer(timer: Timer)

    fun setPlayerCapacity(min: Int, max: Int)

    fun setCanJoinAfterStart(boolean: Boolean)

    fun setDefaultGameMode(mode: GameMode)

    /**
     * Broadcast [message] to everyone in this game.
     */
    fun broadcast(message: String)

}