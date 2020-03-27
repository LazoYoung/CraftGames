package com.github.lazoyoung.craftgames.module.service

import com.github.lazoyoung.craftgames.game.Game
import com.github.lazoyoung.craftgames.module.api.EventModule
import com.github.lazoyoung.craftgames.module.api.EventType
import org.bukkit.event.Event
import java.util.function.Consumer

class EventModuleService(game: Game) : EventModule {

    internal val events = HashMap<EventType, Consumer<in Event>>()
    private val script = game.resource.script

    override fun attachMonitor(eventType: EventType, callback: Consumer<in Event>) {
        val legacy = this.events.put(eventType, callback)

        if (legacy == null) {
            script.getLogger()?.println("Attached an event monitor: $eventType")
        } else {
            script.getLogger()?.println("Replaced an event monitor: $eventType")
        }
    }

    override fun attachMonitor(eventType: String, callback: Consumer<in Event>) {
        attachMonitor(EventType.forName(eventType), callback)
    }

    override fun detachMonitor(eventType: EventType) {
        if (events.containsKey(eventType)) {
            events.remove(eventType)
            script.getLogger()?.println("Detached an event monitor: $eventType")
        }
    }

    override fun detachMonitor(eventType: String) {
        detachMonitor(EventType.forName(eventType))
    }

}