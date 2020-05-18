package com.github.lazoyoung.craftgames.impl.game.service

import com.denizenscript.denizen.npc.traits.AssignmentTrait
import com.github.lazoyoung.craftgames.impl.Main
import com.github.lazoyoung.craftgames.api.GameShopkeeper
import com.github.lazoyoung.craftgames.api.coordtag.capture.AreaCapture
import com.github.lazoyoung.craftgames.api.coordtag.capture.SpawnCapture
import com.github.lazoyoung.craftgames.api.coordtag.tag.CoordTag
import com.github.lazoyoung.craftgames.api.coordtag.tag.TagMode
import com.github.lazoyoung.craftgames.api.module.MobModule
import com.github.lazoyoung.craftgames.impl.game.Game
import com.github.lazoyoung.craftgames.impl.exception.DependencyNotFound
import com.github.lazoyoung.craftgames.impl.exception.FaultyConfiguration
import com.github.lazoyoung.craftgames.impl.util.DependencyUtil
import com.nisovin.shopkeepers.api.ShopkeepersAPI
import com.nisovin.shopkeepers.api.shopkeeper.admin.regular.RegularAdminShopkeeper
import net.citizensnpcs.api.CitizensAPI
import net.citizensnpcs.trait.SkinTrait
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.World
import org.bukkit.entity.Entity
import org.bukkit.entity.EntityType
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Mob
import org.bukkit.loot.LootTable
import org.bukkit.scheduler.BukkitRunnable
import org.json.simple.JSONObject
import org.json.simple.parser.JSONParser
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer
import kotlin.collections.ArrayList
import kotlin.random.Random

class MobModuleService internal constructor(private val game: Game) : MobModule, Service {

    private data class SkinData(val uuid: String, val value: String, val signature: String)

    internal var mobCap = Main.getConfig()?.getInt("optimization.mob-capacity", 100) ?: 100
    private val maxAttempt = Main.getConfig()?.getInt("optimization.safezone-calculation.mob-throttle", 3) ?: 3
    private val script = game.resource.mainScript
    private lateinit var apiHelper: Any
    private lateinit var spawnMethod: Method
    private lateinit var isMythicMobMethod: Method
    private lateinit var getMythicMobInstanceMethod: Method
    private lateinit var getEntityMethod: Method
    private lateinit var unregisterMethod: Method
    private lateinit var removeMethod: Method
    internal val shopkeeperList = LinkedList<UUID>()
    private val npcList = LinkedList<UUID>()

    override fun getNamespacedKey(livingEntity: LivingEntity): NamespacedKey {
        return livingEntity.type.key
    }

    override fun getMobsInside(areaTag: String, callback: Consumer<List<Mob>>) {
        this.getMobsInside(ModuleService.getRelevantTag(game, areaTag, TagMode.AREA), callback)
    }

    override fun getMobsInside(areaTag: CoordTag, callback: Consumer<List<Mob>>) {
        game.getWorldService().getEntitiesInside(areaTag, Consumer<List<Mob>> {
            callback.accept(it)

            script.printDebug(it.joinToString(
                    prefix = "Found ${it.size} mobs inside $areaTag: ",
                    limit = 10,
                    transform = { mob -> mob.type.name })
            )
        })
    }

    // Dependency-bound functions should be separated into modules.
    override fun getShopkeeper(entity: Entity): GameShopkeeper? {
        if (!DependencyUtil.SHOP_KEEPER.isLoaded()) {
            throw DependencyNotFound("Shopkeepers is not installed.")
        }

        val shopkeeper = ShopkeepersAPI.getShopkeeperRegistry().getShopkeeperByEntity(entity)

        requireNotNull(shopkeeper) {
            "This entity is not a Shopkeeper."
        }
        require(shopkeeper is RegularAdminShopkeeper) {
            "This entity is not a RegularAdminShopkeeper."
        }

        return GameShopkeeper(game.resource.layout, shopkeeper)
    }

