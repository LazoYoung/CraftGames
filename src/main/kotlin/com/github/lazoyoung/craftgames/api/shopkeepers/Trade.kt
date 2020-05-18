package com.github.lazoyoung.craftgames.api.shopkeepers

import org.bukkit.inventory.ItemStack

data class Trade(val result: ItemStack, val cost1: ItemStack, val cost2: ItemStack?)