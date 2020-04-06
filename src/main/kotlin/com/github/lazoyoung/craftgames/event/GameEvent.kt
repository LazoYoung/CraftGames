package com.github.lazoyoung.craftgames.event

import com.github.lazoyoung.craftgames.game.Game
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

abstract class GameEvent(private val game: Game) : Event() {

    abstract override fun getHandlers(): HandlerList

    fun getGame(): Game {
        return game
    }

    fun isGameStarted(): Boolean {
        return getGame().phase == Game.Phase.PLAYING
    }

}