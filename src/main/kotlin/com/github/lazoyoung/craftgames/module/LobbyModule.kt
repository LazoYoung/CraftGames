package com.github.lazoyoung.craftgames.module

import com.github.lazoyoung.craftgames.exception.MapNotFound
import org.bukkit.entity.Player

interface LobbyModule {

    /**
     * Vote a map.
     *
     * @param player is who decided to vote.
     * @param vote How many points are counted for this vote? (1 by default)
     * @param mapName The map name. You can obtain map instances via Game.getMapList()
     * @throws MapNotFound is thrown if mapName doesn't indicate any existing map
     */
    fun voteMap(player: Player, vote: Int = 1, mapName: String): Boolean

    /**
     * Set spawnpoint for this lobby.
     *
     * @param spawnTag the coordinate tag which defines spawnpoint.
     */
    fun setSpawn(spawnTag: String)

    /**
     * Set timer for this lobby. The game starts when the timer runs out.
     *
     * @param timer is the amount of time for which the game should wait for more players.
     */
    fun setTimer(timer: Timer)

}