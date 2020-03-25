package com.github.lazoyoung.craftgames.module.api

interface MobModule {

    fun spawnMob(type: String, spawnTag: String)

    fun spawnMythicMob(name: String, level: Int, spawnTag: String)

}