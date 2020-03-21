package com.github.lazoyoung.craftgames.module

import com.github.lazoyoung.craftgames.game.Game
import javax.script.Bindings

class Module(game: Game) {

    val spawn = SpawnModuleImpl(game)
    internal val script = game.resource.script
    private val bind: Bindings

    init {
        bind = script.getBindings()
        bind["SpawnModule"] = spawn as SpawnModule
        script.parse()
    }

    fun update(newPhase: Game.Phase) {
        when (newPhase) {
            Game.Phase.LOBBY -> script.invokeFunction("initLobby")
            Game.Phase.PLAYING -> script.invokeFunction("initGame")
            Game.Phase.FINISH -> { /* Reward logic */ }
            Game.Phase.SUSPEND -> {
                bind.clear()
                script.closeIO()
                // TODO Scheduler Module: Suspend schedulers
            }
        }
    }

}