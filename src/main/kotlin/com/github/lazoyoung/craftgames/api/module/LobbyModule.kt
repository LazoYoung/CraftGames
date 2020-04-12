package com.github.lazoyoung.craftgames.api.module

import com.github.lazoyoung.craftgames.api.Timer

interface LobbyModule {

    /**
     * Set spawnpoint for this lobby.
     *
     * @param spawnTag the coordinate tag which defines spawnpoint.
     */
    @Deprecated("CoordTag is not relevant to defining spawnpoint.",
            ReplaceWith("setSpawnpoint"), DeprecationLevel.ERROR)
    fun setSpawn(spawnTag: String)

    /**
     * Set spawnpoint for this lobby.
     *
     * @param x Coordinate in X-axis.
     * @param y Coordinate in Y-axis.
     * @param z Coordinate in Z-axis.
     */
    fun setSpawnpoint(x: Double, y: Double, z: Double)

    /**
     * Set spawnpoint for this lobby.
     *
     * @param x Coordinate in X-axis.
     * @param y Coordinate in Y-axis.
     * @param z Coordinate in Z-axis.
     * @param yaw Yaw degree.
     * @param pitch Pitch degree.
     */
    fun setSpawnpoint(x: Double, y: Double, z: Double, yaw: Float, pitch: Float)

    /**
     * Set timer for this lobby. The game starts when the timer runs out.
     *
     * @param timer is the amount of time for which the game should wait for more players.
     */
    fun setTimer(timer: Timer)

    fun setExitWorld(world: String, x: Double, y: Double, z: Double)

    fun setExitServer(server: String)

}