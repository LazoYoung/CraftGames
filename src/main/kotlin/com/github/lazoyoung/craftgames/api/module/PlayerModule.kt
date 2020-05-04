package com.github.lazoyoung.craftgames.api.module

import com.github.lazoyoung.craftgames.api.PlayerType
import com.github.lazoyoung.craftgames.internal.exception.DependencyNotFound
import me.libraryaddict.disguise.disguisetypes.CustomDisguise
import me.libraryaddict.disguise.disguisetypes.MiscDisguise
import me.libraryaddict.disguise.disguisetypes.MobDisguise
import me.libraryaddict.disguise.disguisetypes.PlayerDisguise
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.EntityType
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

    /**
     * Eliminate a [player] out of game, consequently switching the player to spectator mode.
     */
    fun eliminate(player: Player)

    /**
     * Disguise a [player] to another Player.
     *
     * This effectively changes the player's skin.
     *
     * @param player Player who are going to disguise.
     * @param skinName Account name of the new skin's owner.
     * @param selfVisible If true, disguise is visible in self perspective.
     * @throws DependencyNotFound is thrown if LibsDisguises is not installed.
     */
    fun disguiseAsPlayer(player: Player, skinName: String, selfVisible: Boolean): PlayerDisguise

    /**
     * Disguise a [player] to a Mob.
     *
     * @param player Player who are going to disguise.
     * @param type Type of this Mob.
     * @param isAdult Whether this Mob is an adult or a baby.
     * @param selfVisible If true, disguise is visible in self perspective.
     * @throws DependencyNotFound is thrown if LibsDisguises is not installed.
     */
    fun disguiseAsMob(player: Player, type: EntityType, isAdult: Boolean, selfVisible: Boolean): MobDisguise

    /**
     * Disguise a [player] to a FallingBlock.
     *
     * @param player Player who are going to disguise.
     * @param material Type of this block.
     * @param selfVisible If true, disguise is visible in self perspective.
     * @throws DependencyNotFound is thrown if LibsDisguises is not installed.
     */
    fun disguiseAsBlock(player: Player, material: Material, selfVisible: Boolean): MiscDisguise

    /**
     * Disguise a [player] to a custom saved preset.
     *
     * Custom disguise can be saved via /savedisguise [name] (disguise_data)
     *
     * @param player Player who are going to disguise.
     * @param name Name of the custom preset.
     * @param selfVisible If true, disguise is visible in self perspective.
     * @throws DependencyNotFound is thrown if LibsDisguises is not installed.
     */
    fun disguiseAsCustomPreset(player: Player, name: String, selfVisible: Boolean): CustomDisguise

    /**
     * Stop disguise of this [player].
     *
     * @param player Player who are going to stop disguise.
     * @throws DependencyNotFound is thrown if LibsDisguises is not installed.
     */
    fun undisguise(player: Player)

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