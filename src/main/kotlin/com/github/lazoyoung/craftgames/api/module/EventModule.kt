package com.github.lazoyoung.craftgames.api.module

import com.github.lazoyoung.craftgames.api.event.*
import java.util.function.Consumer

interface EventModule {
    /**
     * @see [GameInitEvent]
     */
    fun onGameInit(callback: Consumer<GameInitEvent>)
    /**
     * @see [GameStartEvent]
     */
    fun onGameStart(callback: Consumer<GameStartEvent>)
    /**
     * @see [GameJoinEvent]
     */
    fun onGameJoin(callback: Consumer<GameJoinEvent>)
    /**
     * @see [GameJoinPostEvent]
     */
    fun afterGameJoin(callback: Consumer<GameJoinPostEvent>)
    /**
     * @see [GameLeaveEvent]
     */
    fun onGameLeave(callback: Consumer<GameLeaveEvent>)
    /**
     * @see [GameTimeoutEvent]
     */
    fun onGameTimeout(callback: Consumer<GameTimeoutEvent>)
    /**
     * @see [GameFinishEvent]
     */
    fun onGameFinish(callback: Consumer<GameFinishEvent>)
    /**
     * @see [GameAreaEnterEvent]
     */
    fun onAreaEnter(callback: Consumer<GameAreaEnterEvent>)
    /**
     * @see [GameAreaExitEvent]
     */
    fun onAreaExit(callback: Consumer<GameAreaExitEvent>)
    /**
     * @see [GamePlayerKillEvent]
     */
    fun onPlayerKill(callback: Consumer<GamePlayerKillEvent>)
    /**
     * @see [GamePlayerDeathEvent]
     */
    fun onPlayerDeath(callback: Consumer<GamePlayerDeathEvent>)
    /**
     * @see [GamePlayerInteractEvent]
     */
    fun onPlayerInteract(callback: Consumer<GamePlayerInteractEvent>)
    /**
     * @see [GameEntityDamageEvent]
     */
    fun onEntityDamage(callback: Consumer<GameEntityDamageEvent>)
}