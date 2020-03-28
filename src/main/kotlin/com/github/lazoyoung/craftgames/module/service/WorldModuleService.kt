package com.github.lazoyoung.craftgames.module.service

import com.github.lazoyoung.craftgames.game.Game
import com.github.lazoyoung.craftgames.module.api.WorldModule
import org.bukkit.entity.Player
import java.util.function.Consumer
import org.bukkit.Location

class WorldModuleService(val game: Game) : WorldModule {

    /** Key: AreaName(Tag), Value: Trigger function **/
    internal val triggers = HashMap<String, Consumer<Player>>()
    private val script = game.resource.script

    override fun setAreaTrigger(tag: String, task: Consumer<Player>) {
        if (!game.map.areaRegistry.containsKey(tag))
                throw IllegalArgumentException("Area tag \'$tag\' does not exist.")

        triggers[tag] = Consumer<Player> {
            try {
                task.accept(it)
            } catch (e: Exception) {
                script.writeStackTrace(e)
                script.getLogger()?.println("Error occurred in Area trigger: $tag")
            }
        }
        script.getLogger()?.println("An Area trigger is bound to tag: $tag")
    }

    fun getArenaNameAt(loc: Location): String? {
        for (entry in game.map.areaRegistry) {
            if (entry.value.firstOrNull { it.isInside(loc) } != null) {
                return entry.key
            }
        }

        return null
    }

}