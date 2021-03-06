package com.github.lazoyoung.craftgames.api.event

import com.github.lazoyoung.craftgames.impl.game.Game
import com.github.lazoyoung.craftgames.impl.game.GamePhase
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

abstract class GameEvent(private val game: Game) : Event() {

    abstract override fun getHandlers(): HandlerList

    fun getGame(): Game {
        return game
    }

    fun isGameStarted(): Boolean {
        return getGame().phase == GamePhase.PLAYING
    }

}