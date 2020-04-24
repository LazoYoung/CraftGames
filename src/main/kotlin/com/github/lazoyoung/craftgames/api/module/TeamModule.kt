package com.github.lazoyoung.craftgames.api.module

import org.bukkit.ChatColor
import org.bukkit.entity.Player
import org.bukkit.scoreboard.Objective
import org.bukkit.scoreboard.Score
import org.bukkit.scoreboard.Scoreboard
import org.bukkit.scoreboard.Team

interface TeamModule {

    /**
     * Create a new [Team].
     *
     * @param teamName Name of the team.
     * @param color [ChatColor] of the team and its nametag.
     */
    fun createTeam(teamName: String, color: ChatColor): Team

    /**
     * @return The [Scoreboard] instance of this game.
     */
    fun getScoreboard(): Scoreboard

    /**
     * @return The [Team] where the given [Player] is associated at this moment.
     * If the player hasn't joined any, this is null.
     */
    fun getPlayerTeam(player: Player): Team?

    /**
     * @return The [List][java.util.List] of [Player]s associated in the given [Team].
     */
    fun getPlayers(team: Team): List<Player>

    /**
     * Find which [Player] has the most [Score] compared to others.
     *
     * @param objective The objective of the scores to be compared.
     * @return The [Score] of the top player.
     */
    fun getTopPlayerScore(objective: Objective): Score

    /**
     * Find which team(s) have the most [Score] compared to others.
     *
     * @param objective The objective of the scores to be compared.
     * @return The top [Team]s.
     */
    fun getTopTeams(objective: Objective): List<Team>

    /**
     * Score table records the total score of each team.
     *
     * Each entry of this [Map] is a pair of objects: [Team] (key) and [score][Int] (value).
     * Map entires are arranged into descending order, based on [score][Int].
     * Thus, the team with the highest score is the first element.
     *
     * @return A [Map] storing each team's score.
     */
    fun getScoreTable(objective: Objective): Map<Team, Int>

    /**
     * Assign [player] to [team].
     *
     * Player is automatically removed from the previous team if necessary.
     */
    fun assignPlayer(player: Player, team: Team)

    /**
     * A fixed [number] of players are drawn, excluding those who have a team.
     * The selected players will be assigned to the [Team].
     *
     * @param number The number of players to select.
     * @param team The team where selected players should be assigned to.
     */
    fun assignPlayers(number: Int, team: Team)

    /**
     * A set of players are drawn by [ratio], excluding those who have a team.
     * The selected players will be assigned to the [Team].
     *
     * @param ratio The proportion among the players who don't have a team yet. (0.0 to 1.0)
     * @param team The team where selected players should be assigned to.
     */
    fun assignPlayers(ratio: Float, team: Team)

    /**
     * Assign [kits] to the [team].
     *
     * @param team Team instance
     * @param kits Array of [String]s each representing the kit name
     * @throws IllegalArgumentException is thrown if [kits] doesn't exist.
     */
    fun setKit(team: Team, vararg kits: String)

    /**
     * Set spawnpoint for the team.
     *
     * @param team The target [Team].
     * @param spawnTag Name of the coordinate tag which captures spawnpoints.
     * @throws IllegalArgumentException is thrown if [spawnTag] is not in this game.
     */
    fun setSpawnpoint(team: Team, spawnTag: String)

}