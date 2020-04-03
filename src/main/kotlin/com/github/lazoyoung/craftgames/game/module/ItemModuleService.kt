package com.github.lazoyoung.craftgames.game.module

import com.github.lazoyoung.craftgames.api.module.ItemModule
import com.github.lazoyoung.craftgames.coordtag.capture.BlockCapture
import com.github.lazoyoung.craftgames.coordtag.capture.SpawnCapture
import com.github.lazoyoung.craftgames.coordtag.tag.TagMode
import com.github.lazoyoung.craftgames.game.Game
import com.github.lazoyoung.craftgames.internal.exception.MapNotFound
import org.bukkit.Bukkit
import org.bukkit.GameMode
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
import java.io.IOException
import java.util.*


class ItemModuleService(private val game: Game) : ItemModule {

    internal var allowKit = true
    internal var allowKitRespawn = false
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
        allowKit = true
        allowKitRespawn = respawn
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
        var data: BukkitObjectInputStream? = null

        // Clear Inventory and PotionEffects
        player.activePotionEffects.forEach{ e -> player.removePotionEffect(e.type) }
        inv.clear()

        if (byteArr == null)
            return

        try {
            val stream = ByteArrayInputStream(Base64.getDecoder().decode(byteArr))
            data = BukkitObjectInputStream(stream)
            val invSize = data.readInt()

            /* Read ItemStacks from InputStream */
            for (i in 0 until invSize) {
                inv.setItem(i, data.readObject() as? ItemStack)
            }

            val effSize = data.readInt()

            /* Read PotionEffects from InputStream */
            for (i in 0 until effSize) {
                var potionEffect = data.readObject() as PotionEffect

                potionEffect = potionEffect.withDuration(Int.MAX_VALUE)
                player.addPotionEffect(potionEffect)
            }

        } catch (e: Exception) {
            e.printStackTrace()
            script.writeStackTrace(e)
        } finally {
            try {
                data?.close()
            } catch (e: IOException) {
                e.printStackTrace()
                script.writeStackTrace(e)
            }
        }
    }

    override fun saveKit(name: String, player: Player) {
        val inv = player.inventory
        val effects = player.activePotionEffects
        var data: BukkitObjectOutputStream? = null

        try {
            val stream = ByteArrayOutputStream()
            data = BukkitObjectOutputStream(stream)

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

            val byteArr = Base64.getEncoder().encode(stream.toByteArray())
            resource.kitData[name] = byteArr
        } catch (e: Exception) {
            e.printStackTrace()
            script.writeStackTrace(e)
            data?.reset()
        } finally {
            try {
                data?.close()
            } catch (e: IOException) {
                e.printStackTrace()
                script.writeStackTrace(e)
            }
        }
    }

    override fun spawnItem(tag: String, itemStack: ItemStack) {
        val map = game.map
        val world = map.world ?: throw MapNotFound()
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

    internal fun canSelectKit(player: Player): Boolean {
        return when (game.phase) {
            Game.Phase.LOBBY -> allowKit
            Game.Phase.PLAYING -> allowKitRespawn && player.gameMode == GameMode.SPECTATOR
            else -> false
        }
    }
}