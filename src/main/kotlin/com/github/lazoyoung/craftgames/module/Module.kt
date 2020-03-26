package com.github.lazoyoung.craftgames.module

import com.github.lazoyoung.craftgames.coordtag.CoordTag
import com.github.lazoyoung.craftgames.coordtag.TagMode
import com.github.lazoyoung.craftgames.game.Game
import com.github.lazoyoung.craftgames.module.api.GameModule
import com.github.lazoyoung.craftgames.module.api.LobbyModule
import com.github.lazoyoung.craftgames.module.api.MobModule
import com.github.lazoyoung.craftgames.module.api.PlayerModule
import com.github.lazoyoung.craftgames.module.service.GameModuleService
import com.github.lazoyoung.craftgames.module.service.LobbyModuleService
import com.github.lazoyoung.craftgames.module.service.MobModuleService
import com.github.lazoyoung.craftgames.module.service.PlayerModuleService
import com.github.lazoyoung.craftgames.player.PlayerData
import javax.script.Bindings

class Module internal constructor(val game: Game) {

    internal val gameModule = GameModuleService(game)
    internal val lobbyModule = LobbyModuleService(game)
    internal val playerModule = PlayerModuleService(game)
    internal val mobModule = MobModuleService(game)
    private var terminateSignal = false
    private val bind: Bindings

    init {
        val script = game.resource.script

        bind = script.getBindings()
        bind["GameModule"] = gameModule as GameModule
        bind["LobbyModule"] = lobbyModule as LobbyModule
        bind["PlayerModule"] = playerModule as PlayerModule
        bind["MobModule"] = mobModule as MobModule
        script.startLogging()
        script.execute("import com.github.lazoyoung.craftgames.module.Module")
        script.execute("import com.github.lazoyoung.craftgames.util.Timer")
        script.execute("import com.github.lazoyoung.craftgames.util.MessageTask")
        script.parse()
        CoordTag.reload(game)
    }

    companion object {
        fun getGameModule(game: Game): GameModule {
            return game.module.gameModule
        }

        fun getLobbyModule(game: Game): LobbyModule {
            return game.module.lobbyModule
        }

        fun getPlayerModule(game: Game): PlayerModule {
            return game.module.playerModule
        }

        internal fun getSpawnTag(game: Game, name: String): CoordTag {
            val tag = CoordTag.get(game, name) ?: throw IllegalArgumentException("Unable to identify $name tag.")

            if (tag.mode != TagMode.SPAWN)
                throw IllegalArgumentException("Parameter does not accept block tag.")

            return tag
        }
    }

    internal fun update() {
        if (terminateSignal)
            return

        try {
            when (game.phase) {
                Game.Phase.LOBBY -> {}
                Game.Phase.PLAYING -> {
                    lobbyModule.clear()
                    gameModule.startService()
                }
                Game.Phase.SUSPEND -> {
                    lobbyModule.clear()
                    gameModule.endService()
                    bind.clear()
                    game.resource.script.closeIO()
                }
            }
        } catch (e: Exception) {
            terminateSignal = true
            e.printStackTrace()
            game.forceStop(async = true, error = true)
        }
    }

    internal fun ejectPlayer(playerData: PlayerData) {
        if (playerData.game != this.game)
            return

        gameModule.bossBar.removePlayer(playerData.player)
    }

}