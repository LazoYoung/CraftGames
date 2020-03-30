package com.github.lazoyoung.craftgames.module.service

import com.github.lazoyoung.craftgames.Main
import com.github.lazoyoung.craftgames.module.api.EventType
import com.github.lazoyoung.craftgames.module.api.ScriptModule
import com.github.lazoyoung.craftgames.script.ScriptBase
import com.github.lazoyoung.craftgames.util.Timer
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.event.Event
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import java.util.function.Consumer

class ScriptModuleService internal constructor(private val script: ScriptBase) : ScriptModule {

    internal val events = HashMap<EventType, Consumer<in Event>>()
    private val tasks = ArrayList<BukkitTask>()

    override fun attachEventMonitor(eventType: EventType, callback: Consumer<in Event>) {
        val legacy = this.events.put(eventType, callback)

        if (legacy == null) {
            script.getLogger()?.println("Attached an event monitor: $eventType")
        } else {
            script.getLogger()?.println("Replaced an event monitor: $eventType")
        }
    }

    override fun attachEventMonitor(eventType: String, callback: Consumer<in Event>) {
        attachEventMonitor(EventType.forName(eventType), callback)
    }

    override fun detachEventMonitor(eventType: EventType) {
        if (events.containsKey(eventType)) {
            events.remove(eventType)
            script.getLogger()?.println("Detached an event monitor: $eventType")
        }
    }

    override fun detachEventMonitor(eventType: String) {
        detachEventMonitor(EventType.forName(eventType))
    }

    override fun repeat(counter: Int, interval: Timer, task: Runnable): BukkitTask {
        val bukkitTask = object : BukkitRunnable() {
            var count = counter

            override fun run() {
                if (count-- > 0) {
                    task.run()
                } else {
                    this.cancel()
                }
            }
        }.runTaskTimer(Main.instance, 0L, interval.toTick())

        tasks.add(bukkitTask)
        return bukkitTask
    }

    override fun wait(delay: Timer, task: Runnable): BukkitTask {
        val bukkitTask = object : BukkitRunnable() {
            override fun run() {
                task.run()
            }
        }.runTaskLater(Main.instance, delay.toTick())

        tasks.add(bukkitTask)
        return bukkitTask
    }

    override fun readByteStream(file: String): ByteArray {
        TODO("Not yet implemented")
    }

    override fun readYAML(file: String): YamlConfiguration {
        TODO("Not yet implemented")
    }

    internal fun terminate() {
        events.clear()
        tasks.forEach(BukkitTask::cancel)
    }

}