package com.github.lazoyoung.craftgames.module

interface SpawnModule {

    val PERSONAL: Int
        get() = 0
    val EDITOR: Int
        get() = 1
    val SPECTATOR: Int
        get() = 2

    fun setSpawn(type: Int, spawnTag: String)
    fun spawnMythicMob(name: String, level: Int, spawnTag: String)

}