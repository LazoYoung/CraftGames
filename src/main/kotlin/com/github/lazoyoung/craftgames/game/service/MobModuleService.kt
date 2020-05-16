package com.github.lazoyoung.craftgames.game.service

import com.denizenscript.denizen.npc.traits.AssignmentTrait
import com.github.lazoyoung.craftgames.Main
import com.github.lazoyoung.craftgames.api.module.MobModule
import com.github.lazoyoung.craftgames.coordtag.capture.AreaCapture
import com.github.lazoyoung.craftgames.coordtag.capture.SpawnCapture
import com.github.lazoyoung.craftgames.coordtag.tag.TagMode
import com.github.lazoyoung.craftgames.game.Game
import com.github.lazoyoung.craftgames.internal.exception.DependencyNotFound
import com.github.lazoyoung.craftgames.internal.exception.FaultyConfiguration
import com.github.lazoyoung.craftgames.internal.util.DependencyUtil
import com.nisovin.shopkeepers.api.ShopkeepersAPI
import com.nisovin.shopkeepers.api.shopkeeper.admin.regular.RegularAdminShopkeeper
import net.citizensnpcs.api.CitizensAPI
import net.citizensnpcs.api.npc.NPC
import net.citizensnpcs.api.npc.NPCRegistry
import net.citizensnpcs.trait.SkinTrait
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.NamespacedKey
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

    override fun spawnNPC(name: String, type: EntityType, assignment: String?, tagName: String): CompletableFuture<Int> {
        return spawnNPC(type, name, null, assignment, tagName)
    }

    override fun spawnPlayerNPC(name: String, skinURL: String?, assignment: String?, tagName: String): CompletableFuture<Int> {
        return spawnNPC(EntityType.PLAYER, name, skinURL, assignment, tagName)
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

    private fun spawnNPC(
            type: EntityType,
            name: String,
            skinURL: String?,
            assignment: String?,
            tagName: String
    ): CompletableFuture<Int> {
        if (!DependencyUtil.CITIZENS.isLoaded()) {
            throw DependencyNotFound("Citizens is required to spawn NPC.")
        }

        val worldService = game.module.getWorldModule() as WorldModuleService
        val world = worldService.getWorld()
        val mapID = worldService.getMapID()
        val registry = CitizensAPI.getNPCRegistry()
        val tag = ModuleService.getRelevantTag(game, tagName, TagMode.AREA, TagMode.SPAWN)
        val npcList = ArrayList<NPC>()
        val taskList = LinkedList<CompletableFuture<Unit>>()
        val future = CompletableFuture<Int>()

        if (tag.getCaptures(mapID).isEmpty()) {
            throw FaultyConfiguration("Tag $tagName has no capture in map: $mapID")
        }

        fun computeTask(future: CompletableFuture<NPC>): CompletableFuture<Unit> {
            return future.handleAsync { npc, t ->

                if (t != null) {
                    this.script.print("Failed to spawn NPC. (${type.key})")
                    this.script.writeStackTrace(t)
                } else {
                    Bukkit.getScheduler().runTask(Main.instance, Runnable {
                        npcList.add(npc)
                    })
                }
                return@handleAsync
            }
        }

        when (tag.mode) {
            TagMode.SPAWN -> {
                tag.getCaptures(mapID).forEach {
                    it as SpawnCapture

                    val locFuture = CompletableFuture.completedFuture(it.toLocation(world))
                    taskList.add(computeTask(handleNPC(registry, type, name, locFuture, skinURL, assignment)))
                }
            }
            TagMode.AREA -> {
                tag.getCaptures(mapID).forEach {
                    it as AreaCapture

                    val locFuture = it.toLocation(world, maxAttempt)
                    taskList.add(computeTask(handleNPC(registry, type, name, locFuture, skinURL, assignment)))
                }
            }
            else -> {
                error("Illegal tag mode.")
            }
        }

        CompletableFuture.allOf(*taskList.toTypedArray()).whenCompleteAsync {
            _, t ->

            if (t != null) {
                this.script.print("Failed to spawn NPC. (${type.key})")
                this.script.writeStackTrace(t)
                future.completeExceptionally(t)
            } else {
                this.script.printDebug("Spawned ${npcList.size} NPCs. (${type.key})")
                future.complete(npcList.size)
            }
        }

        return future
    }

    private fun handleNPC(
            registry: NPCRegistry,
            type: EntityType,
            name: String,
            locationFuture: CompletableFuture<Location>,
            skinURL: String?,
            assignment: String?
    ): CompletableFuture<NPC> {
        val future = CompletableFuture<NPC>()
        val scheduler = Bukkit.getScheduler()

        if (skinURL != null) {
            val skinFuture = fetchSkin(skinURL)

            CompletableFuture.allOf(skinFuture, locationFuture).handleAsync {
                _, t ->

                if (t != null) {
                    future.completeExceptionally(t)
                } else {
                    scheduler.runTask(Main.instance, Runnable {
                        val npc = registry.createNPC(type, name)
                        val skinTrait = npc.getTrait(SkinTrait::class.java)
                        val skin = skinFuture.join()
                        val location = locationFuture.join()

                        if (assignment != null) {
                            if (!DependencyUtil.DENIZEN.isLoaded()) {
                                this.script.print("Failed to inject script to NPC. Denizen is not installed.")
                            } else {
                                npc.getTrait(AssignmentTrait::class.java).setAssignment(assignment, null)
                            }
                        }

                        skinTrait.clearTexture()
                        skinTrait.setSkinPersistent(skin.uuid, skin.signature, skin.value)
                        npc.spawn(location)
                        npcList.add(npc.uniqueId)
                        future.complete(npc)
                    })
                }
                return@handleAsync
            }
        } else {
            locationFuture.handleAsync {
                location, t ->

                if (t != null) {
                    future.completeExceptionally(t)
                } else {
                    scheduler.runTask(Main.instance, Runnable {
                        val npc = registry.createNPC(type, name)

                        npc.spawn(location)
                        future.complete(npc)
                    })
                }
                return@handleAsync
            }
        }

        return future
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