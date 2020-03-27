package com.github.lazoyoung.craftgames.module.api

import org.bukkit.event.Event
import java.util.function.Consumer

interface EventModule {

    fun attachMonitor(eventType: EventType, callback: Consumer<in Event>)

    fun attachMonitor(eventType: String, callback: Consumer<in Event>)

    fun detachMonitor(eventType: EventType)

    fun detachMonitor(eventType: String)

}