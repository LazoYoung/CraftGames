package com.github.lazoyoung.craftgames.game.module

import com.github.lazoyoung.craftgames.Main
import com.github.lazoyoung.craftgames.api.module.MobModule
import com.github.lazoyoung.craftgames.coordtag.capture.AreaCapture
import com.github.lazoyoung.craftgames.coordtag.capture.SpawnCapture
import com.github.lazoyoung.craftgames.coordtag.tag.TagMode
import com.github.lazoyoung.craftgames.game.Game
import com.github.lazoyoung.craftgames.internal.exception.DependencyNotFound
import com.github.lazoyoung.craftgames.internal.exception.FaultyConfiguration
import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.entity.Entity
import org.bukkit.entity.EntityType
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Mob
import org.bukkit.loot.LootTable
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer
import kotlin.collections.ArrayList
import kotlin.random.Random

@Suppress("DuplicatedCode")
class MobModuleService internal constructor(private val game: Game) : MobModule {

    internal var mobCap = Main.getConfig()?.getInt("optimization.mob-capacity", 100) ?: 100
    private val maxAttempt = Main.getConfig()?.getInt("optimization.safezone-calculation.mob-throttle", 3) ?: 3
    private val script = game.resource.gameScript
    private var mythicMobsActive = Main.mythicMobs
    private lateinit var apiHelper: Any
    private lateinit var spawnMethod: Method
    private lateinit var isMythicMobMethod: Method
    private lateinit var getMythicMobInstanceMethod: Method
    private lateinit var getEntityMethod: Method
    private lateinit var unregisterMethod: Method
    private lateinit var removeMethod: Method

    init { // Reflect MythicMobs API
        if (mythicMobsActive) {
            try {
                val mythicMobsClass = Class.forName("io.lumine.xikage.mythicmobs.MythicMobs")
                val apiHelperClass = Class.forName("io.lumine.xikage.mythicmobs.api.bukkit.BukkitAPIHelper")
                val activeMobClass = Class.forName("io.lumine.xikage.mythicmobs.mobs.ActiveMob")
                val abstractEntityClass = Class.forName("io.lumine.xikage.mythicmobs.adapters.AbstractEntity")
                val mythicMobs = mythicMobsClass.getMethod("inst").invoke(null)
                apiHelper = mythicMobsClass.getMethod("getAPIHelper").invoke(mythicMobs)
                spawnMethod = apiHelperClass.getMethod("spawnMythicMob", String::class.java, Location::class.java, Int::class.java)
                isMythicMobMethod = apiHelperClass.getMethod("isMythicMob", Entity::class.java)
                getMythicMobInstanceMethod = apiHelperClass.getMethod("getMythicMobInstance", Entity::class.java)
                getEntityMethod = activeMobClass.getMethod("getEntity")
                unregisterMethod = activeMobClass.getMethod("unregister")
                removeMethod = abstractEntityClass.getMethod("remove")
            } catch (e: Exception) {
                mythicMobsActive = false
                throw RuntimeException(e)
            }
        }
    }

    override fun getNamespacedKey(livingEntity: LivingEntity): NamespacedKey {
        return livingEntity.type.key
    }

    override fun getMobsInside(areaTag: String, callback: Consumer<List<Mob>>) {
        game.getWorldService().getEntitiesInside(areaTag, Consumer<List<Mob>> {
            callback.accept(it)

            script.printDebug(it.joinToString(
                            prefix = "Found ${it.size} mobs inside $areaTag: ",
                            limit = 10,
                            transform = { mob -> mob.type.name })
            )
        })
    }

    override fun setMobCapacity(max: Int) {
        mobCap = max
    }

    override fun spawnMob(type: String, spawnTag: String): CompletableFuture<List<Mob>> {
        val mapID = game.map.id
        val worldModule = game.getWorldService()
        val world = worldModule.getWorld()

        if (world.entityCount >= mobCap) {
            return CompletableFuture.completedFuture(emptyList())
        }

        val captures = ModuleService.getRelevantTag(game, spawnTag, TagMode.SPAWN, TagMode.AREA).getCaptures(mapID)
        val mobList = ArrayList<Mob>()
        val entityType = EntityType.valueOf(type.toUpperCase().replace(' ', '_'))
        val typeKey = entityType.key
        val tasks = LinkedList<CompletableFuture<Unit>>()

        if (!entityType.isSpawnable) {
            throw RuntimeException("Entity is not spawn-able: $typeKey")
        } else if (captures.isEmpty()) {
            throw FaultyConfiguration("Tag $spawnTag has no capture in map: $mapID")
        }

        captures.forEach { capture ->

            fun teleport(loc: Location?) {
                if (loc == null) {
                    script.print("Excessive attempt to spawn mob at: $spawnTag")
                    return
                }

                val entity = world.spawnEntity(loc, entityType)

                if (entity !is Mob) {
                    entity.remove()
                    throw IllegalArgumentException("This is not a Mob: $typeKey")
                }

                mobList.add(entity)
            }

            when (capture) {
                is AreaCapture -> {
                    tasks.add(capture.toLocation(world, maxAttempt).handle {
                        location, t ->

                        if (t != null) {
                            t.printStackTrace()
                        } else {
                            teleport(location)
                        }
                    })
                }
                is SpawnCapture -> {
                    teleport(capture.toLocation(world))
                }
                else -> error("Illegal tag mode.")
            }
        }

        return if (tasks.isNotEmpty()) {
            CompletableFuture.allOf(*tasks.toTypedArray()).thenCompose {
                script.printDebug("Spawned ${mobList.size} $typeKey")
                CompletableFuture.completedFuture(mobList.toList())
            }
        } else {
            script.printDebug("Spawned ${mobList.size} $typeKey")
            CompletableFuture.completedFuture(mobList.toList())
        }
    }

