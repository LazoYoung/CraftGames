package com.github.lazoyoung.craftgames.impl.game

import com.github.lazoyoung.craftgames.impl.Main
import com.github.lazoyoung.craftgames.api.script.ScriptCompiler
import com.github.lazoyoung.craftgames.api.tag.coordinate.CoordTag
import com.github.lazoyoung.craftgames.api.script.GameScript
import com.github.lazoyoung.craftgames.api.script.ScriptFactory
import com.github.lazoyoung.craftgames.impl.exception.FaultyConfiguration
import com.github.lazoyoung.craftgames.impl.exception.GameNotFound
import com.github.lazoyoung.craftgames.impl.exception.MapNotFound
import com.github.lazoyoung.craftgames.impl.util.DatapackUtil
import com.nisovin.shopkeepers.api.shopkeeper.admin.regular.RegularAdminShopkeeper
import com.nisovin.shopkeepers.shopkeeper.offers.SKTradingOffer
import org.bukkit.Bukkit
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.inventory.ItemStack
import java.io.File
import java.io.IOException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * @throws GameNotFound is thrown if game cannot be resolved by [gameName].
 */
class GameResource internal constructor(private val gameName: String) {

    internal val layout = GameLayout(gameName)
    val tagRegistry = CoordTag.Registry(layout)
    val mapRegistry = GameMap.Registry(layout, tagRegistry)
    val mainScript: GameScript
    val commandScript: GameScript?
    internal val kitData = HashMap<String, ByteArray>()
    private val kitFiles = HashMap<String, File>()
    private val namespace = gameName.toLowerCase()

    init {
        /*
         * Load kit
         */
        layout.kitDir.toFile().let {
            it.mkdirs()
            it.listFiles()?.forEach { file ->
                if (file.extension != "kit")
                    return@forEach

                val name = file.nameWithoutExtension

                try {
                    kitData[name] = Files.readAllBytes(file.toPath())
                    kitFiles[name] = file
                } catch (e: IOException) {
                    throw RuntimeException("Failed to read kit file.", e)
                }
            }
        }

        /*
         * Load scripts
         */
        val mainScriptStr = layout.config.getString("script.main.file")
                ?: throw FaultyConfiguration("Main script path is not defined in ${layout.path}")
        val commandScriptStr = layout.config.getString("script.command.file")
        val mainCompiler = ScriptCompiler.get(layout.config.getString("script.main.compiler"))
        val commandCompiler = ScriptCompiler.get(layout.config.getString("script.command.compiler"))
        val mainScript = layout.scriptDir.resolve(mainScriptStr)
        val commandScript = commandScriptStr?.let { layout.scriptDir.resolve(it) }

        try {
            try {
                Files.createDirectories(layout.scriptDir)
            } catch (e: FileAlreadyExistsException) {
                Files.delete(layout.scriptDir)
                Files.createDirectories(layout.scriptDir)
            }

            Files.createFile(mainScript)
        } catch (e: SecurityException) {
            throw RuntimeException("Failed to load script: $mainScript", e)
        } catch (e: FileAlreadyExistsException) {}

        this.mainScript = ScriptFactory.get(mainScript, mainCompiler)
        this.commandScript = commandScript?.let { ScriptFactory.get(it, commandCompiler) }
    }

    internal fun saveToDisk() {
        try {
            tagRegistry.saveToDisk()
            kitData.forEach { (name, byteArr) ->
                var file = kitFiles[name]

                if (file == null) {
                    file = layout.kitDir.resolve(name.plus(".kit")).toFile()
                    file.createNewFile()
                }

                Files.write(file!!.toPath(), byteArr)
            }
        } catch (e: IOException) {
            e.printStackTrace()
            Main.logger.severe("Failed to save kit data!")
        }
    }

    /**
     * Look for playable maps and get a random element among them.
     * Lobby map is never obtained by using this method.
     *
     * @return A randomly chosen map.
     * @throws MapNotFound if this game doesn't have a map.
     */
    internal fun getRandomMap(): GameMap {
        val map: GameMap
        try {
            map = mapRegistry.getMaps().filter { !it.isLobby }.random()
        } catch (e: NoSuchElementException) {
            throw MapNotFound("$gameName doesn't have any registered map.")
        }
        return map
    }

