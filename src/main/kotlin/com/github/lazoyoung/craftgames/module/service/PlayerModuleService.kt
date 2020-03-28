package com.github.lazoyoung.craftgames.module.service

import com.destroystokyo.paper.Title
import com.github.lazoyoung.craftgames.command.RESET_FORMAT
import com.github.lazoyoung.craftgames.coordtag.CoordTag
import com.github.lazoyoung.craftgames.game.Game
import com.github.lazoyoung.craftgames.module.Module
import com.github.lazoyoung.craftgames.module.api.PlayerModule
import com.github.lazoyoung.craftgames.module.api.PlayerType
import com.github.lazoyoung.craftgames.player.GamePlayer
import com.github.lazoyoung.craftgames.player.PlayerData
import com.github.lazoyoung.craftgames.player.Spectator
import com.github.lazoyoung.craftgames.util.TimeUnit
import com.github.lazoyoung.craftgames.util.Timer
import net.md_5.bungee.api.ChatColor
import net.md_5.bungee.api.chat.ComponentBuilder
import net.md_5.bungee.api.chat.TextComponent
import org.bukkit.GameMode
import org.bukkit.attribute.Attribute
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import java.util.*
import java.util.function.BiConsumer
import java.util.function.Predicate
import kotlin.collections.HashMap

class PlayerModuleService internal constructor(val game: Game) : PlayerModule {

    internal var personal: CoordTag? = null
    internal var editor: CoordTag? = null
    internal var spectator: CoordTag? = null
    internal var respawnTimer: Long = Timer(TimeUnit.SECOND, 20).toTick()
    internal val killTriggers = HashMap<UUID, BiConsumer<Player, LivingEntity>>()
    internal val deathTriggers = HashMap<UUID, Predicate<Player>>()
    private val script = game.resource.script

    override fun setKillTrigger(killer: Player, trigger: BiConsumer<Player, LivingEntity>) {
        killTriggers[killer.uniqueId] = BiConsumer { t, u ->
            try {
                trigger.accept(t, u)
            } catch (e: Exception) {
                script.writeStackTrace(e)
                script.getLogger()?.println("Error occurred in Kill trigger: ${killer.name}")
            }
        }
        script.getLogger()?.println("A kill trigger is bound to ${killer.name}.")
    }

    override fun setDeathTrigger(player: Player, trigger: Predicate<Player>) {
        deathTriggers[player.uniqueId] = Predicate { p ->
            try {
                return@Predicate trigger.test(p)
            } catch (e: Exception) {
                script.writeStackTrace(e)
                script.getLogger()?.println("Error occurred in Death trigger: ${player.name}")
            }
            return@Predicate false
        }
        script.getLogger()?.println("A death trigger is bound to ${player.name}.")
    }

    override fun getLivingPlayers(): List<Player> {
        return game.getPlayers().filter { PlayerData.get(it) is GamePlayer }
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

    override fun setRespawnTimer(timer: Timer) {
        this.respawnTimer = timer.toTick()
    }

    override fun setSpawn(type: PlayerType, spawnTag: String) {
        val tag = Module.getSpawnTag(game, spawnTag)

        when (type) {
            PlayerType.PERSONAL -> personal = tag
            PlayerType.EDITOR -> editor = tag
            PlayerType.SPECTATOR -> spectator = tag
        }
    }

    override fun setSpawn(type: String, spawnTag: String) {
        setSpawn(PlayerType.valueOf(type), spawnTag)
    }

    override fun sendMessage(player: Player, message: String) {
        player.sendMessage(*TextComponent.fromLegacyText(message.replace('&', '\u00A7')))
    }

    fun restore(player: Player, leave: Boolean = false) {
        player.gameMode = Module.getGameModule(game).defaultGameMode
        player.health = player.getAttribute(Attribute.GENERIC_MAX_HEALTH)?.value ?: 20.0
        player.foodLevel = 20
        player.saturation = 5.0f
        player.exhaustion = 0.0f
        player.activePotionEffects.forEach{ e -> player.removePotionEffect(e.type) }

        if (leave) {
            // TODO Restore inventory
        }
    }

}