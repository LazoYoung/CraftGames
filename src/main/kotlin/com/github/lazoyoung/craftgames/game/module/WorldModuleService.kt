package com.github.lazoyoung.craftgames.game.module

import com.github.lazoyoung.craftgames.Main
import com.github.lazoyoung.craftgames.api.TimeUnit
import com.github.lazoyoung.craftgames.api.Timer
import com.github.lazoyoung.craftgames.api.module.WorldModule
import com.github.lazoyoung.craftgames.coordtag.capture.BlockCapture
import com.github.lazoyoung.craftgames.coordtag.capture.SpawnCapture
import com.github.lazoyoung.craftgames.coordtag.tag.CoordTag
import com.github.lazoyoung.craftgames.coordtag.tag.TagMode
import com.github.lazoyoung.craftgames.game.Game
import com.github.lazoyoung.craftgames.game.player.PlayerData
import com.github.lazoyoung.craftgames.internal.exception.DependencyNotFound
import com.github.lazoyoung.craftgames.internal.exception.MapNotFound
import com.github.lazoyoung.craftgames.internal.exception.UndefinedCoordTag
import com.sk89q.worldedit.WorldEdit
import com.sk89q.worldedit.WorldEditException
import com.sk89q.worldedit.bukkit.BukkitAdapter
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats
import com.sk89q.worldedit.function.operation.Operations
import com.sk89q.worldedit.math.BlockVector3
import com.sk89q.worldedit.session.ClipboardHolder
import net.md_5.bungee.api.ChatColor
import net.md_5.bungee.api.chat.ComponentBuilder
import net.md_5.bungee.api.chat.TextComponent
import org.bukkit.*
import org.bukkit.block.Container
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.loot.LootTable
import org.bukkit.loot.Lootable
import java.io.FileInputStream
import java.util.function.Consumer

class WorldModuleService(private val game: Game) : WorldModule {

    /** Key: AreaName(Tag), Value: Trigger function **/
    internal val triggers = HashMap<String, Consumer<Player>>()
    private val script = game.resource.script

    override fun getMapID(): String {
        return game.map.id
    }

    override fun getWorldBorder(): WorldBorder {
        return getWorld().worldBorder
    }

    override fun setAreaTrigger(tag: String, task: Consumer<Player>?) {}

    override fun setStormyWeather(storm: Boolean) {
        val world = getWorld()

        world.setStorm(storm)
        world.weatherDuration = Int.MAX_VALUE
    }

    override fun getTime(absolute: Boolean): Long {
        return if (absolute) {
            getWorld().fullTime
        } else {
            getWorld().time
        }
    }

    override fun addTime(time: Long) {
        setTime(getTime(true) + time, true)
    }

    override fun setTime(time: Long, absolute: Boolean) {
        if (absolute) {
            getWorld().fullTime = time
        } else {
            getWorld().time = time
        }
    }

    override fun <T> setGameRule(rule: GameRule<T>, value: T) {
        getWorld().setGameRule(rule, value)
    }

    override fun fillContainers(tag: String, loot: LootTable) {
        val world = getWorld()
        val mapID = game.map.id
        val ctag = Module.getRelevantTag(game, tag, TagMode.BLOCK)
        val captures = ctag.getCaptures(mapID)

        if (captures.isEmpty()) {
            throw UndefinedCoordTag("$tag has no capture in map: $mapID")
        }

        captures.filterIsInstance(BlockCapture::class.java).forEach {
            val ident = ctag.name.plus("/").plus(it.index)
            val loc = it.toLocation(world)
            val block = loc.block
            val state = block.state

            if (state !is Lootable || state !is Container) {
                throw IllegalArgumentException("Unable to locate lootable container from: $ident")
            }

            if (!state.isPlaced) {
                throw RuntimeException("BlockState is not available!")
            }

            state.inventory.clear()
            state.lootTable = loot
            state.update(true, false)
            script.printDebug("Filled a container at $ident with ${loot.key}")
        }
    }

