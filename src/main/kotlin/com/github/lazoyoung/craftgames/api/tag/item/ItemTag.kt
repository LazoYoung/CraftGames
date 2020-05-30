package com.github.lazoyoung.craftgames.api.tag.item

import com.github.lazoyoung.craftgames.impl.tag.TagRegistry
import org.bukkit.inventory.ItemStack

class ItemTag internal constructor(
        val name: String,
        val itemStack: ItemStack,
        private val registry: TagRegistry
) {
    internal var removed: Boolean = false

    internal fun remove() {
        check(!removed)
        registry.itagConfig.set(name, null)
        registry.reloadItemTags(this)
    }

}