    override fun setMobCapacity(max: Int) {
        mobCap = max
    }

    override fun spawnMob(type: String, name: String?, loot: LootTable?, tagName: String): CompletableFuture<Int> {
        val mapID = game.map.id
        val worldModule = game.getWorldService()
        val world = worldModule.getWorld()

        if (world.entityCount >= mobCap) {
            return CompletableFuture.completedFuture(0)
        }

        val captures = ModuleService.getRelevantTag(game, tagName, TagMode.SPAWN, TagMode.AREA).getCaptures(mapID)
        val mobList = ArrayList<Mob>()
        val entityType = EntityType.valueOf(type.toUpperCase().replace(' ', '_'))
        val typeKey = entityType.key
        val tasks = LinkedList<CompletableFuture<Unit>>()

        if (!entityType.isSpawnable) {
            throw RuntimeException("Entity is not spawn-able: $typeKey")
        } else if (captures.isEmpty()) {
            throw FaultyConfiguration("Tag $tagName has no capture in map: $mapID")
        }

        captures.forEach { capture ->

            fun spawnMobAt(loc: Location?) {
                if (loc == null) {
                    script.print("Excessive attempt to spawn mob at: $tagName")
                    return
                }

                val entity = world.spawnEntity(loc, entityType)

                if (entity !is Mob) {
                    entity.remove()
                    throw IllegalArgumentException("This is not a Mob: $typeKey")
                } else {
                    name?.let { entity.customName = it }
                    loot?.let { entity.setLootTable(it, Random.nextLong()) }
                    mobList.add(entity)
                }
            }

            when (capture) {
                is AreaCapture -> {
                    tasks.add(capture.toLocation(world, maxAttempt).handleAsync { location, t ->

                        if (t != null) {
                            t.printStackTrace()
                        } else {
                            Bukkit.getScheduler().runTask(Main.instance, Runnable {
                                spawnMobAt(location)
                            })
                        }
                        return@handleAsync
                    })
                }
                is SpawnCapture -> {
                    spawnMobAt(capture.toLocation(world))
                }
                else -> error("Illegal tag mode.")
            }
        }

        return if (tasks.isNotEmpty()) {
            CompletableFuture.allOf(*tasks.toTypedArray()).thenCompose {
                script.printDebug("Spawned ${mobList.size} $typeKey")
                CompletableFuture.completedFuture(mobList.size)
            }
        } else {
            script.printDebug("Spawned ${mobList.size} $typeKey")
            CompletableFuture.completedFuture(mobList.size)
        }
    }

    override fun spawnMob(type: String, name: String?, loot: LootTable?, location: Location): Mob {
        val world = game.getWorldService().getWorld()
        val locFuture = CompletableFuture.completedFuture(location)

        return this.spawnMob0(type, name, loot, locFuture, world)
    }

    override fun spawnMob(type: String, name: String?, loot: LootTable?, tag: CoordTag): Array<Mob> {
        val world = game.getWorldService().getWorld()
        val mobList = LinkedList<Mob>()

        this.getTagLocations(tag).forEach {
            mobList.add(this.spawnMob0(type, name, loot, it, world))
        }

        return mobList.toTypedArray()
    }

