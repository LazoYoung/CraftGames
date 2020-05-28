package com.github.lazoyoung.craftgames.api.event

import com.github.lazoyoung.craftgames.impl.game.Game
import org.bukkit.entity.Entity

abstract class GameEntityEvent(
        game: Game,
        val entity: Entity
) : GameEvent(game)