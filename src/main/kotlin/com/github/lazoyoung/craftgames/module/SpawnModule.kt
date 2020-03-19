package com.github.lazoyoung.craftgames.module

import org.bukkit.entity.Mob

interface SpawnModule {

    fun setPersonalSpawn(spawnTag: String)
    fun setEditorSpawn(spawnTag: String)
    fun setSpectatorSpawn(spawnTag: String)
    fun spawnEntity(type: Mob, spawnTag: String)

}