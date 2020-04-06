package com.github.lazoyoung.craftgames.api

import com.github.lazoyoung.craftgames.event.*

enum class EventType(vararg val alias: String) {
    /**
     * @see [GameInitEvent]
     */
    GAME_INIT_EVENT("GameInitEvent"),

    /**
     * @see [GameStartEvent]
     */
    GAME_START_EVENT("GameStartEvent"),

    /**
     * @see [GameJoinEvent]
     */
    GAME_JOIN_EVENT("GameJoinEvent", "PlayerJoinEvent"),

    /**
     * @see [GameJoinPostEvent]
     */
    GAME_JOIN_POST_EVENT("GameJoinPostEvent", "PlayerJoinPostEvent"),

    /**
     * @see [GameLeaveEvent]
     */
    GAME_LEAVE_EVENT("GameLeaveEvent", "PlayerLeaveEvent"),

    /**
     * @see [GameFinishEvent]
     */
    GAME_FINISH_EVENT("GameFinishEvent"),

    /**
     * @see [GameAreaEnterEvent]
     */
    AREA_ENTER_EVENT("AreaEnterEvent", "GameAreaEnterEvent"),

    /**
     * @see [GameAreaExitEvent]
     */
    AREA_EXIT_EVENT("AreaExitEvent", "GameAreaExitEvent"),

    /**
     * @see [GamePlayerKillEvent]
     */
    PLAYER_KILL_EVENT("PlayerKillEvent", "GamePlayerKillEvent"),

    /**
     * @see [GamePlayerDeathEvent]
     */
    PLAYER_DEATH_EVENT("PlayerDeathEvent", "GamePlayerDeathEvent");

    companion object {
        /**
         * Returns an [EventType] that matches with given [name].
         *
         * @throws IllegalArgumentException is thrown if nothing matches the name.
         */
        fun forName(name: String): EventType {
            values().forEach {
                if (it.name.equals(name, true) || it.alias.contains(name))
                    return it
            }

            throw IllegalArgumentException("EventType $name does not exist.")
        }
    }
}