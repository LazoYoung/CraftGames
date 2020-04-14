package com.github.lazoyoung.craftgames.api.module

import com.github.lazoyoung.craftgames.api.PlayerType
import com.github.lazoyoung.craftgames.api.Timer
import org.bukkit.Location
import org.bukkit.entity.LivingEntity
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
     * The [trigger] executes when the given [Player] kills a [LivingEntity].
     *
     * @param killer is the only player binded to this trigger.
     * @param trigger The trigger that you want to add.
     *   Pass null to this parameter if you want to remove the previous one.
     */
    @Deprecated("Replaced with GamePlayerKillEvent", level = DeprecationLevel.ERROR)
    fun setKillTrigger(killer: Player, trigger: Consumer<LivingEntity>?)

    /**
     * The [trigger] executes right after the given [player] dies.
     *
     * @param player This player is the only one binded to the trigger
     * @param respawn Determines whether or not the player will respawn.
     * @param trigger The trigger that you want to add.
     *   Pass null to this parameter if you want to remove the previous one.
     */
    @Deprecated("Replaced with GamePlayerDeathEvent", level = DeprecationLevel.ERROR)
    fun setDeathTrigger(player: Player, respawn: Boolean, trigger: Runnable?)

    @Deprecated("Replaced with GamePlayerDeathEvent.setRespawnTimer()", level = DeprecationLevel.ERROR)
    fun setRespawnTimer(player: Player, timer: Timer)

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
    fun overrideSpawnpoint(player: Player, tagName: String, index: Int?)

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

    @Deprecated("Name has changed.", ReplaceWith("setSpawnpoint"))
    fun setSpawn(type: String, spawnTag: String)

    /**
     * Send [message] to [player]. Formatting codes are supported.
     *
     * Consult wiki about [Formatting codes](https://minecraft.gamepedia.com/Formatting_codes).
     */
    @Deprecated("Not useful.", level = DeprecationLevel.ERROR)
    fun sendMessage(player: Player, message: String)

}