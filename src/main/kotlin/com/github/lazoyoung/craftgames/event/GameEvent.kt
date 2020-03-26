package com.github.lazoyoung.craftgames.event

import com.github.lazoyoung.craftgames.game.Game
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

abstract class GameEvent(val game: Game) : Event() {

    abstract override fun getHandlers(): HandlerList

}