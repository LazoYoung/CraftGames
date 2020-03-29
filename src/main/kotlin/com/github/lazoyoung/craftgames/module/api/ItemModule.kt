package com.github.lazoyoung.craftgames.module.api

import org.bukkit.NamespacedKey
import org.bukkit.inventory.Inventory
import org.bukkit.loot.LootTable

interface ItemModule {

    fun getLootTable(key: NamespacedKey): LootTable?

    fun fillKit(name: String, inv: Inventory): Inventory

}