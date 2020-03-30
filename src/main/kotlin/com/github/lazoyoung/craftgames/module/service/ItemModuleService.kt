package com.github.lazoyoung.craftgames.module.service

import com.github.lazoyoung.craftgames.coordtag.BlockCapture
import com.github.lazoyoung.craftgames.coordtag.SpawnCapture
import com.github.lazoyoung.craftgames.coordtag.TagMode
import com.github.lazoyoung.craftgames.exception.MapNotFound
import com.github.lazoyoung.craftgames.game.Game
import com.github.lazoyoung.craftgames.module.Module
import com.github.lazoyoung.craftgames.module.api.ItemModule
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.loot.LootTable
import org.bukkit.potion.PotionEffect
import org.bukkit.util.io.BukkitObjectInputStream
import org.bukkit.util.io.BukkitObjectOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.*


class ItemModuleService(private val game: Game) : ItemModule {

    internal var allowKit = true
    private val resource = game.resource
    private val script = resource.script
    private val kitSel = HashMap<UUID, ByteArray>()
    private var defaultKit: ByteArray? = resource.kitData.values.let {
        if (it.isEmpty()) {
            null
        } else {
            it.random()
        }
    }

    override fun getLootTable(key: NamespacedKey): LootTable? {
        val table = Bukkit.getLootTable(key)

        if (table == null)
            script.getLogger()?.println("Unable to locate LootTable: $key")

        return table
    }

    override fun allowKit(respawn: Boolean) {
        allowKit = when (game.phase) {
            Game.Phase.LOBBY -> true
            Game.Phase.PLAYING -> respawn
            Game.Phase.SUSPEND -> false
        }
    }

    override fun setDefaultKit(name: String?) {
        defaultKit = when {
            name == null -> {
                null
            }
            resource.kitData.containsKey(name) -> {
                resource.kitData[name]
            }
            else -> {
                throw IllegalArgumentException("No such kit found: $name")
            }
        }
    }

    override fun selectKit(name: String?, player: Player) {
        val uid = player.uniqueId

        when {
            name == null -> {
                kitSel.remove(uid)
            }
            resource.kitData.containsKey(name) -> {
                kitSel[uid] = resource.kitData[name]!!
            }
            else -> {
                throw IllegalArgumentException("No such kit found: $name")
            }
        }
    }

    override fun applyKit(player: Player) {
        val byteArr = kitSel[player.uniqueId] ?: defaultKit
        val inv = player.inventory

        inv.clear()

        if (byteArr == null)
            return

        try {
            val stream = ByteArrayInputStream(Base64.getDecoder().decode(byteArr))
            val data = BukkitObjectInputStream(stream)
            val invSize = data.readInt()

            /* Read ItemStacks from InputStream */
            for (i in 0 until invSize) {
                inv.setItem(i, data.readObject() as? ItemStack)
            }

            val effSize = data.readInt()

            /* Read PotionEffects from InputStream */
            for (i in 0 until effSize) {
                player.addPotionEffect(data.readObject() as PotionEffect)
            }

            /* Close InputStream */
            data.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun saveKit(name: String, player: Player) {
        val inv = player.inventory
        val effects = player.activePotionEffects

        try {
            val stream = ByteArrayOutputStream()
            val data = BukkitObjectOutputStream(stream)

            /* Write ItemStacks to OutputStream */
            data.writeInt(inv.size)

            for (i in 0 until inv.size) {
                data.writeObject(inv.getItem(i))
            }

            /* Write PotionEffects to OutputStream */
            data.writeInt(effects.size)

            for (effect in effects) {
                data.writeObject(effect)
            }

            /* Close OutputStream */
            data.close()

            val byteArr = Base64.getEncoder().encode(stream.toByteArray())
            resource.kitData[name] = byteArr
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun spawnItem(tag: String, itemStack: ItemStack) {
        val map = game.map
        val world = map.world ?: throw MapNotFound("World is not loaded yet!")
        val ctag = Module.getRelevantTag(game, tag, TagMode.SPAWN, TagMode.BLOCK)
        var counter = 0

        if (ctag.mode == TagMode.SPAWN) { // SpawnCapture
            ctag.getCaptures(map.id).filterIsInstance(SpawnCapture::class.java).forEach {
                world.dropItemNaturally(Location(world, it.x, it.y, it.z), itemStack)
                counter++
            }
        } else { // BlockCapture
            val blockList = ctag.getCaptures(map.id).filterIsInstance(BlockCapture::class.java).map {
                Location(world, it.x.toDouble(), it.y.toDouble(), it.z.toDouble()).block
            }

            for (block in blockList) {
                var b = block

                while (!b.isEmpty) {
                    b = world.getBlockAt(b.x, b.y + 1, b.z)
                }

                world.dropItemNaturally(b.location, itemStack)
                counter++
            }
        }

        script.getLogger()?.println("Spawned $counter items across all ${ctag.name} captures.")
    }
}