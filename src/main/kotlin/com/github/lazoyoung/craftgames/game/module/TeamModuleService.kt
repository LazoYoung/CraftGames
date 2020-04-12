package com.github.lazoyoung.craftgames.game.module

import com.github.lazoyoung.craftgames.api.module.TeamModule
import com.github.lazoyoung.craftgames.coordtag.tag.CoordTag
import com.github.lazoyoung.craftgames.coordtag.tag.TagMode
import com.github.lazoyoung.craftgames.game.Game
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.entity.Player
import org.bukkit.scoreboard.Objective
import org.bukkit.scoreboard.Score
import org.bukkit.scoreboard.Scoreboard
import org.bukkit.scoreboard.Team
import java.util.*
import kotlin.Comparator
import kotlin.collections.HashMap
import kotlin.math.roundToInt

class TeamModuleService(private val game: Game) : TeamModule {

    private var spawnTag = HashMap<String, CoordTag>()
    private var scoreboard = Bukkit.getScoreboardManager().newScoreboard
    private val script = game.resource.script

    override fun createTeam(teamName: String, color: String): Team {
        return createTeam(teamName, ChatColor.valueOf(color.toUpperCase()))
    }

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
        val players = Module.getPlayerModule(game).getLivingPlayers()
        var topScore = objective.getScore(players.first().name)

        players.forEach {
            val score = objective.getScore(it.name)

            if (score.score > topScore.score) {
                topScore = score
            }
        }
        return topScore
    }

    override fun getFirstPlayerScore(objective: Objective): Score {
        return getTopPlayerScore(objective)
    }

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

        teams.forEach {
            table[it] = getPlayers(it).sumBy { p -> objective.getScore(p.name).score }
        }

        Collections.sort(linkedList, Comparator { e1, e2 ->
            return@Comparator (e1.value - e2.value)
        })

        linkedList.forEach {
            orderedTable[it.key] = it.value
        }

        return orderedTable
    }

    override fun getFirstTeam(objective: Objective): Team {
        return getTopTeams(objective).first()
    }

    override fun assignPlayer(player: Player, team: Team) {
        val name = player.name
        val legacy = scoreboard.getEntryTeam(name)

        if (legacy?.removeEntry(name) == true) {
            script.printDebug("$name is removed from team ${legacy.name}.")
        }

        team.addEntry(name)
        player.scoreboard = scoreboard
        script.printDebug("$name is assigned to team ${team.name}.")
    }

    override fun assignPlayers(number: Int, team: Team) {
        Module.getPlayerModule(game).getLivingPlayers()
                .filter { scoreboard.getEntryTeam(it.name) == null }
                .shuffled().take(number)
                .forEach{ assignPlayer(it, team) }
    }

    override fun assignPlayers(ratio: Float, team: Team) {
        if (ratio < 0f || ratio > 1f)
            throw IllegalArgumentException("Ratio out of range: $ratio")

        val set = Module.getPlayerModule(game).getLivingPlayers()
                .filter { scoreboard.getEntryTeam(it.name) == null }.shuffled()
        val drops = (set.size * (1f - ratio)).roundToInt()
        val assignee = set.drop(drops)

        assignee.forEach { assignPlayer(it, team) }
    }

    override fun setSpawnpoint(team: Team, spawnTag: String) {
        this.spawnTag[team.name] = Module.getRelevantTag(game, spawnTag, TagMode.SPAWN)
    }

    override fun setSpawn(team: Team, spawnTag: String) {
        setSpawnpoint(team, spawnTag)
    }

    internal fun getSpawnpoint(player: Player): CoordTag? {
        return getPlayerTeam(player)?.let { spawnTag[it.name] }
    }

    internal fun terminate() {
        spawnTag.clear()
        scoreboard.objectives.forEach(Objective::unregister)
        scoreboard.teams.forEach(Team::unregister)
    }

}