package com.github.lazoyoung.craftgames.module

interface LobbyModule {

    fun setSpawn(spawnTag: String)
    fun setTimer(unit: TimerUnit, value: Int)

}