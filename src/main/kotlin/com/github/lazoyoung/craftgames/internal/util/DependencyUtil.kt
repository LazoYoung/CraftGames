package com.github.lazoyoung.craftgames.internal.util

import com.github.lazoyoung.craftgames.Main
import com.github.lazoyoung.craftgames.game.Game
import com.github.lazoyoung.craftgames.game.GameResource
import com.github.lazoyoung.craftgames.game.service.CommandModuleService
import com.github.lazoyoung.craftgames.internal.listener.ShopkeepersListener
import com.nisovin.shopkeepers.api.ShopkeepersAPI
import org.bukkit.Bukkit
import org.bukkit.plugin.PluginManager

enum class DependencyUtil(
        private val pluginName: String,
        private val serviceClass: String?,
        private var loaded: Boolean = false
) {
    COMMAND_API("CommandAPI", null) {
        override fun init(pluginManager: PluginManager) {
            super.init(pluginManager)

            if (isLoaded()) {
                registerGameCommands()
            }
        }

        private fun registerGameCommands() {
            for (gameName in Game.getGameNames()) {
                val resource = GameResource(gameName)

                resource.commandScript?.let {
                    val commandModule = CommandModuleService(it, resource.layout)

                    try {
                        it.startLogging()
                        it.bind("CommandModule", commandModule)
                        it.parse()
                        it.run()
                    } catch (e: Throwable) {
                        it.writeStackTrace(e)
                    }
                }
            }
        }
    },

    LOOT_TABLE_FIX("LootTableFix", "com.github.lazoyoung.loottablefix.LootTablePatch"),

    WORLD_EDIT("WorldEdit", null),

    CITIZENS("Citizens", null),

    DENIZEN("Denizen", null),

    SHOP_KEEPER("Shopkeepers", null) {
        override fun init(pluginManager: PluginManager) {
            super.init(pluginManager)

            if (isLoaded()) {
                Bukkit.getServer().pluginManager.registerEvents(ShopkeepersListener(), Main.instance)
            }
        }

        override fun isLoaded(): Boolean {
            return super.isLoaded() && ShopkeepersAPI.isEnabled()
        }
    },

    MYTHIC_MOBS("MythicMobs", null),

    LIBS_DISGUISES("LibsDisguises", null),

    VAULT_ECONOMY("Vault", "net.milkbowl.vault.economy.Economy"),

    VAULT_PERMISSION("Vault", "net.milkbowl.vault.permission.Permission");

    companion object {
        internal fun load(pluginManager: PluginManager) {
            values().forEach {
                it.init(pluginManager)
            }
        }
    }

    open fun isLoaded(): Boolean {
        return loaded
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

    open fun init(pluginManager: PluginManager) {
        check(!loaded) {
            "$pluginName is already loaded."
        }

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
}