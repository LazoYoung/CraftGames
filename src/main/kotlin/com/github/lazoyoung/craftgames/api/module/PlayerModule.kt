package com.github.lazoyoung.craftgames.api.module

import com.github.lazoyoung.craftgames.api.PlayerType
import org.bukkit.Location
import org.bukkit.entity.Player
import java.util.function.Consumer

interface PlayerModule {

    fun getLivingPlayers(): List<Player>

    fun getDeadPlayers(): List<Player>

    /**
     * Inspect which [Player]s are inside the area.
     *
     * @param areaTag Name of the coordinate tag which designates the area.
     * @param callback Callback function that will accept the result
     * ([List] of players inside) once the process is completed.
     */
    fun getPlayersInside(areaTag: String, callback: Consumer<List<Player>>)

    /**
     * Check if [player] is playing this game.
     */
    fun isOnline(player: Player): Boolean

    fun eliminate(player: Player)

    /**
     * Set spawnpoint for the players associated in certain [type][PlayerType].
     *
     * For team-based spawnpoint, see [TeamModule.setSpawnpoint].
     *
     * @param type [PlayerType] (represented by [String]) classifies the players.
     * @param spawnTag Name of the coordinate tag which captures spawnpoints.
     * @throws IllegalArgumentException is thrown if [spawnTag] is not in this game.
     */
    fun setSpawnpoint(type: PlayerType, spawnTag: String)

    /**
     * Set spawnpoint for the players associated in certain [type][PlayerType].
     *
     * For team-based spawnpoint, see [TeamModule.setSpawnpoint].
     *
     * @param type [PlayerType] (represented by [String]) classifies the players.
     * @param spawnTag Name of the coordinate tag which captures spawnpoints.
     * @throws IllegalArgumentException is thrown if [spawnTag] is not in this game.
     */
    fun setSpawnpoint(type: String, spawnTag: String)

    /**
     * Set new spawnpoint for individual player.
     *
     * Default spawnpoint will become ineffective for the player.
     *
     * @param player The target player.
     * @param tagName Name of the coordinate tag which designates spawnpoint.
     * @param index The capture index, which is randomly chosen if you to pass null.
     */
    fun overrideSpawnpoint(player: Player, tagName: String, index: Int = 0)

    /**
     * Set new spawnpoint for individual player.
     *
     * Default spawnpoint will become ineffective for the player.
     *
     * @param player The target player.
     */
    fun overrideSpawnpoint(player: Player, location: Location)

    /**
     * Reset individual spawnpoint back to default one.
     *
     * In other words, it discards the spawnpoint
     * previously set for the player via [overrideSpawnpoint].
     *
     * @param player The target player.
     */
    fun resetSpawnpoint(player: Player)

}