    override fun spawnMob(type: String, name: String, spawnTag: String): CompletableFuture<List<Mob>> {
        return spawnMob(type, spawnTag).thenApply {
            it.forEach { mob -> mob.customName = name }
            it
        }
    }

    override fun spawnMob(type: String, loot: LootTable, spawnTag: String): CompletableFuture<List<Mob>> {
        return spawnMob(type, spawnTag).thenApply {
            it.forEach { mob -> mob.setLootTable(loot, Random.nextLong()) }
            it
        }
    }

    override fun spawnMob(type: String, name: String, loot: LootTable, spawnTag: String): CompletableFuture<List<Mob>> {
        return spawnMob(type, spawnTag).thenApply {
            it.forEach { mob ->
                mob.customName = name
                mob.setLootTable(loot, Random.nextLong())
            }
            it
        }
    }

    override fun spawnMythicMob(name: String, level: Int, spawnTag: String): CompletableFuture<List<Mob>> {

        if (!mythicMobsActive) {
            throw DependencyNotFound("MythicMobs is required to spawn custom mobs.")
        }

        val mapID = game.map.id
        val world = game.getWorldService().getWorld()

        if (world.entityCount >= mobCap) {
            return CompletableFuture.completedFuture(emptyList())
        }

        val captures = ModuleService.getRelevantTag(game, spawnTag, TagMode.SPAWN, TagMode.AREA).getCaptures(mapID)
        val mobList = ArrayList<Mob>()
        var typeKey: NamespacedKey? = null
        val tasks = LinkedList<CompletableFuture<Unit>>()

        if (captures.isEmpty()) {
            throw FaultyConfiguration("Tag $spawnTag has no capture in map: $mapID")
        }

        captures.forEach { capture ->

            fun teleport(loc: Location?) {
                val entity: Entity

                if (loc == null) {
                    script.print("Excessive attempt to spawn mob at: $spawnTag")
                    return
                }

                try {
                    entity = spawnMethod.invoke(apiHelper, name, loc, level) as Entity
                    typeKey = entity.type.key

                    if (entity !is Mob) {
                        entity.remove()
                        throw IllegalArgumentException("This is not a Mob: $typeKey")
                    }

                    mobList.add(entity)
                } catch (e: InvocationTargetException) {
                    (e.cause as? Exception)?.let {
                        if (it::class.java.simpleName == "InvalidMobTypeException") {
                            throw IllegalArgumentException("Unable to identify MythicMob: $name")
                        }
                    }
                }
            }

            when (capture) {
                is AreaCapture -> {
                    tasks.add(capture.toLocation(world, maxAttempt).handle {
                        location, t ->

                        if (t != null) {
                            t.printStackTrace()
                        } else {
                            teleport(location)
                        }
                    })
                }
                is SpawnCapture -> {
                    teleport(capture.toLocation(world))
                }
                else -> error("Illegal tag mode.")
            }
        }

        return if (tasks.isNotEmpty()) {
            CompletableFuture.allOf(*tasks.toTypedArray()).thenCompose {
                script.printDebug("Spawned ${mobList.size} $typeKey")
                CompletableFuture.completedFuture(mobList.toList())
            }
        } else {
            script.printDebug("Spawned ${mobList.size} $typeKey")
            CompletableFuture.completedFuture(mobList.toList())
        }
    }

    override fun spawnNPC() {
        TODO("Not yet implemented")
    }

    override fun despawnEntities(type: EntityType): Int {
        val world = game.getWorldService().getWorld()
        var counter = 0

        world.entities.forEach {
            if (it.type == type) {
                it.remove()
                counter++
            }
        }

        return counter
    }

    override fun despawnMythicMobs(name: String): Int {

        if (!mythicMobsActive) {
            throw DependencyNotFound("MythicMobs is required to spawn custom mobs.")
        }

        val world = game.getWorldService().getWorld()
        var counter = 0

        world.livingEntities.forEach {
            if (isMythicMobMethod.invoke(apiHelper, it) as Boolean) {
                val activeMob = getMythicMobInstanceMethod.invoke(apiHelper, it)
                val abstractEntity = getEntityMethod.invoke(activeMob)

                removeMethod.invoke(abstractEntity)
                unregisterMethod.invoke(activeMob)
                counter++
            }
        }

        return counter
    }

}