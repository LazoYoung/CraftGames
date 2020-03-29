package com.github.lazoyoung.craftgames.module.service

import com.github.lazoyoung.craftgames.coordtag.CoordTag
import com.github.lazoyoung.craftgames.coordtag.TagMode
import com.github.lazoyoung.craftgames.game.Game
import com.github.lazoyoung.craftgames.module.Module
import com.github.lazoyoung.craftgames.module.api.TeamModule
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.entity.Player
import org.bukkit.scoreboard.Scoreboard
import org.bukkit.scoreboard.Team
import kotlin.math.roundToInt

class TeamModuleService(val game: Game) : TeamModule {

    private var spawnTag = HashMap<String, CoordTag>()
    private var scoreboard = Bukkit.getScoreboardManager().newScoreboard
    private val script = game.resource.script

    override fun createTeam(teamName: String, color: String): Team {
        return createTeam(teamName, ChatColor.valueOf(color.toUpperCase()))
    }

    override fun createTeam(teamName: String, color: ChatColor): Team {
        val team = scoreboard.registerNewTeam(teamName)

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

    override fun assignPlayer(player: Player, team: Team) {
        val name = player.name
        val legacy = scoreboard.getEntryTeam(name)

        if (legacy?.removeEntry(name) == true) {
            script.getLogger()?.println("$name is removed from team ${legacy.name}.")
        }

        team.addEntry(name)
        player.scoreboard = scoreboard
        script.getLogger()?.println("$name is assigned to team ${team.name}.")
    }

    override fun assignPlayers(number: Int, team: Team) {
        Module.getPlayerModule(game).getLivingPlayers()
                .filter { scoreboard.getEntryTeam(it.name) == null }
                .shuffled().take(number)
                .forEach{ assignPlayer(it, team) }
    }

    override fun assignPlayers(ratio: Float, team: Team) {
        if (ratio < 0 || ratio > 1)
            throw IllegalArgumentException("Ratio out of range: $ratio")

        val set = Module.getPlayerModule(game).getLivingPlayers()
                .filter { scoreboard.getEntryTeam(it.name) == null }.shuffled()
        val drops = (set.size * (1 - ratio)).roundToInt()

        set.drop(drops).forEach { assignPlayer(it, team) }
    }

    override fun setSpawn(team: Team, spawnTag: String) {
        this.spawnTag[team.name] = Module.getRelevantTag(game, spawnTag, TagMode.SPAWN)
    }

    internal fun getSpawn(player: Player): CoordTag? {
        return getPlayerTeam(player)?.let { spawnTag[it.name] }
    }

    internal fun terminate() {
        spawnTag.clear()
    }

}