package com.github.lazoyoung.craftgames.api.module

import com.github.lazoyoung.craftgames.api.tag.item.ItemTag
import com.github.lazoyoung.craftgames.impl.exception.MapNotFound
import org.bukkit.Location
import org.bukkit.entity.Item
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.loot.LootTable

interface ItemModule {

    /**
     * Spawn a floating item.
     *
     * @param tag Name of the coordinate tag designating the spawnpoint.
     * @param itemStack [ItemStack] which represents attribute of the item.
     */
    @Deprecated("Location should be used to determine spawnpoint.", ReplaceWith("spawnItem(Location, itemStack)"))
    fun spawnItem(tag: String, itemStack: ItemStack)

    /**
     * Spawn a floating item.
     *
     * @param location Location of spawnpoint.
     * @param item [ItemStack] which represents attribute of the item.
     * @return [Item] entity that is spawned.
     * @throws MapNotFound is thrown if [location] indicates a world which is not loaded.
     */
    fun spawnItem(location: Location, item: ItemStack): Item

    /**
     * Get [ItemTag] by searching for [name].
     *
     * One tag stores [ItemStack] information for one item.
     * Tags are created via '/itag create' in editor mode.
     */
    fun getItemTag(name: String): ItemTag?

    /**
     * Find [LootTable] by the [key].
     *
     * ex) (game_dir)/datapack/loot_tables/test/loot.json -> test/loot
     */
    fun getLootTable(key: String): LootTable?

    /**
     * Let the players select kit inside lobby.
     *
     * @param respawn True if players waiting for respawn should be able to select kit as well.
     */
    fun allowKit(respawn: Boolean)

    /**
     * Prevent players from dropping items.
     */
    fun preventItemDrop()

    /**
     * Lock inventory so that players won't be able to move items in it.
     */
    fun lockInventory()

    /**
     * Default kit is assigned to players who don't select anything.
     *
     * To assign kit per team, see [TeamModule.setKit]
     *
     * @param name The name of kit. Pass null to assign nothing.
     */
    fun setDefaultKit(name: String?)

    /**
     * Select the [player]'s kit.
     *
     * Upon death, player's state is restored again with this kit.
     * You can still use this function even if you disallow players to select one.
     *
     * @param name The kit name. (Pass null to de-select)
     * @throws IllegalArgumentException is thrown if kit cannot be located or is not allowed for the player.
     * @see [allowKit]
     */
    fun selectKit(name: String?, player: Player)

    /**
     * Apply the selected kit to the [player].
     *
     * After clearing up the inventory, contents are supplied.
     */
    fun applyKit(player: Player)

    /**
     * Save kit with the given [name][String]. If there's an existing kit, this will overwrite it.
     *
     * The [Player]'s inventory and point effects are captured.
     *
     * @param name Name of the kit.
     */
    fun saveKit(name: String, player: Player)

}