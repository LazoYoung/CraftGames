package com.github.lazoyoung.craftgames.api.shopkeepers

import com.github.lazoyoung.craftgames.impl.game.GameLayout
import com.nisovin.shopkeepers.api.shopkeeper.admin.regular.RegularAdminShopkeeper
import com.nisovin.shopkeepers.api.shopkeeper.offers.TradingOffer
import com.nisovin.shopkeepers.shopkeeper.offers.SKTradingOffer
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.inventory.ItemStack
import java.io.IOException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files

class GameShopkeeper(
        private val layout: GameLayout,
        private val instance: RegularAdminShopkeeper
) {

    private var id: String? = null

    fun getTrades(): List<Trade> {
        return instance.offers.map { encapsulate(it) }
    }

    fun clearTrades() {
        instance.clearOffers()
    }

    fun setTrades(trade: List<Trade>) {
        instance.offers = trade.map { decapsulate(it) }
    }

    fun addTrade(trade: Trade) {
        instance.addOffer(decapsulate(trade))
    }

    fun load(id: String) {
        check(this.id == null) {
            "This shopkeeper is already loaded with id: ${this.id}"
        }

        this.id = id
        val fileName = id.plus(".yml")
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

                    addTrade(Trade(result, item1, item2))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun save(id: String) {
        check(this.id == null) {
            "This shopkeeper is already saved with id: ${this.id}"
        }
        check(instance.isValid) {
            "This shopkeeper is not valid."
        }
        check(instance.shopObject.isActive) {
            "This shopkeeper object is not active."
        }

        this.id = id
        val fileName = id.plus(".yml")
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

            getTrades().forEach {
                val map = HashMap<String, ItemStack?>()

                map["result"] = it.result
                map["item1"] = it.cost1
                map["item2"] = it.cost2
                entries.add(map)
            }

            config.set("offers", entries)
            config.save(file)
        }
    }

    fun discard() {
        checkNotNull(id) {
            "Cannot discard this shopkeeper because it's not saved."
        }

        val fileName = id.plus(".yml")
        val path = layout.shopkeepersDir.resolve(fileName)

        try {
            Files.deleteIfExists(path)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun encapsulate(o: TradingOffer): Trade {
        return Trade(o.resultItem, o.item1, o.item2)
    }

    private fun decapsulate(t: Trade): TradingOffer {
        return SKTradingOffer(t.result, t.cost1, t.cost2)
    }

}