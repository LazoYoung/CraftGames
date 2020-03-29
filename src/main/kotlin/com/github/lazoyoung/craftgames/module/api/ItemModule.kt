package com.github.lazoyoung.craftgames.module.api

import org.bukkit.NamespacedKey
import org.bukkit.loot.LootTable

interface ItemModule {

    fun getLootTable(key: NamespacedKey): LootTable?

}