    internal fun loadDatapack(): Boolean {
        try {
            val packName = DatapackUtil.getInternalPackName()
            val packDir = DatapackUtil.getPackDirectory(Bukkit.getWorlds().first(), packName)
                    ?: DatapackUtil.createPack(packName, true)
            val resourceDir = packDir.resolve("data").resolve(namespace)

            // Clone loot tables into datapack.
            if (layout.lootTableDir != null) {
                val lootTableDir = resourceDir.resolve("loot_tables")

                check(lootTableDir.isDirectory || lootTableDir.mkdirs()) {
                    "Failed to create directory."
                }
                Files.newDirectoryStream(layout.lootTableDir).use {
                    it.forEach { entry ->
                        val entryTarget = lootTableDir.toPath().resolve(layout.lootTableDir.relativize(entry))
                        Files.copy(entry, entryTarget, StandardCopyOption.REPLACE_EXISTING)
                    }
                }
            }

            Bukkit.reloadData()
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
        return true
    }

    /**
     * @param shopkeeper an instance of [RegularAdminShopkeeper]
     * @throws IllegalArgumentException is raised if [shopkeeper] is not a [RegularAdminShopkeeper].
     */
    @Deprecated("GameShopkeeper is the alternative.")
    internal fun <T> loadShopkeeper(shopkeeper: T) {
        require(shopkeeper is RegularAdminShopkeeper) {
            "This is not a RegularAdminShopkeeper."
        }

        val fileName = shopkeeper.uniqueId.toString().plus(".yml")
        val path = layout.shopkeepersDir.resolve(fileName)

        if (!Files.isRegularFile(path)) {
            return
        }

        try {
            path.toFile().bufferedReader().use {
                val config = YamlConfiguration.loadConfiguration(it)
                val entries = config.getMapList("offers")

                for (entry in entries) {
                    val result = entry["result"] as ItemStack
                    val item1 = entry["item1"] as ItemStack
                    val item2 = entry["item2"] as ItemStack?

                    shopkeeper.addOffer(SKTradingOffer(result, item1, item2))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * @param shopkeeper an instance of [RegularAdminShopkeeper]
     * @throws IllegalArgumentException is raised if [shopkeeper] is not a [RegularAdminShopkeeper].
     * @throws IllegalStateException is raised if [shopkeeper] is invalid.
     * @throws IllegalStateException is raised if [shopkeeper]'s object is inactive or unsupported.
     */
    @Deprecated("GameShopkeeper is the alternative.")
    internal fun <T> saveShopkeeper(shopkeeper: T) {
        require(shopkeeper is RegularAdminShopkeeper) {
            "This is not a RegularAdminShopkeeper."
        }
        check(shopkeeper.isValid) {
            "This shopkeeper is not valid."
        }
        check(shopkeeper.shopObject.isActive) {
            "This shopkeeper object is not active."
        }

        val fileName = shopkeeper.uniqueId.toString().plus(".yml")
        val path = layout.shopkeepersDir.resolve(fileName)
        val entries = ArrayList<Map<String, ItemStack?>>()
        val file = path.toFile()

        try {
            Files.createDirectories(path.parent!!)
            Files.createFile(path)
        } catch (e: FileAlreadyExistsException) {
            // ignore
        } catch (e: IOException) {
            e.printStackTrace()
        }

        file.bufferedReader().use { reader ->
            val config = YamlConfiguration.loadConfiguration(reader)

            shopkeeper.offers.forEach {
                val map = HashMap<String, ItemStack?>()

                map["result"] = it.resultItem
                map["item1"] = it.item1
                map["item2"] = it.item2
                entries.add(map)
            }

            config.set("offers", entries)
            config.save(file)
        }
    }

    @Deprecated("GameShopkeeper is the alternative.")
    internal fun <T> discardShopkeeper(shopkeeper: T) {
        require(shopkeeper is RegularAdminShopkeeper) {
            "This is not a RegularAdminShopkeeper."
        }

        val fileName = shopkeeper.uniqueId.toString().plus(".yml")
        val path = layout.shopkeepersDir.resolve(fileName)

        try {
            Files.deleteIfExists(path)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}