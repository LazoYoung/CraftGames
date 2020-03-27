package com.github.lazoyoung.craftgames.module.api

import org.bukkit.ChatColor
import org.bukkit.entity.Player
import org.bukkit.scoreboard.Scoreboard
import org.bukkit.scoreboard.Team

interface TeamModule {

    fun createTeam(teamName: String, color: String): Team

    fun createTeam(teamName: String, color: ChatColor): Team

    fun getScoreboard(): Scoreboard

    fun getPlayerTeam(player: Player): Team?

    fun getPlayers(team: Team): List<Player>

    /**
     * Assign [player] to [team].
     *
     * Player is automatically removed from the previous team if necessary.
     */
    fun assignPlayer(player: Player, team: Team)

    /**
     * A fixed [number] of players are drawn, excluding those who have a team.
     * The selected players will be assigned to the [team].
     *
     * @param number The number of players to select.
     * @param team The team where selected players should be assigned to.
     */
    fun assignPlayers(number: Int, team: Team)

    /**
     * A set of players are drawn by [ratio], excluding those who have a team.
     * The selected players will be assigned to the [team].
     *
     * @param ratio The proportion among the players who don't have a team yet.
     * @param team The team where selected players should be assigned to.
     */
    fun assignPlayers(ratio: Float, team: Team)

    fun setSpawn(team: Team, spawnTag: String)

}