    override fun placeSchematics(tag: String, path: String, biomes: Boolean, entities: Boolean, ignoreAir: Boolean) {
        if (!Bukkit.getPluginManager().isPluginEnabled("WorldEdit"))
            throw DependencyNotFound("WorldEdit is required to place schematics.")

        val filePath = game.resource.root.resolve(path)
        val file = filePath.toFile()
        val world = getWorld()
        val maxBlocks = Main.getConfig()?.getInt("schematic-throttle", 10000) ?: 10000
        val format = ClipboardFormats.findByFile(file)
                ?: throw IllegalArgumentException("Unable to resolve schematic file: $filePath")
        val ctag = CoordTag.get(game, tag)
                ?: throw IllegalArgumentException("Tag $tag doesn't exist in this map.")

        if (ctag.mode != TagMode.BLOCK)
            throw IllegalArgumentException("$ctag is an ${ctag.mode.label} tag which isn't supported by this function.")

        format.getReader(FileInputStream(file)).use {
            val holder = ClipboardHolder(it.read())
            val session = WorldEdit.getInstance().editSessionFactory
                    .getEditSession(BukkitAdapter.adapt(world), maxBlocks)

            ctag.getCaptures(game.map.id).filterIsInstance(BlockCapture::class.java).forEach { capture ->
                script.printDebug("Placing schematic ${file.name} at ${ctag.name}/${capture.index}")

                try {
                    session.use {
                        val operation = holder.createPaste(session).copyBiomes(biomes)
                                .copyEntities(entities).ignoreAirBlocks(ignoreAir)
                                .to(BlockVector3.at(capture.x, capture.y, capture.z)).build()

                        Operations.complete(operation)
                        script.printDebug("Schematic process complete.")
                    }
                } catch (e: WorldEditException) {
                    e.printStackTrace()
                    script.writeStackTrace(e)
                    script.print("Failed to place schematic ${file.name} at ${ctag.name}/${capture.index}!")
                }
            }
        }
    }

    fun getAreaNameAt(loc: Location): String? {
        for (entry in game.map.areaRegistry) {
            if (entry.value.firstOrNull { it.isInside(loc) } != null) {
                return entry.key
            }
        }

        return null
    }

    /**
     * Teleport [player][playerData] to the relevant spawnpoint
     * matching with its [type][PlayerData].
     *
     * @param index Index of the spawnpoint capture. (Optional)
     * @throws UndefinedCoordTag If spawnpoint is not captured in this map, this is thrown.
     */
    fun teleportSpawn(playerData: PlayerData, index: Int?, asyncCallback: Consumer<Boolean>? = null) {
        val world = getWorld()
        val scheduler = Bukkit.getScheduler()
        val player = playerData.getPlayer()
        val playerModule = Module.getPlayerModule(game)
        val tag = playerModule.getSpawnpoint(playerData)
        val script = game.resource.script
        val location: Location
        val notFound = ComponentBuilder("Unable to locate spawnpoint!")
                .color(ChatColor.RED).create().first() as TextComponent

        if (tag == null) {
            location = world.spawnLocation
            location.y = world.getHighestBlockYAt(location).toDouble()
            player.sendMessage(notFound)
            script.print("Spawn tag is not defined for ${player.name}.")
        } else {
            val mapID = game.map.id
            val captures = tag.getCaptures(mapID)

            if (captures.isEmpty()) {
                location = world.spawnLocation
                location.y = world.getHighestBlockYAt(location).toDouble()
                player.sendMessage(notFound)
                script.print("Spawn tag \'${tag.name}\' is not captured in: $mapID")
            } else {
                val c = if (index != null) {
                    captures[index % captures.size] as SpawnCapture
                } else {
                    captures.random() as SpawnCapture
                }

                location = Location(world, c.x, c.y, c.z, c.yaw, c.pitch)
            }
        }

        fun protect() {
            val gracePeriod = Main.getConfig()?.getLong("spawn.invincible", 60L) ?: 60L

            player.isInvulnerable = true
            scheduler.runTaskLater(Main.instance, Runnable {
                player.isInvulnerable = false
            }, Timer(TimeUnit.TICK, gracePeriod).toTick())
        }

        if (asyncCallback == null) {
            player.teleport(location)
            protect()
        } else {
            scheduler.runTaskAsynchronously(Main.instance, Runnable {
                player.teleportAsync(location, PlayerTeleportEvent.TeleportCause.PLUGIN)
                        .thenAccept(asyncCallback::accept)
                        .thenRun { protect() }
                        .exceptionally { it.printStackTrace(); return@exceptionally null }
            })
        }
    }

    private fun getWorld(): World {
        return game.map.world ?: throw MapNotFound()
    }

}