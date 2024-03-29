package com.github.lazoyoung.craftgames.impl.game.module

import com.github.lazoyoung.craftgames.api.module.TeamModule
import com.github.lazoyoung.craftgames.api.tag.coordinate.CoordTag
import com.github.lazoyoung.craftgames.api.tag.coordinate.TagMode
import com.github.lazoyoung.craftgames.impl.game.Game
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.entity.Player
import org.bukkit.scoreboard.Objective
import org.bukkit.scoreboard.Score
import org.bukkit.scoreboard.Scoreboard
import org.bukkit.scoreboard.Team
import java.util.*
import kotlin.collections.HashMap
import kotlin.math.roundToInt

class TeamModuleService(private val game: Game) : TeamModule, Service {

    private var spawnTag = HashMap<String, CoordTag>()
    private val scoreboard = Bukkit.getScoreboardManager().newScoreboard
    private val script = game.resource.mainScript

    override fun createTeam(teamName: String, color: ChatColor): Team {
        val team = scoreboard.registerNewTeam(teamName)

        team.color = color
        team.prefix = color.toString()
        team.suffix = ChatColor.RESET.toString()
        return team
    }

    override fun getScoreboard(): Scoreboard {
        return scoreboard
    }

    override fun getPlayerTeam(player: Player): Team? {
        return scoreboard.getEntryTeam(player.name)
    }

    override fun getPlayers(team: Team): List<Player> {
        return team.entries.mapNotNull { Bukkit.getPlayer(it) }
    }

    override fun getTopPlayerScore(objective: Objective): Score {
        val players = game.getPlayerService().getLivingPlayers()
        var topScore = objective.getScore(players.first().name)

        players.forEach {
            val score = objective.getScore(it.name)

            if (score.score > topScore.score) {
                topScore = score
            }
        }
        return topScore
    }

    /* TODO Rewrite score calculation methods
    fun getTopTeamScore(objective: Objective): Int {
        val teams = scoreboard.teams
        val topTeams
    }
     */

    override fun getTopTeams(objective: Objective): List<Team> {
        val teams = scoreboard.teams
        val topTeams = arrayListOf(teams.first())
        var topScore = 0

        if (teams.isEmpty()) {
            throw IllegalStateException("No team is defined.")
        }

        teams.forEach {
            val score = getPlayers(it).sumBy { p -> objective.getScore(p.name).score }

            if (score > topScore) {
                topScore = score
                topTeams.clear()
                topTeams.add(it)
            } else if (score == topScore) {
                topTeams.add(it)
            }
        }

        return topTeams
    }

    override fun getScoreTable(objective: Objective): Map<Team, Int> {
        val teams = scoreboard.teams
        val table = HashMap<Team, Int>()
        val orderedTable = HashMap<Team, Int>()
        val linkedList = LinkedList(table.entries)

        if (teams.isEmpty()) {
            throw IllegalStateException("No team is defined.")
        }

        teams.forEach { table[it] = getPlayers(it).sumBy { p -> objective.getScore(p.name).score } }
        linkedList.sortWith(Comparator { e1, e2 -> (e1.value - e2.value) })
        linkedList.forEach { orderedTable[it.key] = it.value }

        return orderedTable
    }

    override fun assignPlayer(player: Player, team: Team) {
        val name = player.name
        val legacy = scoreboard.getEntryTeam(name)

        if (legacy?.removeEntry(name) == true) {
            script.printDebug("$name is removed from team ${legacy.name}.")
        }

        team.addEntry(name)
        script.printDebug("$name is assigned to team ${team.name}.")
    }

    override fun assignPlayers(number: Int, team: Team) {
        game.getPlayerService().getLivingPlayers()
                .filter { scoreboard.getEntryTeam(it.name) == null }
                .shuffled().take(number)
                .forEach{ assignPlayer(it, team) }
    }

    override fun assignPlayers(ratio: Float, team: Team) {
        if (ratio < 0f || ratio > 1f)
            throw IllegalArgumentException("Ratio out of range: $ratio")

        val set = game.getPlayerService().getLivingPlayers()
                .filter { scoreboard.getEntryTeam(it.name) == null }.shuffled()
        val drops = (set.size * (1f - ratio)).roundToInt()
        val assignee = set.drop(drops)

        assignee.forEach { assignPlayer(it, team) }
    }

    override fun setKit(team: Team, vararg kits: String) {
        val itemModule = game.getItemService()
        val kitMap = HashMap<String, ByteArray>()

        for (kitName in kits) {
            val byteArray = game.resource.kitData[kitName]
                    ?: throw IllegalArgumentException("$kits does not exist.")

            kitMap[kitName] = byteArray
        }

        itemModule.teamKit[team.name] = kitMap
        script.printDebug("Kit ".plus(kits.joinToString()).plus(" are assigned to ${team.name}."))
    }

    override fun setSpawnpoint(team: Team, spawnTag: String) {
        error("Deprecated function.")
    }

    override fun setSpawnpoint(team: Team, tag: CoordTag) {
        val mode = tag.mode

        require(mode == TagMode.SPAWN || mode == TagMode.AREA) {
            "Illegal tag mode: ${mode.label}"
        }

        this.spawnTag[team.name] = tag
    }

    internal fun getSpawnpoint(player: Player): CoordTag? {
        return getPlayerTeam(player)?.let { spawnTag[it.name] }
    }

    override fun start() {}

    override fun terminate() {
        scoreboard.objectives.forEach(Objective::unregister)
        scoreboard.teams.forEach(Team::unregister)
    }

}