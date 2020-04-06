package com.github.lazoyoung.craftgames.game.module

import com.github.lazoyoung.craftgames.Main
import com.github.lazoyoung.craftgames.api.module.ItemModule
import com.github.lazoyoung.craftgames.coordtag.capture.BlockCapture
import com.github.lazoyoung.craftgames.coordtag.capture.SpawnCapture
import com.github.lazoyoung.craftgames.coordtag.tag.TagMode
import com.github.lazoyoung.craftgames.game.Game
import com.github.lazoyoung.craftgames.internal.exception.DependencyNotFound
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

    private var allowKit = true
    private var allowKitRespawn = false
    private val resource = game.resource
    private val script = resource.script
    private val kitSel = HashMap<UUID, ByteArray>()
    private val kitSelName = HashMap<UUID, String>()
    private var defaultKitName: String?
    private var defaultKit: ByteArray?

    init {
        resource.kitData.keys.let {
            if (it.isEmpty()) {
                defaultKitName = null
                defaultKit = null
            } else {
                val name = it.random()
                defaultKitName = name
                defaultKit = resource.kitData[name]
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

    override fun getLootTable(key: NamespacedKey): LootTable? {
        if (Main.lootTablePatch == null)
            throw DependencyNotFound("LootTableFix plugin is required.")

        return Bukkit.getLootTable(key)
                ?: throw IllegalArgumentException("Unable to locate LootTable: $key")
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
                kitSelName[uid] = name
                kitSel[uid] = resource.kitData[name]!!
            }
            else -> {
                throw IllegalArgumentException("No such kit found: $name")
            }
        }
    }

    override fun applyKit(player: Player) {
        val uid = player.uniqueId
        val kitName = kitSelName[uid] ?: defaultKitName
        val byteArr = kitSel[uid] ?: defaultKit
        val inv = player.inventory
        var wrapper: BukkitObjectInputStream? = null

        if (kitName == null || byteArr == null)
            return

        // Clear Inventory and PotionEffects
        player.activePotionEffects.forEach{ e -> player.removePotionEffect(e.type) }
        inv.clear()

        try {
            // Decode from Base64
            val stream = ByteArrayInputStream(Base64.getDecoder().decode(byteArr))
            wrapper = BukkitObjectInputStream(stream)

            /* Read inventory items */
            val invSize = wrapper.readInt()
            for (i in 0 until invSize) {
                inv.setItem(i, wrapper.readObject() as? ItemStack)
            }

            /* Read potion effects */
            val effSize = wrapper.readInt()
            for (i in 0 until effSize) {
                var potionEffect = wrapper.readObject() as PotionEffect

                potionEffect = potionEffect.withDuration(Int.MAX_VALUE)
                player.addPotionEffect(potionEffect)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            script.getLogger()?.println("Failed to apply kit: $kitName")
            script.writeStackTrace(e)
            return
        } finally {
            try {
                wrapper?.close()
            } catch (e: IOException) {
                e.printStackTrace()
                script.writeStackTrace(e)
            }
        }

        player.updateInventory()
        script.getLogger()?.println("Kit $kitName is applied to ${player.name}")
    }

    override fun saveKit(name: String, player: Player) {
        val inv = player.inventory
        val effects = player.activePotionEffects
        val wrapper = ByteArrayOutputStream()
        var stream: BukkitObjectOutputStream? = null

        try {
            stream = BukkitObjectOutputStream(wrapper)

            /* Write inventory items */
            stream.writeInt(inv.size)
            for (i in 0 until inv.size) {
                stream.writeObject(inv.getItem(i))
            }

            /* Write potion effects */
            stream.writeInt(effects.size)
            for (effect in effects) {
                stream.writeObject(effect)
            }

            // Flush to wrapper.
            stream.flush()

            // Encode to Base64
            val byteArr = Base64.getEncoder().encode(wrapper.toByteArray())
            resource.kitData[name] = byteArr
        } catch (e: Exception) {
            e.printStackTrace()
            script.writeStackTrace(e)
            stream?.reset()
        } finally {
            try {
                stream?.close()
            } catch (e: IOException) {
                e.printStackTrace()
                script.writeStackTrace(e)
            }
        }
    }

    internal fun canSelectKit(player: Player): Boolean {
        return when (game.phase) {
            Game.Phase.LOBBY -> allowKit
            Game.Phase.PLAYING -> allowKitRespawn && player.gameMode == GameMode.SPECTATOR
            else -> false
        }
    }
}