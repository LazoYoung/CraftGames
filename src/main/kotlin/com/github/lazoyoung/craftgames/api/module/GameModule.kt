package com.github.lazoyoung.craftgames.api.module

import com.github.lazoyoung.craftgames.api.Timer
import org.bukkit.GameMode
import org.bukkit.GameRule
import org.bukkit.entity.Player
import org.bukkit.scoreboard.Team

interface GameModule {

    fun getTimer(): Timer

    fun setTimer(timer: Timer)

    fun setPlayerCapacity(min: Int, max: Int)

    fun setCanJoinAfterStart(boolean: Boolean)

    fun setGameMode(mode: GameMode)

    fun <T> setGameRule(rule: GameRule<T>, value: T)

    fun setPVP(pvp: Boolean)

    /**
     * Broadcast [message] to everyone in this game.
     */
    fun broadcast(message: String)

    /**
     * Finish game and mark winner team.
     *
     * @param winner The winning team.
     * @param timer Amount of time to celebrate.
     */
    fun finishGame(winner: Team, timer: Timer)

    /**
     * Finish game and mark a winner.
     *
     * @param winner The winner.
     * @param timer Amount of time to celebrate.
     */
    fun finishGame(winner: Player, timer: Timer)

    /**
     * Finish game and mark it as draw.
     *
     * @param timer Amount of time to celebrate.
     */
    fun drawGame(timer: Timer)

}