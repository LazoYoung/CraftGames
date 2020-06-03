package com.github.lazoyoung.craftgames.api.event

import com.github.lazoyoung.craftgames.impl.game.Game
import com.github.lazoyoung.craftgames.impl.game.player.GameEditor

abstract class GameEditorEvent(game: Game, val editor: GameEditor)
    : GameEvent(game)