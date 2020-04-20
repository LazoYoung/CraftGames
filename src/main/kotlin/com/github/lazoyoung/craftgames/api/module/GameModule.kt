package com.github.lazoyoung.craftgames.api.module

import com.github.lazoyoung.craftgames.api.Timer
import org.bukkit.GameMode
import org.bukkit.entity.Player
import org.bukkit.loot.LootTable
import org.bukkit.scoreboard.Team

interface GameModule {

    /**
     * @return Remaining [time][Timer] for this game.
     */
    fun getTimer(): Timer

    /**
     * Set remaining [time][Timer] for this game.
     */
    fun setTimer(timer: Timer)

    /**
     * Set [minimum][min] and [maximum][max] number of player capacity.
     */
    fun setPlayerCapacity(min: Int, max: Int)

    /**
     * After the game starts, you decide
     * to allow or deny players from joining this game.
     * (Defaults to false)
     */
    fun setCanJoinAfterStart(boolean: Boolean)

    /**
     * Whether or not to let players respawn upon death.
     * (Defaults to false)
     */
    fun setCanRespawn(boolean: Boolean)

    /**
     * Decide if players' items should be kept upon death.
     *
     * @param keep Whether or not to keep items in inventory.
     * @param drop Whether or not to drop items on ground.
     * @throws IllegalArgumentException is thrown if [keep] and [drop] are both true.
     */
    fun setKeepInventory(keep: Boolean, drop: Boolean)

    /**
     * The amount of [time][Timer] players have to wait before they respawn.
     */
    fun setRespawnTimer(timer: Timer)

    /**
     * Toggle LastManStanding mode.
     *
     * In this mode, the player (or its team) wins
     * if they have survived and the others are dead.
     *
     * @param enable true to enable LMS. (Defaults to false)
     */
    fun setLastManStanding(enable: Boolean)

    /**
     * Set default [GameMode].
     */
    fun setGameMode(mode: GameMode)

    /**
     * Control the ability of players to attack each other.
     */
    fun setPVP(pvp: Boolean)

    /**
     * Set [amount][Double] of money the [Player] will be rewarded at the end.
     */
    fun setMoneyReward(player: Player, amount: Double)

    /**
     * Set [lootTable][LootTable] the [Player] will be rewarded at the end.
     *
     * Use [ItemModule.getLootTable] to get a loot table.
     */
    fun setItemReward(player: Player, lootTable: LootTable)

    /**
     * Broadcast [message] to everyone in this game.
     */
    fun broadcast(message: String)

    /**
     * Finish game and mark winner team.
     *
     * @param winner The winning team.
     * @param timer Amount of time to celebrate.
     */
    fun finishGame(winner: Team, timer: Timer)

    /**
     * Finish game and mark a winner.
     *
     * @param winner The winner.
     * @param timer Amount of time to celebrate.
     */
    fun finishGame(winner: Player, timer: Timer)

    /**
     * Finish game and mark it as draw.
     *
     * @param timer Amount of time to celebrate.
     */
    fun drawGame(timer: Timer)

}