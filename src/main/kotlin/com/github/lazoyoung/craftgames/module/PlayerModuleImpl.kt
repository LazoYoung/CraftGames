package com.github.lazoyoung.craftgames.module

import com.destroystokyo.paper.Title
import com.github.lazoyoung.craftgames.command.RESET_FORMAT
import com.github.lazoyoung.craftgames.game.Game
import com.github.lazoyoung.craftgames.player.GamePlayer
import com.github.lazoyoung.craftgames.player.PlayerData
import com.github.lazoyoung.craftgames.player.Spectator
import net.md_5.bungee.api.ChatColor
import net.md_5.bungee.api.chat.ComponentBuilder
import org.bukkit.GameMode
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.scoreboard.Team
import java.util.*
import java.util.function.BiConsumer
import java.util.function.Predicate
import kotlin.collections.HashMap

class PlayerModuleImpl(val game: Game) : PlayerModule {

    /** Default GameMode **/
    internal var gameMode = GameMode.ADVENTURE // FIXME populate to GameModule

    internal val killTriggers = HashMap<UUID, BiConsumer<Player, LivingEntity>>()

    internal val deathTriggers = HashMap<UUID, Predicate<Player>>()

    override fun addKillTrigger(killer: Player, trigger: BiConsumer<Player, LivingEntity>) {
        killTriggers[killer.uniqueId] = trigger
    }

    override fun addKillTrigger(trigger: BiConsumer<Player, LivingEntity>) {
        getPlayers().forEach { addKillTrigger(it, trigger) }
    }

    override fun addDeathTrigger(player: Player, trigger: Predicate<Player>) {
        deathTriggers[player.uniqueId] = trigger
    }

    override fun addDeathTrigger(trigger: Predicate<Player>) {
        getPlayers().forEach { addDeathTrigger(it, trigger) }
    }

    override fun setDefaultGameMode(mode: GameMode) {
        this.gameMode = mode
    }

    override fun getPlayers(): List<Player> {
        return game.getPlayers().filter { PlayerData.get(it) is GamePlayer }
    }

    override fun getTeamPlayers(team: Team): List<Player> {
        return game.getPlayers().filter { team.hasEntry(it.name) }
    }

    override fun getDeadPlayers(): List<Player> {
        return game.getPlayers().filter { PlayerData.get(it) is Spectator }
    }

    override fun eliminate(player: Player) {
        val gamePlayer = PlayerData.get(player.uniqueId) as? GamePlayer
                ?: return // TODO Write warning to script logger.

        val title = ComponentBuilder("YOU DIED").color(ChatColor.RED).create()
        val subTitle = ComponentBuilder("Type ").color(ChatColor.GRAY)
                .append("/leave").color(ChatColor.WHITE).bold(true).append(" to exit.", RESET_FORMAT).color(ChatColor.GRAY).create()

        player.gameMode = GameMode.SPECTATOR
        player.sendTitle(Title(title, subTitle, 20, 80, 20))
        gamePlayer.toSpectator()
    }

}