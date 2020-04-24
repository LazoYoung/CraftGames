package com.github.lazoyoung.craftgames.game.player

import com.github.lazoyoung.craftgames.Main
import com.github.lazoyoung.craftgames.api.PlayerType
import com.github.lazoyoung.craftgames.game.Game
import com.github.lazoyoung.craftgames.game.module.Module
import org.bukkit.GameMode
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.loot.LootContext
import org.bukkit.loot.LootTable
import org.bukkit.util.io.BukkitObjectInputStream
import org.bukkit.util.io.BukkitObjectOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.*
import kotlin.collections.HashMap

open class PlayerData {
    internal var keepInventory: Boolean = false
    internal var itemReward: LootTable? = null
    internal var moneyReward: Double = 0.0
    private val player: Player
    private var game: Game?
    private var restoreFile: File? = null
    private var restoreGameMode: GameMode? = null
    private var restoreItems = ArrayList<ItemStack?>()

    internal constructor(player: Player, game: Game) {
        this.player = player
        this.game = game
    }

    private constructor(player: Player, file: File, gameMode: GameMode, invSlot: List<ItemStack?>) {
        this.player = player
        this.game = null
        this.restoreFile = file
        this.restoreGameMode = gameMode
        this.restoreItems.addAll(invSlot)
    }

    companion object {
        internal val registry = HashMap<UUID, PlayerData>()

        fun get(player: Player?): PlayerData? {
            if (player == null)
                return null

            return registry[player.uniqueId]
        }

        fun get(uid: UUID): PlayerData? {
            return registry[uid]
        }

        /**
         * Read offline [PlayerData] from disk.
         *
         * Path to file: /plugins/CraftGames/_data/players/(uuid)
         *
         * @throws IOException is thrown if plugin fails to read [player]'s data.
         * @throws OutOfMemoryError is thrown if plugin fails to read [player]'s data.
         * @throws SecurityException is thrown if plugin fails to read [player]'s data.
         */
        fun getOffline(player: Player): PlayerData? {
            val uid = player.uniqueId
            val instance = registry[uid]

            if (instance == null) {
                val restoreFile = getDataFile(uid)

                if (restoreFile.isFile) {
                    val byteArray = Files.readAllBytes(restoreFile.toPath())
                    val stream = ByteArrayInputStream(Base64.getDecoder().decode(byteArray))
                    val wrapper = BukkitObjectInputStream(stream)

                    val gameMode = wrapper.readObject() as GameMode
                    val invSlot = ArrayList<ItemStack?>()
                    val invSize = wrapper.readInt()

                    for (i in 0 until invSize) {
                        invSlot.add(wrapper.readObject() as? ItemStack)
                    }

                    wrapper.close()
                    return PlayerData(player, restoreFile, gameMode, invSlot)
                }
            }

            return instance
        }

        private fun getDataFile(uid: UUID): File {
            return Main.dataFolder.resolve("players").resolve(uid.toString())
        }
    }

    fun leaveGame() {
        game?.let {
            it.leave(this)
            restore(respawn = false, leave = true)
            unregister()

            if (moneyReward > 0.0) {
                Main.vaultEco!!.depositPlayer(player, moneyReward)
            }

            if (itemReward != null) {
                val context = LootContext.Builder(player.location).build()
                val patch = Main.lootTablePatch!!

                player.inventory.addItem(*patch.populateLoot(itemReward!!, context).toTypedArray())
                player.updateInventory()
            }
        }
    }

    /**
     * Returns if this player is inside a game or not.
     */
    fun isOnline(): Boolean {
        return game != null
    }

    /**
     * @throws IllegalStateException is thrown if player is not in game.
     * @see isOnline
     */
    open fun getGame(): Game {
        return game ?: throw IllegalStateException("Player ${player.name} is not in game.")
    }

    fun getPlayer(): Player {
        return player
    }

    fun getPlayerType(): PlayerType {
        return when (this) {
            is GamePlayer -> PlayerType.PLAYER
            is Spectator -> PlayerType.SPECTATOR
            is GameEditor -> PlayerType.EDITOR
            else -> throw IllegalStateException("This PlayerData has unknown type.")
        }
    }

    /**
     * Restore this player's state to get ready for new phase.
     *
     * @param respawn if true, kit is equipped to this player if applicable.
     * @param leave if true, it's restored back to the point before he/she joined the game.
     * @throws IllegalArgumentException is thrown if [respawn] and [leave] are both true.
     */
    fun restore(respawn: Boolean, leave: Boolean) {
        val maxHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH)
        val flySpeed = player.getAttribute(Attribute.GENERIC_FLYING_SPEED)
        val walkSpeed = player.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED)

        // Reset player state
        game?.let { player.gameMode = Module.getGameModule(getGame()).defaultGameMode }
        maxHealth?.defaultValue?.let { maxHealth.baseValue = it }
        flySpeed?.defaultValue?.let { flySpeed.baseValue = it }
        walkSpeed?.defaultValue?.let { walkSpeed.baseValue = it }
        player.foodLevel = 20
        player.saturation = 5.0f
        player.exhaustion = 0.0f
        player.activePotionEffects.forEach { e -> player.removePotionEffect(e.type) }

        if (!(respawn && keepInventory)) {
            player.inventory.clear()
        }

        if (respawn && leave) {
            throw IllegalArgumentException()
        } else if (respawn && !keepInventory) {
            Module.getItemModule(getGame()).applyKit(player)
        } else if (leave) {
            val inv = player.inventory

            restoreGameMode?.let { player.gameMode = it }

            if (restoreFile != null) {
                for ((index, item) in restoreItems.withIndex()) {
                    inv.setItem(index, item)
                }

                try {
                    Files.deleteIfExists(restoreFile!!.toPath())
                } catch (e: IOException) {
                    e.printStackTrace()
                } catch (e: SecurityException) {
                    e.printStackTrace()
                }
            }
        }
    }

    /**
     * @param migrate [PlayerData] storing the previous data
     * which should be migrated into this instance (This is optional).
     * @throws RuntimeException is raised if plugin fails to write player's data.
     */
    internal fun captureState(migrate: PlayerData? = null) {
        if (migrate != null) {
            this.restoreFile = migrate.restoreFile
            this.restoreItems = migrate.restoreItems
            this.restoreGameMode = migrate.restoreGameMode
            this.itemReward = migrate.itemReward
            this.moneyReward = migrate.moneyReward
            this.keepInventory = migrate.keepInventory
            return
        }

        val inv = player.inventory
        val wrapper = ByteArrayOutputStream()
        var stream: BukkitObjectOutputStream? = null
        val itemStackList = ArrayList<ItemStack?>()
        this.restoreFile = getDataFile(player.uniqueId)
        this.restoreGameMode = player.gameMode

        try {
            stream = BukkitObjectOutputStream(wrapper)
            stream.writeObject(player.gameMode)
            stream.writeInt(inv.size)

            for (i in 0 until inv.size) {
                val itemStack = inv.getItem(i)

                itemStackList.add(itemStack)
                stream.writeObject(itemStack)
            }

            restoreFile!!.parentFile!!.mkdirs()
            Files.write(restoreFile!!.toPath(), Base64.getEncoder().encode(wrapper.toByteArray()))
        } catch (e: Exception) {
            throw RuntimeException(e)
        } finally {
            try {
                stream?.close()
            } catch (e: IOException) {
                throw RuntimeException(e)
            }
        }

        this.restoreItems = itemStackList
    }

    private fun unregister() {
        game = null
        registry.remove(player.uniqueId)
    }
}