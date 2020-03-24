package com.github.lazoyoung.craftgames.module

interface SpawnModule {

    companion object {
        const val PERSONAL = 0
        const val EDITOR = 1
        const val SPECTATOR = 2
    }

    fun setPlayerSpawn(type: Int, spawnTag: String)

    fun setPlayerSpawnTimer(timer: Timer)

    fun spawnMob(type: String, spawnTag: String)

    fun spawnMythicMob(name: String, level: Int, spawnTag: String)

}