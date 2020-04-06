package com.github.lazoyoung.craftgames.api.module

import com.github.lazoyoung.craftgames.api.PlayerType
import com.github.lazoyoung.craftgames.api.Timer
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import java.util.function.Consumer

interface PlayerModule {

    fun getLivingPlayers(): List<Player>

    fun getDeadPlayers(): List<Player>

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

    fun setRespawnTimer(player: Player, timer: Timer)

    fun setSpawnpoint(type: PlayerType, spawnTag: String)

    fun setSpawnpoint(type: String, spawnTag: String)

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