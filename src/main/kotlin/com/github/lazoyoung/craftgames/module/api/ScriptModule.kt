package com.github.lazoyoung.craftgames.module.api

import org.bukkit.event.Event
import java.util.function.Consumer

interface ScriptModule {

    companion object {
        /**
         * @see [com.github.lazoyoung.craftgames.event.GameInitEvent]
         */
        @JvmStatic
        val GAME_INIT_EVENT = "GameInitEvent"

        /**
         * @see [com.github.lazoyoung.craftgames.event.GameStartEvent]
         */
        @JvmStatic
        val GAME_START_EVENT = "GameStartEvent"
    }

    fun setEventMonitor(type: String, callback: Consumer<in Event>)

}