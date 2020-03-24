package com.github.lazoyoung.craftgames.module

import org.bukkit.GameMode
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.scoreboard.Team
import java.util.function.BiConsumer
import java.util.function.Predicate

interface PlayerModule {

    /**
     * The [trigger] executes when the given [Player] kills a [LivingEntity].
     *
     * @param killer is the only player binded to this trigger.
     * @param trigger The trigger that you want to add.
     */
    fun addKillTrigger(killer: Player, trigger: BiConsumer<Player, LivingEntity>)

    /**
     * The [trigger] executes when any [Player] kills a [LivingEntity].
     *
     * @param trigger The trigger that you want to add.
     */
    fun addKillTrigger(trigger: BiConsumer<Player, LivingEntity>)

    /**
     * The [trigger] executes right after the given [player] dies.
     *
     * Boolean value returned from the Predicate
     * determines whether the player respawns(true) or not(false).
     *
     * @param player This player is the only one binded to the trigger.
     * @param trigger The trigger that you want to add.
     */
    fun addDeathTrigger(player: Player, trigger: Predicate<Player>)

    /**
     * The [trigger] executes if any [Player] dies.
     *
     * Boolean value returned from the Predicate
     * determines whether the player respawns(true) or not(false).
     *
     * @param trigger The trigger that you want to add.
     */
    fun addDeathTrigger(trigger: Predicate<Player>)

    fun setDefaultGameMode(mode: GameMode)

    fun getPlayers(): List<Player>

    fun getTeamPlayers(team: Team): List<Player>

    fun getDeadPlayers(): List<Player>

    fun eliminate(player: Player)

}