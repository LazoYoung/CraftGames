package com.github.lazoyoung.craftgames.game.module

import com.github.lazoyoung.craftgames.Main
import com.github.lazoyoung.craftgames.api.module.MobModule
import com.github.lazoyoung.craftgames.coordtag.tag.TagMode
import com.github.lazoyoung.craftgames.game.Game
import com.github.lazoyoung.craftgames.internal.exception.DependencyNotFound
import com.github.lazoyoung.craftgames.internal.exception.FaultyConfiguration
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.entity.Entity
import org.bukkit.entity.EntityType
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Mob
import org.bukkit.loot.LootTable
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.util.function.Consumer
import kotlin.random.Random

@Suppress("DuplicatedCode")
class MobModuleService internal constructor(private val game: Game) : MobModule {

    internal var mobCap = Main.getConfig()?.getInt("optimization.mob-capacity", 100) ?: 100
    private val script = game.resource.script
    private var mythicMobsActive = false
    private lateinit var apiHelper: Any
    private lateinit var spawnMethod: Method
    private lateinit var isMythicMobMethod: Method
    private lateinit var getMythicMobInstanceMethod: Method
    private lateinit var getEntityMethod: Method
    private lateinit var unregisterMethod: Method
    private lateinit var removeMethod: Method

    init { // Reflect MythicMobs API
        if (Bukkit.getPluginManager().getPlugin("MythicMobs") != null) {
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
                mythicMobsActive = true
            } catch (e: Exception) {
                throw RuntimeException(e)
            }
        }
    }

    override fun getNamespacedKey(livingEntity: LivingEntity): NamespacedKey {
        return livingEntity.type.key
    }

    override fun getMobsInside(areaTag: String, callback: Consumer<List<Mob>>) {
        Module.getWorldModule(game).getEntitiesInside(areaTag, Consumer<List<Mob>> {
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

    override fun spawnMob(type: String, spawnTag: String): List<Mob> {
        val mapID = game.map.id
        val worldModule = Module.getWorldModule(game)
        val world = worldModule.getWorld()

        if (world.entityCount >= mobCap) {
            return emptyList()
        }

        val captures = Module.getRelevantTag(game, spawnTag, TagMode.SPAWN, TagMode.AREA).getCaptures(mapID)
        val mobList = ArrayList<Mob>()
        var typeKey: NamespacedKey? = null

        if (captures.isEmpty()) {
            throw FaultyConfiguration("Tag $spawnTag has no capture in map: $mapID")
        }

        captures.forEach { capture ->
            val entity: Entity
            val entityType = EntityType.valueOf(type.toUpperCase().replace(' ', '_'))
            val loc = capture.toLocation(world)
            typeKey = entityType.key

            if (!entityType.isSpawnable) {
                throw RuntimeException("Entity is not spawn-able: $typeKey")
            }

            entity = world.spawnEntity(loc, entityType)

            if (entity !is Mob) {
                entity.remove()
                throw IllegalArgumentException("This is not a Mob: $typeKey")
            }

            mobList.add(entity)
        }

        script.printDebug("Spawned ${mobList.size} $typeKey")
        return mobList
    }

    override fun spawnMob(type: String, name: String, spawnTag: String): List<Mob> {
        val mobList = spawnMob(type, spawnTag)

        mobList.forEach {
            it.customName = name
        }
        return mobList
    }

    override fun spawnMob(type: String, loot: LootTable, spawnTag: String): List<Mob> {
        val mobList = spawnMob(type, spawnTag)

        mobList.forEach { it.setLootTable(loot, Random.nextLong()) }
        return mobList
    }

    override fun spawnMob(type: String, name: String, loot: LootTable, spawnTag: String): List<Mob> {
        val mobList = spawnMob(type, spawnTag)

        mobList.forEach {
            it.customName = name
            it.setLootTable(loot, Random.nextLong())
        }
        return mobList
    }

    override fun spawnMythicMob(name: String, level: Int, spawnTag: String): List<Mob> {

        if (!mythicMobsActive) {
            throw DependencyNotFound("MythicMobs is required to spawn custom mobs.")
        }

        val mapID = game.map.id
        val worldModule = Module.getWorldModule(game)
        val world = worldModule.getWorld()

        if (world.entityCount >= mobCap) {
            return emptyList()
        }

        val captures = Module.getRelevantTag(game, spawnTag, TagMode.SPAWN, TagMode.AREA).getCaptures(mapID)
        val mobList = ArrayList<Mob>()
        var typeKey: NamespacedKey? = null

        if (captures.isEmpty()) {
            throw FaultyConfiguration("Tag $spawnTag has no capture in map: $mapID")
        }

        captures.forEach { capture ->
            val loc = capture.toLocation(world)
            val entity: Entity

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

        script.printDebug("Spawned ${mobList.size} $typeKey")
        return mobList
    }

    override fun despawnEntities(type: EntityType): Int {
        val world = Module.getWorldModule(game).getWorld()
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

        val world = Module.getWorldModule(game).getWorld()
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