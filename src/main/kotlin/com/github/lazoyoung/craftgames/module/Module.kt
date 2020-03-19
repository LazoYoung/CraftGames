package com.github.lazoyoung.craftgames.module

import com.github.lazoyoung.craftgames.game.Game

class Module(game: Game) {

    internal val spawn = SpawnModuleImpl(game)

    init {
        for (entry in game.scriptReg) {
            val script = entry.value

            script.setVariable("SpawnModule", spawn as SpawnModule)
            script.parse()
            script.execute()
        }
    }

}