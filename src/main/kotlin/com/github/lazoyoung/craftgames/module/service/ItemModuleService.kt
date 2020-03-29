package com.github.lazoyoung.craftgames.module.service

import com.github.lazoyoung.craftgames.game.GameResource
import com.github.lazoyoung.craftgames.module.api.ItemModule
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.loot.LootTable
import org.bukkit.util.io.BukkitObjectInputStream
import org.bukkit.util.io.BukkitObjectOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.*


class ItemModuleService(val resource: GameResource) : ItemModule {

    val script = resource.script

    override fun getLootTable(key: NamespacedKey): LootTable? {
        val table = Bukkit.getLootTable(key)

        if (table == null)
            script.getLogger()?.println("Unable to locate LootTable: $key")

        return table
    }

    /**
     * @throws IllegalArgumentException is thrown if kit wasn't found by the given [name]
     */
    override fun fillKit(name: String, inv: Inventory): Inventory {
        val byteArr = resource.kitData[name] ?: throw IllegalArgumentException("No such kit found: $name")

        inv.clear()

        try {
            val stream = ByteArrayInputStream(Base64.getDecoder().decode(byteArr))
            val data = BukkitObjectInputStream(stream)
            val size = data.readInt()

            for (i in 0 until size) {
                inv.setItem(i, data.readObject() as? ItemStack)
            }
            data.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return inv
    }

    internal fun saveKit(name: String, inv: Inventory) {
        try {
            val stream = ByteArrayOutputStream()
            val data = BukkitObjectOutputStream(stream)
            data.writeInt(inv.size)
            for (i in 0 until inv.size) {
                data.writeObject(inv.getItem(i))
            }
            data.close()

            val byteArr = Base64.getEncoder().encode(stream.toByteArray())
            resource.kitData[name] = byteArr
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}