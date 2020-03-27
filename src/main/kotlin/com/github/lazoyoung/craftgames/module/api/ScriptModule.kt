package com.github.lazoyoung.craftgames.module.api

import com.github.lazoyoung.craftgames.util.Timer
import org.bukkit.event.Event
import org.bukkit.scheduler.BukkitTask
import java.util.function.Consumer

interface ScriptModule {

    fun attachEventMonitor(eventType: EventType, callback: Consumer<in Event>)

    fun attachEventMonitor(eventType: String, callback: Consumer<in Event>)

    fun detachEventMonitor(eventType: EventType)

    fun detachEventMonitor(eventType: String)

    fun repeat(counter: Int, interval: Timer, task: Runnable): BukkitTask

    fun wait(delay: Timer, task: Runnable): BukkitTask

}