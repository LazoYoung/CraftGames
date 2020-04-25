package com.github.lazoyoung.craftgames.game.module

import com.github.lazoyoung.craftgames.Main
import com.github.lazoyoung.craftgames.api.TimeUnit
import com.github.lazoyoung.craftgames.api.Timer
import com.github.lazoyoung.craftgames.api.module.WorldModule
import com.github.lazoyoung.craftgames.coordtag.capture.AreaCapture
import com.github.lazoyoung.craftgames.coordtag.capture.BlockCapture
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
import org.bukkit.*
import org.bukkit.block.Container
import org.bukkit.entity.Entity
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.loot.LootTable
import org.bukkit.loot.Lootable
import java.io.FileInputStream
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer

class WorldModuleService(private val game: Game) : WorldModule {

    companion object {
        internal fun getChunkX(coordX: Int): Int {
            return if (coordX >= 0) {
                (coordX - coordX % 16) / 16
            } else {
                (coordX - (15 - ((-coordX - 1) % 16))) / 16
            }
        }

        internal fun getChunkZ(coordZ: Int): Int {
            return if (coordZ >= 0) {
                (coordZ - coordZ % 16) / 16
            } else {
                (coordZ - (15 - ((-coordZ - 1) % 16))) / 16
            }
        }
    }

    internal var difficulty = Difficulty.NORMAL
    internal val gamerules = HashMap<String, String>()
    private val script = game.resource.script

    override fun getMapID(): String {
        return game.map.id
    }

    override fun getWorldBorder(): WorldBorder {
        return getWorld().worldBorder
    }

    override fun setBorderCenter(blockTag: String, index: Int) {
        val tag = Module.getRelevantTag(game, blockTag, TagMode.BLOCK)
        val capture = tag.getCaptures(getMapID())
                .filterIsInstance(BlockCapture::class.java)[index]

        getWorldBorder().setCenter(capture.x.toDouble(), capture.z.toDouble())
    }

    override fun setDifficulty(difficulty: Difficulty) {
        this.difficulty = difficulty
    }

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

    @Suppress("DEPRECATION")
    override fun setGameRule(rule: String, value: Any) {
        if (GameRule.getByName(rule) == null) {
            throw IllegalArgumentException("$rule does not exist.")
        }

        gamerules[rule] = value.toString()
        game.map.world?.setGameRuleValue(rule, value.toString())
    }

    override fun fillContainers(blockTag: String, loot: LootTable) {
        val world = getWorld()
        val mapID = game.map.id
        val ctag = Module.getRelevantTag(game, blockTag, TagMode.BLOCK)
        val captures = ctag.getCaptures(mapID)

        if (captures.isEmpty()) {
            throw UndefinedCoordTag("$blockTag has no capture in map: $mapID")
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

    override fun fillBlocks(tag: String, material: Material) {
        val world = getWorld()
        val coordTag = Module.getRelevantTag(game, tag, TagMode.AREA, TagMode.BLOCK)
        val captures = coordTag.getCaptures(game.map.id)
        var counter = 0

        captures.forEach {
            when (coordTag.mode) {
                TagMode.AREA -> {
                    val capture = it as AreaCapture
                    var x = capture.x1
                    var y = capture.y1
                    var z = capture.z1

                    do {
                        do {
                            do {
                                world.getBlockAt(x, y, z).type = material
                                counter++
                                x++
                            } while (x <= capture.x2)
                            x = capture.x1
                            y++
                        } while (y <= capture.y2)
                        x = capture.x1
                        y = capture.y1
                        z++
                    } while (z <= capture.z2)
                }
                TagMode.BLOCK -> {
                    val capture = it as BlockCapture

                    world.getBlockAt(capture.x, capture.y, capture.z).type = material
                    counter++
                }
                else -> throw IllegalStateException("Illegal tag mode.")
            }
        }

        script.printDebug("Filled $counter blocks at $tag.")
    }

    override fun placeSchematics(tag: String, path: String, biomes: Boolean, entities: Boolean, ignoreAir: Boolean) {
        if (!Bukkit.getPluginManager().isPluginEnabled("WorldEdit"))
            throw DependencyNotFound("WorldEdit is required to place schematics.")

        val filePath = game.resource.root.resolve(path)
        val file = filePath.toFile()
        val world = getWorld()
        val maxBlocks = Main.getConfig()?.getInt("optimization.schematic-throttle", 10000) ?: 10000
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
    fun teleportSpawn(playerData: PlayerData, index: Int?): CompletableFuture<Boolean> {
        val scheduler = Bukkit.getScheduler()
        val player = playerData.getPlayer()
        val gracePeriod = Main.getConfig()?.getLong("spawn.invincible", 60L) ?: 60L
        val future = Module.getPlayerModule(game).getSpawnpoint(playerData, index)

        return future.exceptionally { t ->
            t.printStackTrace()
            null
        }.thenComposeAsync {
            return@thenComposeAsync if (it == null) {
                CompletableFuture.completedFuture(false)
            } else {
                player.teleportAsync(it, PlayerTeleportEvent.TeleportCause.PLUGIN)
                        .handleAsync { result, t ->
                            if (t != null) {
                                t.printStackTrace()
                                return@handleAsync false
                            } else {
                                scheduler.runTask(Main.instance, Runnable {
                                    player.isInvulnerable = true
                                    scheduler.runTaskLater(Main.instance, Runnable {
                                        player.isInvulnerable = false
                                    }, Timer(TimeUnit.TICK, gracePeriod).toTick())
                                })
                                return@handleAsync result
                            }
                        }
            }
        }
    }

    internal fun getWorld(): World {
        return game.map.world ?: throw MapNotFound()
    }

    internal inline fun <reified T : Entity> getEntitiesInside(areaTag: String, callback: Consumer<List<T>>) {
        val world = Module.getWorldModule(game).getWorld()
        val tag = Module.getRelevantTag(game, areaTag, TagMode.AREA)
        val captures = tag.getCaptures(game.map.id)
                .filterIsInstance(AreaCapture::class.java)
        val futureMap = LinkedHashMap<CompletableFuture<Chunk>, AreaCapture>()
        val totalMobs = LinkedList<T>()

        // Load chunks asynchronously
        captures.forEach { capture ->
            var xCursor = getChunkX(capture.x1)
            var zCursor = getChunkZ(capture.z1)

            do {
                do {
                    val future = world.getChunkAtAsync(xCursor, zCursor)
                            .exceptionally { t ->
                                t.printStackTrace()
                                futureMap.clear()
                                callback.accept(emptyList())
                                null
                            }

                    futureMap[future] = capture
                    xCursor++
                } while (xCursor * 16 <= capture.x2)

                xCursor = getChunkX(capture.x1)
                zCursor++
            } while (zCursor * 16 <= capture.z2)
        }

        CompletableFuture.allOf(*futureMap.keys.toTypedArray()).handleAsync { _, t ->
            if (t != null) {
                t.printStackTrace()
                callback.accept(emptyList())
                return@handleAsync
            }

            Bukkit.getScheduler().runTask(Main.instance, Runnable {

                // Aggregate mobs by iterating each chunk
                futureMap.forEach { (future, capture) ->
                    try {
                        val chunk = future.join()
                        val mobs = chunk.entities

                        totalMobs.addAll(
                                mobs.filterIsInstance<T>()
                                        .filter { return@filter capture.isInside(it) }
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                callback.accept(totalMobs)
            })
        }
    }
}