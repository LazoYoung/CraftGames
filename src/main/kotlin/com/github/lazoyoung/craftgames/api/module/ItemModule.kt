package com.github.lazoyoung.craftgames.api.module

import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.loot.LootTable

interface ItemModule {

    fun spawnItem(tag: String, itemStack: ItemStack)

    fun getLootTable(key: NamespacedKey): LootTable?

    /**
     * Let the players select kit during the countdown in lobby.
     *
     * @param respawn If true, players are allowed to choose kit during the respawn cooldown as well.
     */
    fun allowKit(respawn: Boolean)

    /**
     * Default kit is assigned to the players who don't select anything.
     *
     * @param name The kit name. (Nullable)
     */
    fun setDefaultKit(name: String?)

    /**
     * Select the [player]'s kit.
     *
     * Upon death, player's state is restored again with this kit.
     * You can still use this function even if you disallow players to select one.
     *
     * @param name The kit name. (Pass null to de-select)
     * @throws IllegalArgumentException is thrown if no kit was found by the given [name].
     * @see [allowKit]
     */
    fun selectKit(name: String?, player: Player)

    /**
     * Apply the selected kit to the [player].
     *
     * After clearing up the inventory, contents are supplied.
     */
    fun applyKit(player: Player)

    fun saveKit(name: String, player: Player)

}