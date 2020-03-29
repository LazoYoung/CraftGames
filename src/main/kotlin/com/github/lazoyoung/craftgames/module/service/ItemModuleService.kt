package com.github.lazoyoung.craftgames.module.service

import com.github.lazoyoung.craftgames.module.api.ItemModule
import com.github.lazoyoung.craftgames.script.ScriptBase
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.loot.LootTable

class ItemModuleService(val script: ScriptBase) : ItemModule {

    override fun getLootTable(key: NamespacedKey): LootTable? {
        val table = Bukkit.getLootTable(key)

        if (table == null)
            script.getLogger()?.println("Unable to locate LootTable: $key")

        return table
    }

}