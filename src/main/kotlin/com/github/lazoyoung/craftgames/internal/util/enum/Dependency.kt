package com.github.lazoyoung.craftgames.internal.util.enum

import com.github.lazoyoung.craftgames.Main
import org.bukkit.Bukkit
import org.bukkit.plugin.PluginManager

enum class Dependency(
        private val pluginName: String,
        private val serviceClass: String?,
        protected var loaded: Boolean = false
) {
    LOOT_TABLE_FIX("LootTableFix", "com.github.lazoyoung.loottablefix.LootTablePatch"),

    WORLD_EDIT("WorldEdit", null),

    CITIZENS("Citizens", null),

    DENIZEN("Denizen", null),

    MYTHIC_MOBS("MythicMobs", null),

    LIBS_DISGUISES("LibsDisguises", null),

    VAULT_ECONOMY("Vault", "net.milkbowl.vault.economy.Economy"),

    VAULT_PERMISSION("Vault", "net.milkbowl.vault.permission.Permission");

    companion object {
        internal fun loadAll(pluginManager: PluginManager) {
            values().forEach {
                it.init(pluginManager)
            }
        }
    }

    fun isLoaded(): Boolean {
        return loaded
    }

    internal fun init(pluginManager: PluginManager) {
        Main.logger.info("Loading dependency: $name")

        if (!pluginManager.isPluginEnabled(pluginName)) {
            Main.logger.info("...SKIPPED")
            return
        }

        if (serviceClass != null) {
            try {
                getService()
            } catch (e: IllegalStateException) {
                Main.logger.warning(e.localizedMessage)
                Main.logger.warning("...FAILURE")
                return
            }
        }

        this.loaded = true
        Main.logger.info("...SUCCESS")
    }

    open fun getService(): Any {
        checkNotNull(serviceClass) {
            "$name does not provide any service."
        }

        val clazz = Class.forName(serviceClass)
        return checkNotNull(Bukkit.getServicesManager().getRegistration(clazz)) {
            "Service provider is not registered: $serviceClass"
        }.provider
    }
}