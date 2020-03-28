package com.github.lazoyoung.craftgames.module.service

import com.github.lazoyoung.craftgames.Main
import com.github.lazoyoung.craftgames.coordtag.BlockCapture
import com.github.lazoyoung.craftgames.coordtag.CoordTag
import com.github.lazoyoung.craftgames.coordtag.TagMode
import com.github.lazoyoung.craftgames.exception.DependencyNotFound
import com.github.lazoyoung.craftgames.exception.MapNotFound
import com.github.lazoyoung.craftgames.game.Game
import com.github.lazoyoung.craftgames.module.api.WorldModule
import com.sk89q.worldedit.WorldEdit
import com.sk89q.worldedit.WorldEditException
import com.sk89q.worldedit.bukkit.BukkitAdapter
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats
import com.sk89q.worldedit.function.operation.Operations
import com.sk89q.worldedit.math.BlockVector3
import com.sk89q.worldedit.session.ClipboardHolder
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.WorldBorder
import org.bukkit.entity.Player
import java.io.FileInputStream
import java.util.function.Consumer

class WorldModuleService(val game: Game) : WorldModule {

    /** Key: AreaName(Tag), Value: Trigger function **/
    internal val triggers = HashMap<String, Consumer<Player>>()
    private val script = game.resource.script

    override fun setAreaTrigger(tag: String, task: Consumer<Player>?) {
        if (!game.map.areaRegistry.containsKey(tag))
                throw IllegalArgumentException("Area tag \'$tag\' does not exist.")

        if (task == null) {
            triggers.remove(tag)
            script.getLogger()?.println("An Area trigger is un-bounded from tag: $tag")
            return
        }

        triggers[tag] = Consumer<Player> {
            try {
                task.accept(it)
            } catch (e: Exception) {
                script.writeStackTrace(e)
                script.getLogger()?.println("Error occurred in Area trigger: $tag")
            }
        }
        script.getLogger()?.println("An Area trigger is bound to tag: $tag")
    }

    override fun getWorldBorder(): WorldBorder {
        return game.map.world?.worldBorder ?: throw MapNotFound("Map is not loaded yet.")
    }

    override fun placeSchematics(tag: String, path: String, biomes: Boolean, entities: Boolean, ignoreAir: Boolean) {
        if (!Bukkit.getPluginManager().isPluginEnabled("WorldEdit"))
            throw DependencyNotFound("WorldEdit is required to place schematics.")

        val filePath = game.resource.root.resolve(path)
        val file = filePath.toFile()
        val world = game.map.world ?: throw MapNotFound("Map is not loaded yet.")
        val maxBlocks = Main.config.getInt("schematic-throttle")
        val format = ClipboardFormats.findByFile(file)
                ?: throw IllegalArgumentException("Unable to resolve schematic file: $filePath")
        val ctag = CoordTag.get(game, tag) ?: throw IllegalArgumentException("Tag $tag doesn't exist in this map.")

        if (ctag.mode != TagMode.BLOCK)
            throw IllegalArgumentException("$ctag is an ${ctag.mode.label} tag which isn't supported by this function.")

        format.getReader(FileInputStream(file)).use {
            val holder = ClipboardHolder(it.read())
            val session = WorldEdit.getInstance().editSessionFactory
                    .getEditSession(BukkitAdapter.adapt(world), maxBlocks)

            ctag.getCaptures(game.map.id).filterIsInstance(BlockCapture::class.java).forEach { capture ->
                script.getLogger()?.println("Started processing ${file.name} at ${ctag.name}/${capture.index}")

                try {
                    session.use {
                        val operation = holder.createPaste(session).copyBiomes(biomes)
                                .copyEntities(entities).ignoreAirBlocks(ignoreAir)
                                .to(BlockVector3.at(capture.x, capture.y, capture.z)).build()

                        Operations.complete(operation)
                        script.getLogger()?.println("Schematic process complete.")
                    }
                } catch (e: WorldEditException) {
                    e.printStackTrace()
                    script.writeStackTrace(e)
                    script.getLogger()?.println("Schematic process failed!")
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

}