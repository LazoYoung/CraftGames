package com.github.lazoyoung.craftgames.module.service

import com.destroystokyo.paper.Title
import com.github.lazoyoung.craftgames.command.RESET_FORMAT
import com.github.lazoyoung.craftgames.game.Game
import com.github.lazoyoung.craftgames.module.api.PlayerModule
import com.github.lazoyoung.craftgames.player.GamePlayer
import com.github.lazoyoung.craftgames.player.PlayerData
import com.github.lazoyoung.craftgames.player.Spectator
import net.md_5.bungee.api.ChatColor
import net.md_5.bungee.api.chat.ComponentBuilder
import net.md_5.bungee.api.chat.TextComponent
import org.bukkit.GameMode
import org.bukkit.attribute.Attribute
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.scoreboard.Team
import java.util.*
import java.util.function.BiConsumer
import java.util.function.Predicate
import kotlin.collections.HashMap

class PlayerModuleService internal constructor(val game: Game) : PlayerModule {

    internal val killTriggers = HashMap<UUID, BiConsumer<Player, LivingEntity>>()
    internal val deathTriggers = HashMap<UUID, Predicate<Player>>()
    private val script = game.resource.script

    override fun addKillTrigger(killer: Player, trigger: BiConsumer<Player, LivingEntity>) {
        killTriggers[killer.uniqueId] = BiConsumer { t, u ->
            try {
                trigger.accept(t, u)
            } catch (e: Exception) {
                script.writeStackTrace(e)
            }
        }
        script.getLogger()?.println("A kill trigger has been binded to ${killer.name}.")
    }

    override fun addDeathTrigger(player: Player, trigger: Predicate<Player>) {
        deathTriggers[player.uniqueId] = Predicate { p ->
            try {
                return@Predicate trigger.test(p)
            } catch (e: Exception) {
                script.writeStackTrace(e)
            }
            return@Predicate false
        }
        script.getLogger()?.println("A death trigger has been binded to ${player.name}.")
    }

    override fun getLivingPlayers(): List<Player> {
        return game.getPlayers().filter { PlayerData.get(it) is GamePlayer }
    }

    override fun getTeamPlayers(team: Team): List<Player> {
        return game.getPlayers().filter { team.hasEntry(it.name) }
    }

    override fun getDeadPlayers(): List<Player> {
        return game.getPlayers().filter { PlayerData.get(it) is Spectator }
    }

    override fun isOnline(player: Player): Boolean {
        return game.getPlayers().contains(player)
    }

    override fun eliminate(player: Player) {
        val gamePlayer = PlayerData.get(player.uniqueId) as? GamePlayer
                ?: throw IllegalArgumentException("Player ${player.name} is not online.")
        val title = ComponentBuilder("YOU DIED").color(ChatColor.RED).create()
        val subTitle = ComponentBuilder("Type ").color(ChatColor.GRAY)
                .append("/leave").color(ChatColor.WHITE).bold(true).append(" to exit.", RESET_FORMAT).color(ChatColor.GRAY).create()

        player.gameMode = GameMode.SPECTATOR
        player.sendTitle(Title(title, subTitle, 20, 80, 20))
        gamePlayer.toSpectator()
    }

    override fun sendMessage(player: Player, message: String) {
        player.sendMessage(*TextComponent.fromLegacyText(message.replace('&', '\u00A7')))
    }

    fun restore(player: Player) {
        player.gameMode = game.module.gameModule.defaultGameMode
        player.health = player.getAttribute(Attribute.GENERIC_MAX_HEALTH)?.value ?: 20.0
        player.foodLevel = 20
        player.saturation = 5.0f
        player.exhaustion = 0.0f
    }

}