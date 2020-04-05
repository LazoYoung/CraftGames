package com.github.lazoyoung.craftgames.game.player

import com.github.lazoyoung.craftgames.Main
import com.github.lazoyoung.craftgames.api.PlayerType
import com.github.lazoyoung.craftgames.game.Game
import org.bukkit.GameMode
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
    internal var itemReward: LootTable? = null
    internal var moneyReward: Double = 0.0
    private val player: Player
    private var game: Game?
    private val restoreFile: File
    private val restoreGameMode: GameMode
    private val restoreInventorySlot: List<ItemStack?>

    /**
     * @throws RuntimeException is raised if plugin fails to write player's data.
     */
    internal constructor(player: Player, game: Game) {
        val inv = player.inventory
        val wrapper = ByteArrayOutputStream()
        var stream: BukkitObjectOutputStream? = null
        val itemStackList = ArrayList<ItemStack?>()
        this.restoreFile = getDataFile(player.uniqueId)
        this.player = player
        this.game = game
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

            restoreFile.parentFile!!.mkdirs()
            Files.write(restoreFile.toPath(), Base64.getEncoder().encode(wrapper.toByteArray()))
        } catch (e: Exception) {
            throw RuntimeException(e)
        } finally {
            try {
                stream?.close()
            } catch (e: IOException) {
                throw RuntimeException(e)
            }
        }

        this.restoreInventorySlot = itemStackList
    }

    private constructor(player: Player, file: File, gameMode: GameMode, invSlot: List<ItemStack?>) {
        this.player = player
        this.game = null
        this.restoreFile = file
        this.restoreGameMode = gameMode
        this.restoreInventorySlot = invSlot
    }

    companion object {
        internal val registry = HashMap<UUID, PlayerData>()

        fun get(player: Player?): PlayerData? {
            if (player == null)
                return null

            return registry[player.uniqueId]
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
            restore()

            if (moneyReward > 0.0) {
                Main.economy!!.depositPlayer(player, moneyReward)
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
        return game ?: throw IllegalStateException("Player is not in game.")
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

    fun restore() {
        var index = 0
        val inv = player.inventory
        player.gameMode = restoreGameMode

        for (item in restoreInventorySlot) {
            inv.setItem(index++, item)
        }

        try {
            Files.deleteIfExists(restoreFile.toPath())
        } catch (e: IOException) {
            e.printStackTrace()
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    internal fun unregister() {
        game = null
        registry.remove(player.uniqueId)
    }
}