package com.github.lazoyoung.craftgames.api

import com.github.lazoyoung.craftgames.event.*

enum class EventType (val clazz: Class<out GameEvent>) {
    /**
     * @see [GameInitEvent]
     */
    GAME_INIT_EVENT(GameInitEvent::class.java),

    /**
     * @see [GameStartEvent]
     */
    GAME_START_EVENT(GameStartEvent::class.java),

    /**
     * @see [GameJoinEvent]
     */
    GAME_JOIN_EVENT(GameJoinEvent::class.java),

    /**
     * @see [GameJoinPostEvent]
     */
    GAME_JOIN_POST_EVENT(GameJoinPostEvent::class.java),

    /**
     * @see [GameLeaveEvent]
     */
    GAME_LEAVE_EVENT(GameLeaveEvent::class.java),

    /**
     * @see [GameTimeoutEvent]
     */
    GAME_TIMEOUT_EVENT(GameTimeoutEvent::class.java),

    /**
     * @see [GameFinishEvent]
     */
    GAME_FINISH_EVENT(GameFinishEvent::class.java),

    /**
     * @see [GameAreaEnterEvent]
     */
    AREA_ENTER_EVENT(GameAreaEnterEvent::class.java),

    /**
     * @see [GameAreaExitEvent]
     */
    AREA_EXIT_EVENT(GameAreaExitEvent::class.java),

    /**
     * @see [GamePlayerKillEvent]
     */
    PLAYER_KILL_EVENT(GamePlayerKillEvent::class.java),

    /**
     * @see [GamePlayerDeathEvent]
     */
    PLAYER_DEATH_EVENT(GamePlayerDeathEvent::class.java),

    /**
     * @see [GamePlayerInteractEvent]
     */
    PLAYER_INTERACT_EVENT(GamePlayerInteractEvent::class.java);

    companion object {
        /**
         * Returns an [EventType] that matches with given [name].
         *
         * @throws IllegalArgumentException is thrown if nothing matches the name.
         */
        fun forName(name: String): EventType {
            values().forEach {
                if (it.name.equals(name, true))
                    return it
            }

            throw IllegalArgumentException("EventType $name does not exist.")
        }

        /**
         * Returns an [EventType] that matches with given [clazz][Class].
         *
         * @throws IllegalArgumentException is thrown if nothing matches the name.
         */
        fun <T : GameEvent> forClass(clazz: T): EventType {
            values().forEach {
                if (it.clazz == clazz) {
                    return it
                }
            }

            throw IllegalArgumentException("Unknown class: ${clazz::class.java.simpleName}")
        }
    }
}