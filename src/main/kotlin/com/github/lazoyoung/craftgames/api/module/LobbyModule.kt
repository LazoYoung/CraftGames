package com.github.lazoyoung.craftgames.api.module

import com.github.lazoyoung.craftgames.api.Timer

interface LobbyModule {

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

    fun setExitWorld(world: String, x: Double, y: Double, z: Double)

    fun setExitServer(server: String)

}