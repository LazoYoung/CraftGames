package com.github.lazoyoung.craftgames.game.player

import com.github.lazoyoung.craftgames.Main
import com.github.lazoyoung.craftgames.api.PlayerType
import com.github.lazoyoung.craftgames.game.Game
import com.github.lazoyoung.craftgames.internal.util.LocationUtil
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.loot.LootContext
import org.bukkit.loot.LootTable
import org.bukkit.potion.PotionEffect
import org.bukkit.util.io.BukkitObjectInputStream
import org.bukkit.util.io.BukkitObjectOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.*
import kotlin.collections.HashMap

enum class RestoreMode {
    JOIN, RESPAWN, LEAVE
}

open class PlayerData {
    internal var keepInventory: Boolean = false
    internal var itemReward: LootTable? = null
    internal var moneyReward: Double = 0.0
    internal val player: Player
    private var game: Game?
    private val defaultGameMode: GameMode
    private lateinit var restoreFile: File
    private lateinit var restoreGameMode: GameMode
    private lateinit var restoreLocation: Location
    private var restoreHealth: Double = 20.0
    private var restoreLevel: Int = 0
    private var restoreExperience: Float = 0f
    private val restorePotionEffects = LinkedList<PotionEffect>()
    private val restoreItems = LinkedList<ItemStack?>()

    internal constructor(player: Player, game: Game, defaultGameMode: GameMode) {
        this.player = player
        this.game = game
        this.defaultGameMode = defaultGameMode
    }

    private constructor(
            player: Player,
            file: File,
            gameMode: GameMode,
            location: Location,
            health: Double,
            level: Int,
            experience: Float,
            potionEffects: List<PotionEffect>,
            invSlot: List<ItemStack?>
    ) {
        this.player = player
        this.game = null
        this.defaultGameMode = gameMode
        this.restoreFile = file
        this.restoreGameMode = gameMode
        this.restoreLocation = location
        this.restoreHealth = health
        this.restoreLevel = level
        this.restoreExperience = experience
        this.restorePotionEffects.addAll(potionEffects)
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
                    var location: Location
                    val effects = ArrayList<PotionEffect>()
                    val invSlot = ArrayList<ItemStack?>()
                    val gameMode = wrapper.readObject() as GameMode

                    try {
                        location = wrapper.readObject() as Location

                        if (!location.isWorldLoaded) {
                            error("World is not loaded.")
                        }
                    } catch (e: Exception) {
                        location = LocationUtil.getExitFallback(player.world.name)
                    }

                    val health = wrapper.readDouble()
                    val level = wrapper.readInt()
                    val experience = wrapper.readFloat()
                    val effSize = wrapper.readInt()

                    for (i in 0 until effSize) {
                        effects.add(wrapper.readObject() as PotionEffect)
                    }

                    val invSize = wrapper.readInt()

                    for (i in 0 until invSize) {
                        invSlot.add(wrapper.readObject() as? ItemStack)
                    }

                    wrapper.close()
                    return PlayerData(player, restoreFile, gameMode,
                            location, health, level, experience, effects, invSlot)
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
            restore(RestoreMode.LEAVE)
            unregister()

            if (moneyReward > 0.0) {
                Main.vaultEco!!.depositPlayer(player, moneyReward)
            }

            if (itemReward != null) {
                val context = LootContext.Builder(player.location).build()
                val patch = Main.lootTableFix!!

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
     * Restore player's attributes according to [mode].
     *
     * [RestoreMode.JOIN] - Every attributes are reset: max-health, health, hunger, potion effect, etc.
     *
     * [RestoreMode.RESPAWN] - Attributes are kept except gamemode, health, hunger being reset.
     * Kit is equipped to this player if applicable.
     *
     * [RestoreMode.LEAVE] - Attributes are restored to the point where this player issued /join.
     *
     * @param mode Defines restore mode.
     */
    fun restore(mode: RestoreMode) {
        val maxHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH)!!
        player.health = maxHealth.value
        player.foodLevel = 20
        player.saturation = 5f
        player.exhaustion = 0f
        player.activePotionEffects.forEach { player.removePotionEffect(it.type) }

        when (mode) {
            RestoreMode.JOIN -> {
                val flySpeed = player.getAttribute(Attribute.GENERIC_FLYING_SPEED)
                val walkSpeed = player.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED)
                maxHealth.baseValue = maxHealth.defaultValue
                flySpeed?.defaultValue?.let { flySpeed.baseValue = it }
                walkSpeed?.defaultValue?.let { walkSpeed.baseValue = it }
                player.gameMode = defaultGameMode
                player.level = 0
                player.exp = 0f
                player.inventory.clear()
            }
            RestoreMode.RESPAWN -> {
                if (!keepInventory) {
                    getGame().getItemService().applyKit(player)
                }

                player.gameMode = defaultGameMode
            }
            RestoreMode.LEAVE -> {
                player.inventory.clear()
                player.gameMode = restoreGameMode
                player.health = restoreHealth
                player.level = restoreLevel
                player.exp = restoreExperience
                player.addPotionEffects(restorePotionEffects)
                player.teleportAsync(restoreLocation, PlayerTeleportEvent.TeleportCause.PLUGIN)
                        .exceptionally { it.printStackTrace(); false }

                for ((index, item) in restoreItems.withIndex()) {
                    player.inventory.setItem(index, item)
                }

                try {
                    Files.deleteIfExists(restoreFile.toPath())
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
            this.restoreItems.clear()
            this.restorePotionEffects.clear()
            this.restoreItems.addAll(migrate.restoreItems)
            this.restorePotionEffects.addAll(migrate.restorePotionEffects)
            this.restoreFile = migrate.restoreFile
            this.restoreGameMode = migrate.restoreGameMode
            this.restoreLocation = migrate.restoreLocation
            this.restoreHealth = migrate.restoreHealth
            this.restoreLevel = migrate.restoreLevel
            this.restoreExperience = migrate.restoreExperience
            this.itemReward = migrate.itemReward
            this.moneyReward = migrate.moneyReward
            this.keepInventory = migrate.keepInventory
            return
        }

        val inv = player.inventory
        val effects = player.activePotionEffects
        val wrapper = ByteArrayOutputStream()
        var stream: BukkitObjectOutputStream? = null
        this.restoreFile = getDataFile(player.uniqueId)
        this.restoreGameMode = player.gameMode
        this.restoreLocation = player.location
        this.restoreHealth = player.health
        this.restoreLevel = player.level
        this.restoreExperience = player.exp

        try {
            stream = BukkitObjectOutputStream(wrapper)
            stream.writeObject(player.gameMode)
            stream.writeObject(player.location)
            stream.writeDouble(player.health)
            stream.writeInt(player.level)
            stream.writeFloat(player.exp)
            stream.writeInt(effects.size)

            for (effect in effects) {
                restorePotionEffects.add(effect)
                stream.writeObject(effect)
            }

            stream.writeInt(inv.size)

            for (i in 0 until inv.size) {
                val itemStack = inv.getItem(i)

                restoreItems.add(itemStack)
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
    }

    private fun unregister() {
        game = null
        registry.remove(player.uniqueId)
    }
}