package com.github.lazoyoung.craftgames.module.api

enum class EventType(val label: String) {
    /**
     * @see [com.github.lazoyoung.craftgames.event.GameInitEvent]
     */
    GAME_INIT_EVENT("GameInitEvent"),

    /**
     * @see [com.github.lazoyoung.craftgames.event.GameStartEvent]
     */
    GAME_START_EVENT("GameStartEvent"),

    /**
     * @see [com.github.lazoyoung.craftgames.event.PlayerJoinGameEvent]
     */
    PLAYER_JOIN_GAME_EVENT("PlayerJoinGameEvent"),

    /**
     * @see [com.github.lazoyoung.craftgames.event.PlayerLeaveGameEvent]
     */
    PLAYER_LEAVE_GAME_EVENT("PlayerLeaveGameEvent");

    companion object {
        /**
         * Returns an [EventType] that matches with given [name].
         *
         * @throws IllegalArgumentException is thrown if nothing matches the name.
         */
        fun forName(name: String): EventType {
            values().forEach {
                if (it.name.equals(name, true)
                        || it.label.equals(name, true))
                    return it
            }
            throw IllegalArgumentException("EventType $name does not exist.")
        }
    }
}