    override fun spawnMythicMob(name: String, level: Int, tagName: String): CompletableFuture<Int> {
        if (!DependencyUtil.MYTHIC_MOBS.isLoaded()) {
            throw DependencyNotFound("MythicMobs is required to spawn custom mobs.")
        }

        val mapID = game.map.id
        val world = game.getWorldService().getWorld()

        if (world.entityCount >= mobCap) {
            return CompletableFuture.completedFuture(0)
        }

        val captures = ModuleService.getRelevantTag(game, tagName, TagMode.SPAWN, TagMode.AREA).getCaptures(mapID)
        val mobList = ArrayList<Mob>()
        var typeKey: NamespacedKey? = null
        val tasks = LinkedList<CompletableFuture<Unit>>()
        val future = CompletableFuture<Int>()

        if (captures.isEmpty()) {
            throw FaultyConfiguration("Tag $tagName has no capture in map: $mapID")
        }

        fun teleport(loc: Location?) {
            val entity: Entity

            if (loc == null) {
                script.print("Excessive attempt to spawn mob at: $tagName")
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

        captures.forEach { capture ->
            when (capture) {
                is AreaCapture -> {
                    tasks.add(capture.toLocation(world, maxAttempt).exceptionally {
                        return@exceptionally null
                    }.thenCompose { location ->
                        Bukkit.getScheduler().runTask(Main.instance, Runnable {
                            teleport(location)
                        })
                        return@thenCompose null
                    })
                }
                is SpawnCapture -> {
                    teleport(capture.toLocation(world))
                }
                else -> error("Illegal tag mode.")
            }
        }

        if (tasks.isNotEmpty()) {
            CompletableFuture.allOf(*tasks.toTypedArray()).whenCompleteAsync {
                _, t ->

                if (t != null) {
                    script.print("Failed to spawn mob. ($typeKey)")
                    future.completeExceptionally(t)
                } else {
                    script.printDebug("Spawned ${mobList.size} $typeKey")
                    future.complete(mobList.size)
                }
            }

        } else {
            script.printDebug("Spawned ${mobList.size} $typeKey")
            future.complete(mobList.size)
        }

        return future
    }

    override fun spawnMythicMob(name: String, level: Int, location: Location): Entity {
        if (!DependencyUtil.MYTHIC_MOBS.isLoaded()) {
            throw DependencyNotFound("MythicMobs is required to spawn custom mobs.")
        }

        val world = game.getWorldService().getWorld()
        val locFuture = CompletableFuture.completedFuture(location)

        return spawnMythicMob0(name, locFuture, level, world)
    }

    override fun spawnMythicMob(name: String, level: Int, tag: CoordTag): Array<Entity> {
        if (!DependencyUtil.MYTHIC_MOBS.isLoaded()) {
            throw DependencyNotFound("MythicMobs is required to spawn custom mobs.")
        }

        val world = game.getWorldService().getWorld()
        val entityList = LinkedList<Entity>()

        this.getTagLocations(tag).forEach {
            entityList.add(this.spawnMythicMob0(name, it, level, world))
        }

        return entityList.toTypedArray()
    }

    override fun spawnNPC(name: String, type: EntityType, assignment: String?, tagName: String): CompletableFuture<Int> {
        error("Deprecated function.")
    }

    override fun spawnNPC(name: String, type: EntityType, assignment: String?, location: Location): Entity {
        if (!DependencyUtil.CITIZENS.isLoaded()) {
            throw DependencyNotFound("Citizens is required to spawn NPC.")
        }

        val locFuture = CompletableFuture.completedFuture(location)
        return spawnNPC0(name, type, locFuture, null, assignment)
    }

    override fun spawnNPC(name: String, type: EntityType, assignment: String?, tag: CoordTag): Array<Entity> {
        if (!DependencyUtil.CITIZENS.isLoaded()) {
            throw DependencyNotFound("Citizens is required to spawn NPC.")
        }

        val entityList = LinkedList<Entity>()

        this.getTagLocations(tag).forEach {
            entityList.add(this.spawnNPC0(name, type, it, null, assignment))
        }

        return entityList.toTypedArray()
    }

    override fun spawnPlayerNPC(name: String, skinURL: String?, assignment: String?, tagName: String): CompletableFuture<Int> {
        error("Deprecated function.")
    }

    override fun spawnPlayerNPC(name: String, skinURL: String?, assignment: String?, location: Location): Entity {
        if (!DependencyUtil.CITIZENS.isLoaded()) {
            throw DependencyNotFound("Citizens is required to spawn NPC.")
        }

        val locFuture = CompletableFuture.completedFuture(location)
        return spawnNPC0(name, EntityType.PLAYER, locFuture, skinURL, assignment)
    }

    override fun spawnPlayerNPC(name: String, skinURL: String?, assignment: String?, tag: CoordTag): Array<Entity> {
        if (!DependencyUtil.CITIZENS.isLoaded()) {
            throw DependencyNotFound("Citizens is required to spawn NPC.")
        }

        val entityList = LinkedList<Entity>()

        this.getTagLocations(tag).forEach {
            entityList.add(this.spawnNPC0(name, EntityType.PLAYER, it, skinURL, assignment))
        }

        return entityList.toTypedArray()
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

        if (!DependencyUtil.MYTHIC_MOBS.isLoaded()) {
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

    override fun start() {
        if (DependencyUtil.MYTHIC_MOBS.isLoaded()) {
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
                throw RuntimeException(e)
            }
        }
    }

    /*
     * In editor-mode, Each Citizen shopkeeper gets saved before destroying the base entity.
     * Therefore, Shopkeeper save process must precede to Citizen termination process.
     */
    override fun terminate() {

        // Shopkeeper save process
        if (game.editMode && DependencyUtil.SHOP_KEEPER.isLoaded()) {
            while (shopkeeperList.isNotEmpty()) {
                val registry = ShopkeepersAPI.getShopkeeperRegistry()
                val uid = shopkeeperList.pop()

                registry.getShopkeeperByUniqueId(uid)?.let {
                    if (it is RegularAdminShopkeeper) {
                        game.resource.saveShopkeeper(it)
                    }

                    it.delete()
                }
            }
        }

        // Citizen termination process
        if (DependencyUtil.CITIZENS.isLoaded()) {
            npcList.iterator().let {
                while (it.hasNext()) {
                    val registry = CitizensAPI.getNPCRegistry()
                    val npc = registry.getByUniqueId(it.next())
                    npc?.destroy()
                    it.remove()
                }
            }
        }
    }

    private fun getTagLocations(tag: CoordTag): List<CompletableFuture<Location>> {
        val locFutures = LinkedList<CompletableFuture<Location>>()
        val world = game.getWorldService().getWorld()
        val mapID = game.getWorldService().getMapID()
        val captures = tag.getCaptures(mapID)

        if (captures.isEmpty()) {
            throw FaultyConfiguration("Tag ${tag.name} has no capture in map: $mapID")
        }

        when (val mode = tag.mode) {
            TagMode.SPAWN -> {
                captures.filterIsInstance(SpawnCapture::class.java).forEach {
                    val loc = it.toLocation(world)
                    val future = CompletableFuture.completedFuture(loc)

                    locFutures.add(future)
                }
            }
            TagMode.AREA -> {
                captures.filterIsInstance(AreaCapture::class.java).forEach {
                    val future = it.toLocation(world, maxAttempt)

                    locFutures.add(future)
                }
            }
            else -> throw IllegalArgumentException("Cannot spawn with ${mode.label} tag.")
        }

        return locFutures
    }

    private fun spawnMob0(type: String, name: String?, loot: LootTable?, locFuture: CompletableFuture<Location>, world: World): Mob {
        val entityType = EntityType.valueOf(type.toUpperCase().replace(' ', '_'))
        val typeKey = entityType.key

        require(entityType.isSpawnable) {
            "This is not spawn-able: $typeKey"
        }

        val entity = world.spawnEntity(world.spawnLocation, entityType)

        require(entity is Mob) {
            entity.remove()
            "This is not a Mob: $typeKey"
        }

        name?.let { entity.customName = it }
        loot?.let { entity.setLootTable(it, Random.nextLong()) }
        locFuture.whenCompleteAsync { location, t ->
            if (t != null) {
                script.print("Failed to resolve spawnpoint for mob: $name")
                script.writeStackTrace(t)
            } else {
                Bukkit.getScheduler().runTask(Main.instance, Runnable {
                    entity.teleport(location)
                })
            }
        }
        return entity
    }

    private fun spawnMythicMob0(name: String, locFuture: CompletableFuture<Location>, level: Int, world: World): Entity {
        try {
            val entity = spawnMethod.invoke(apiHelper, name, world.spawnLocation, level) as Entity

            require(entity is Mob) {
                entity.remove()
                "This is not a Mob: ${entity.type.key}"
            }
            locFuture.whenCompleteAsync { location, t ->
                if (t != null) {
                    script.print("Failed to resolve spawnpoint for mob: $name")
                    script.writeStackTrace(t)
                } else {
                    Bukkit.getScheduler().runTask(Main.instance, Runnable {
                        entity.teleport(location)
                    })
                }
            }

            return entity
        } catch (e: InvocationTargetException) {
            if (e.cause?.javaClass?.simpleName == "InvalidMobTypeException") {
                throw IllegalArgumentException("Unable to identify MythicMob: $name")
            }

            throw ReflectiveOperationException(e.cause)
        }
    }

    private fun spawnNPC0(
            name: String,
            type: EntityType,
            locFuture: CompletableFuture<Location>,
            skinURL: String?,
            assignment: String?
    ): Entity {
        val scheduler = Bukkit.getScheduler()
        val npc = CitizensAPI.getNPCRegistry().createNPC(type, name)

        if (assignment != null) {
            if (!DependencyUtil.DENIZEN.isLoaded()) {
                this.script.print("Failed to inject NPC script. Denizen is not installed.")
            } else {
                npc.getTrait(AssignmentTrait::class.java).setAssignment(assignment, null)
            }
        }

        if (skinURL != null) {
            fetchSkin(skinURL).whenCompleteAsync { skin, t ->
                if (t != null) {
                    script.print("Failed to fetch skin for NPC: $name, from URL: $skinURL")
                    script.writeStackTrace(t)
                } else {
                    scheduler.runTask(Main.instance, Runnable {
                        val skinTrait = npc.getTrait(SkinTrait::class.java)

                        skinTrait.clearTexture()
                        skinTrait.setSkinPersistent(skin.uuid, skin.signature, skin.value)
                    })
                }
            }
        }

        locFuture.whenCompleteAsync { location, t ->
            if (t != null) {
                script.print("Failed to resolve spawnpoint for NPC: $name")
                script.writeStackTrace(t)
            } else {
                Bukkit.getScheduler().runTask(Main.instance, Runnable {
                    npc.spawn(location)
                    npcList.add(npc.uniqueId)
                })
            }
        }

        return npc.entity
    }

    private fun fetchSkin(skinURL: String): CompletableFuture<SkinData> {
        val future = CompletableFuture<SkinData>()

        object : BukkitRunnable() {
            override fun run() {
                try {
                    val genURL = URL("https://api.mineskin.org/generate/url")
                    val con = genURL.openConnection() as HttpURLConnection
                    con.requestMethod = "POST"
                    con.doOutput = true
                    con.connectTimeout = 1000
                    con.readTimeout = 10000
                    DataOutputStream(con.outputStream).use {
                        it.writeBytes("url=".plus(URLEncoder.encode(skinURL, "UTF-8")))
                    }
                    BufferedReader(InputStreamReader(con.inputStream)).use {
                        val response = JSONParser().parse(it) as JSONObject
                        val data = response["data"] as JSONObject
                        val texture = data["texture"] as JSONObject
                        val uuid = data["uuid"] as String
                        val value = texture["value"] as String
                        val signature = texture["signature"] as String

                        future.complete(SkinData(uuid, value, signature))
                    }
                    con.disconnect()
                } catch (e: Exception) {
                    e.printStackTrace()
                    future.completeExceptionally(e)
                }
            }
        }.runTaskAsynchronously(Main.instance)
        return future
    }

}