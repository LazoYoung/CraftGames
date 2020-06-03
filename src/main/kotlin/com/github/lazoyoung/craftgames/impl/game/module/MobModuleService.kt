package com.github.lazoyoung.craftgames.impl.game.module

import com.denizenscript.denizen.npc.traits.AssignmentTrait
import com.github.lazoyoung.craftgames.api.module.MobModule
import com.github.lazoyoung.craftgames.api.shopkeepers.GameShopkeeper
import com.github.lazoyoung.craftgames.api.tag.coordinate.CoordTag
import com.github.lazoyoung.craftgames.api.tag.coordinate.TagMode
import com.github.lazoyoung.craftgames.impl.Main
import com.github.lazoyoung.craftgames.impl.exception.DependencyNotFound
import com.github.lazoyoung.craftgames.impl.exception.FaultyConfiguration
import com.github.lazoyoung.craftgames.impl.game.Game
import com.github.lazoyoung.craftgames.impl.tag.coordinate.AreaCaptureService
import com.github.lazoyoung.craftgames.impl.tag.coordinate.SpawnCaptureService
import com.github.lazoyoung.craftgames.impl.util.DependencyUtil
import com.nisovin.shopkeepers.api.ShopkeepersAPI
import com.nisovin.shopkeepers.api.shopkeeper.DefaultShopTypes
import com.nisovin.shopkeepers.api.shopkeeper.Shopkeeper
import com.nisovin.shopkeepers.api.shopkeeper.admin.AdminShopCreationData
import com.nisovin.shopkeepers.api.shopkeeper.admin.regular.RegularAdminShopkeeper
import com.nisovin.shopkeepers.api.shopobjects.DefaultShopObjectTypes
import com.nisovin.shopkeepers.shopobjects.citizens.SKCitizensShopObject
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
import org.bukkit.event.player.PlayerTeleportEvent
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
    private val npcList = LinkedList<UUID>()

    override fun getNamespacedKey(livingEntity: LivingEntity): NamespacedKey {
        return livingEntity.type.key
    }

    override fun getMobsInside(areaTag: CoordTag, callback: Consumer<List<Mob>>) {
        error("This is deprecated.")
    }

    // Dependency-bound functions should be separated into modules.
    override fun getShopkeeper(entity: Entity): GameShopkeeper {
        if (!DependencyUtil.SHOP_KEEPER.isLoaded()) {
            throw DependencyNotFound("Shopkeepers is not installed.")
        }

        val registry = ShopkeepersAPI.getShopkeeperRegistry()
        val shopkeeper = registry.getShopkeeperByEntity(entity)

        requireNotNull(shopkeeper) {
            "This entity is not a Shopkeeper."
        }
        require(shopkeeper is RegularAdminShopkeeper) {
            "This entity is not a RegularAdminShopkeeper."
        }

        return GameShopkeeper(game.resource.layout, shopkeeper)
    }

    override fun makeShopkeeper(entity: Entity): GameShopkeeper {
        if (!DependencyUtil.SHOP_KEEPER.isLoaded()) {
            throw DependencyNotFound("Shopkeepers is not installed.")
        }

        val registry = ShopkeepersAPI.getShopkeeperRegistry()
        val shopType = DefaultShopTypes.ADMIN()
        val data: AdminShopCreationData

        if (DependencyUtil.CITIZENS.isLoaded() && CitizensAPI.getNPCRegistry().isNPC(entity)) {
            val npc = CitizensAPI.getNPCRegistry().getNPC(entity)

            data = AdminShopCreationData.create(
                    null, shopType, DefaultShopObjectTypes.CITIZEN(),
                    npc.storedLocation, null
            )
            data.setValue(SKCitizensShopObject.CREATION_DATA_NPC_UUID_KEY, npc.uniqueId)
        } else {
            val objType = DefaultShopObjectTypes.LIVING().get(entity.type)

            data = AdminShopCreationData.create(
                    null, shopType, objType, entity.location, null
            )
        }

        val shopkeeper = registry.createShopkeeper(data) as RegularAdminShopkeeper

        return GameShopkeeper(game.resource.layout, shopkeeper)
    }

    override fun spawnMob(type: String, name: String?, loot: LootTable?, location: Location): Mob {
        val world = game.getWorldService().getWorld()
        val locFuture = CompletableFuture.completedFuture(location)

        return spawnMob0(type, name, loot, locFuture, world)
    }

    override fun spawnMob(type: String, name: String?, loot: LootTable?, tag: CoordTag): Mob {
        val world = game.getWorldService().getWorld()
        val locFuture = getRandomLocation(tag)

        return spawnMob0(type, name, loot, locFuture, world)
    }

    override fun spawnMythicMob(name: String, level: Int, location: Location): Entity {
        if (!DependencyUtil.MYTHIC_MOBS.isLoaded()) {
            throw DependencyNotFound("MythicMobs is required to spawn custom mobs.")
        }

        val world = game.getWorldService().getWorld()
        val locFuture = CompletableFuture.completedFuture(location)

        return spawnMythicMob0(name, locFuture, level, world)
    }

    override fun spawnMythicMob(name: String, level: Int, tag: CoordTag): Entity {
        if (!DependencyUtil.MYTHIC_MOBS.isLoaded()) {
            throw DependencyNotFound("MythicMobs is required to spawn custom mobs.")
        }

        val world = game.getWorldService().getWorld()
        val locFuture = getRandomLocation(tag)

        return spawnMythicMob0(name, locFuture, level, world)
    }

    override fun spawnNPC(name: String, type: EntityType, assignment: String?, location: Location): Entity {
        if (!DependencyUtil.CITIZENS.isLoaded()) {
            throw DependencyNotFound("Citizens is required to spawn NPC.")
        }

        val world = game.getWorldService().getWorld()
        val locFuture = CompletableFuture.completedFuture(location)

        return spawnNPC0(name, type, locFuture, null, assignment, world)
    }

    override fun spawnNPC(name: String, type: EntityType, assignment: String?, tag: CoordTag): Entity {
        if (!DependencyUtil.CITIZENS.isLoaded()) {
            throw DependencyNotFound("Citizens is required to spawn NPC.")
        }

        val world = game.getWorldService().getWorld()
        val locFuture = getRandomLocation(tag)

        return spawnNPC0(name, type, locFuture, null, assignment, world)
    }

    override fun spawnPlayerNPC(name: String, skinURL: String?, assignment: String?, location: Location): Entity {
        if (!DependencyUtil.CITIZENS.isLoaded()) {
            throw DependencyNotFound("Citizens is required to spawn NPC.")
        }

        val world = game.getWorldService().getWorld()
        val locFuture = CompletableFuture.completedFuture(location)

        return spawnNPC0(name, EntityType.PLAYER, locFuture, skinURL, assignment, world)
    }

    override fun spawnPlayerNPC(name: String, skinURL: String?, assignment: String?, tag: CoordTag): Entity {
        if (!DependencyUtil.CITIZENS.isLoaded()) {
            throw DependencyNotFound("Citizens is required to spawn NPC.")
        }

        val world = game.getWorldService().getWorld()
        val locFuture = getRandomLocation(tag)

        return spawnNPC0(name, EntityType.PLAYER, locFuture, skinURL, assignment, world)
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

    override fun terminate() {

        // Shopkeeper deletion
        if (DependencyUtil.SHOP_KEEPER.isLoaded()) {
            val registry = ShopkeepersAPI.getShopkeeperRegistry()
            val list = LinkedList(registry.getShopkeepersInWorld(game.map.worldName))

            list.forEach(Shopkeeper::delete)
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

    private fun getRandomLocation(tag: CoordTag): CompletableFuture<Location> {
        val world = game.getWorldService().getWorld()
        val mapID = game.getWorldService().getMapID()
        val captures = tag.getCaptures(mapID)

        if (captures.isEmpty()) {
            throw FaultyConfiguration("Tag ${tag.name} has no capture in map: $mapID")
        }

        return when (val mode = tag.mode) {
            TagMode.SPAWN -> {
                val capture = captures.filterIsInstance(SpawnCaptureService::class.java).random()
                CompletableFuture.completedFuture(capture.toLocation(world))
            }
            TagMode.AREA -> {
                val capture = captures.filterIsInstance(AreaCaptureService::class.java).random()
                capture.toLocation(world, maxAttempt)
            }
            else -> throw IllegalArgumentException("Cannot spawn with ${mode.label} tag.")
        }
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
                entity.remove()
                script.print("Failed to resolve spawnpoint for mob: $name")
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
                    entity.remove()
                    script.print("Failed to resolve spawnpoint for mob: $name")
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
            assignment: String?,
            world: World
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
                npc.destroy()
            } else {
                Bukkit.getScheduler().runTask(Main.instance, Runnable {
                    npc.teleport(location, PlayerTeleportEvent.TeleportCause.PLUGIN)
                    npcList.add(npc.uniqueId)
                })
            }
        }

        npc.spawn(world.spawnLocation)
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