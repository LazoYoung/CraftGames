package com.github.lazoyoung.craftgames.module.service

import com.github.lazoyoung.craftgames.game.Game
import com.github.lazoyoung.craftgames.module.api.ScriptModule
import com.github.lazoyoung.craftgames.module.api.ScriptModule.Companion.GAME_INIT_EVENT
import com.github.lazoyoung.craftgames.module.api.ScriptModule.Companion.GAME_START_EVENT
import org.bukkit.event.Event
import java.util.function.Consumer

class ScriptModuleService(game: Game) : ScriptModule {

    internal val events = HashMap<String, Consumer<in Event>>()
    private val script = game.resource.script

    override fun setEventMonitor(type: String, callback: Consumer<in Event>) {
        val arr = arrayOf(GAME_INIT_EVENT, GAME_START_EVENT)
        var event: String? = null
        val typeC = type.replace("_", "")

        for (e in arr) {
            if (typeC.equals(e, true)) {
                event = e
                break
            }
        }

        if (event != null) {
            this.events[event] = callback
            script.getLogger()?.println("Set monitoring for $event.")
        } else {
            throw IllegalArgumentException("Failed to set event monitor. Unknown event: $type")
        }
    }

}