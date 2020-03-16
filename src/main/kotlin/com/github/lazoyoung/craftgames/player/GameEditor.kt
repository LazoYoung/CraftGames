package com.github.lazoyoung.craftgames.player

import com.github.lazoyoung.craftgames.ActionBarTask
import com.github.lazoyoung.craftgames.FileUtil
import com.github.lazoyoung.craftgames.Main
import com.github.lazoyoung.craftgames.coordtag.CoordTag
import com.github.lazoyoung.craftgames.exception.ConcurrentPlayerState
import com.github.lazoyoung.craftgames.exception.FaultyConfiguration
import com.github.lazoyoung.craftgames.exception.GameNotFound
import com.github.lazoyoung.craftgames.exception.MapNotFound
import com.github.lazoyoung.craftgames.game.Game
import com.github.lazoyoung.craftgames.game.GameFactory
import org.bukkit.Bukkit
import org.bukkit.block.Block
import org.bukkit.entity.Player
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.function.Consumer

class GameEditor private constructor(
        player: Player,
        game: Game
): PlayerData(player, game) {

    var capture: CoordTag? = null

    private var blockPrompt: Consumer<Block>? = null

    private val actionBarTask = ActionBarTask(this, listOf(
            "&bEDIT MODE - &e${game.map.mapID} &bin &e${game.id}",
            "&aType &b/game save &ato save changes and exit."
    ), 10)

    companion object {
        /**
         * Make the player jump into editor mode for specific map.
         *
         * @param player who will edit the map
         * @param gameID Identifies the game in which the editor mode takes place.
         * @param mapID Identifies the map in which the editor mode takes place.
         * @param consumer Consumes the new instance. (null if game fails to start)
         * @throws ConcurrentPlayerState Thrown if the duplicate instance were found.
         * @throws MapNotFound Thrown if map is not found.
         * @throws GameNotFound No such game exists with given id.
         * @throws FaultyConfiguration Configuration is not complete.
         * @throws RuntimeException Unexpected issue has arrised.
         */
        fun start(player: Player, gameID: String, mapID: String, consumer: Consumer<GameEditor?>) {
            val pid = player.uniqueId
            val succeed: Boolean

            if (registry.containsKey(pid))
                throw ConcurrentPlayerState("Concurrent GameEditor instances are not allowed.")

            val game = GameFactory.openNew(gameID)

            if (!game.map.getMapList().contains(mapID))
                throw MapNotFound("Map not found: $mapID")

            // Start game
            succeed = game.start(mapID, Consumer {
                val instance = GameEditor(player, game)
                registry[pid] = instance
                player.teleport(it!!.spawnLocation) // TODO Module: editor spawnpoint
                consumer.accept(instance)
            })

            if (!succeed)
                consumer.accept(null)
        }
    }

    internal fun requestBlockPrompt(consumer: Consumer<Block>) {
        blockPrompt = Consumer{
            consumer.accept(it)
            blockPrompt = null
        }
    }

    internal fun callBlockPrompt(block: Block) {
        blockPrompt?.accept(block)
    }

    /**
     * @throws RuntimeException Thrown if it's unable to save map for some reason.
     */
    fun saveAndLeave() {
        val scheduler = Bukkit.getScheduler()

        get(player)?.unregister()
        game.saveConfig()
        actionBarTask.cancel()

        // Save world
        try {
            game.map.world!!.save()
        } catch (e: NullPointerException) {
            throw RuntimeException("Unable to save map because the world is null.", e)
        }

        // Clone map files to disk
        scheduler.runTaskAsynchronously(Main.instance, Runnable{
            val fileUtil = FileUtil(Main.instance.logger)
            val source = game.map.worldPath
            var target: Path
            val renameTo: Path

            if (source == null || !Files.isDirectory(source))
                throw RuntimeException("Unable to locate world files to save!")

            try {
                target = Main.instance.dataFolder.toPath()
                        .resolve(game.map.mapRegistry.first { it["id"] == game.map.mapID }["path"] as String)
            } catch (e: Exception) {
                throw RuntimeException("Unable to resolve target path for map saving.", e)
            }

            try {
                if (Files.isDirectory(target)) {
                    fileUtil.deleteFileTree(target)
                }

                renameTo = target.fileName
                target = target.parent ?: target.root!!
                fileUtil.cloneFileTree(source, target, StandardCopyOption.REPLACE_EXISTING)
                Files.move(target.resolve(source.fileName), target.resolve(renameTo))
                scheduler.runTask(Main.instance, Runnable{
                    player.sendMessage("Saved the changes! Leaving editor mode...")
                    game.stop()
                })
            } catch (e: Exception) {
                throw RuntimeException("Unable to clone world files.", e)
            }
        })
    }
}