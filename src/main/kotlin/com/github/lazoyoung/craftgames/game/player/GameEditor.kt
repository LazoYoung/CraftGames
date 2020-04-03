package com.github.lazoyoung.craftgames.game.player

import com.github.lazoyoung.craftgames.Main
import com.github.lazoyoung.craftgames.api.ActionbarTask
import com.github.lazoyoung.craftgames.coordtag.tag.CoordTag
import com.github.lazoyoung.craftgames.game.Game
import com.github.lazoyoung.craftgames.game.GameResource
import com.github.lazoyoung.craftgames.game.module.Module
import com.github.lazoyoung.craftgames.internal.exception.FaultyConfiguration
import com.github.lazoyoung.craftgames.internal.exception.GameNotFound
import com.github.lazoyoung.craftgames.internal.exception.MapNotFound
import com.github.lazoyoung.craftgames.internal.util.FileUtil
import net.md_5.bungee.api.ChatColor
import net.md_5.bungee.api.chat.ClickEvent
import net.md_5.bungee.api.chat.HoverEvent
import net.md_5.bungee.api.chat.TextComponent
import org.bukkit.Bukkit
import org.bukkit.block.Block
import org.bukkit.entity.Player
import org.bukkit.event.block.Action
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.function.BiConsumer
import java.util.function.Consumer

class GameEditor private constructor(
        player: Player,
        game: Game
): PlayerData(player, game) {

    val mapID = game.map.id

    internal var mainActionbar: ActionbarTask? = null
    private var blockPrompt: Consumer<Block>? = null
    private var areaPrompt: BiConsumer<Block, Action>? = null
    private var block1: Block? = null
    private var block2: Block? = null
    private var dialogActionbar: ActionbarTask? = null

    companion object {
        /**
         * Make the player jump into editor mode for specific map.
         *
         * @param player who will edit the map
         * @param gameName Identifies the game in which the editor mode takes place.
         * @param mapID Identifies the map in which the editor mode takes place.
         * @throws GameNotFound
         * @throws MapNotFound
         * @throws FaultyConfiguration
         * @throws RuntimeException is thrown if [player] is already in editor mode.
         */
        fun start(player: Player, gameName: String, mapID: String) {
            val pid = player.uniqueId
            var mapSel: String? = mapID

            if (registry.containsKey(pid)) {
                throw RuntimeException("Unexpected error: concurrent entries inside a registry.")
            }

            if (mapID == GameResource(gameName).lobbyMap.id)
                mapSel = null

            val present = Game.find(gameName, true).firstOrNull { mapID == it.map.id }

            if (present == null) {
                try {
                    Game.openNew(gameName, editMode = true, mapID = mapSel, consumer = Consumer { game ->
                        val instance = GameEditor(player, game)

                        registry[pid] = instance
                        game.joinEditor(instance)
                    })
                } catch (e: FaultyConfiguration) {
                    throw FaultyConfiguration(e.localizedMessage, e)
                }
            } else {
                val instance = GameEditor(player, present)

                registry[pid] = instance
                present.joinEditor(instance)
            }
        }
    }

    internal fun requestBlockPrompt(consumer: Consumer<Block>) {
        areaPrompt = null
        blockPrompt = Consumer {
            consumer.accept(it)
            blockPrompt = null
        }
    }

    internal fun requestAreaPrompt(consumer: BiConsumer<Block, Block>) {
        blockPrompt = null
        areaPrompt = BiConsumer { block, action ->
            when (action) {
                Action.LEFT_CLICK_BLOCK -> block1 = block
                Action.RIGHT_CLICK_BLOCK -> block2 = block
                else -> return@BiConsumer
            }

            dialogActionbar?.clear()

            when {
                block1 == null -> {
                    dialogActionbar = ActionbarTask(
                            player = player,
                            repeat = true,
                            text = *arrayOf("&eCapture another block with &6Left click&e!")
                    ).start()
                }
                block2 == null -> {
                    dialogActionbar = ActionbarTask(
                            player = player,
                            repeat = true,
                            text = *arrayOf("&eCapture another block with &6Right click&e!")
                    ).start()
                }
                else -> {
                    consumer.accept(block1!!, block2!!)
                    areaPrompt = null
                    block1 = null
                    block2 = null
                }
            }
        }
    }

    internal fun callBlockPrompt(block: Block): Boolean {
        blockPrompt?.accept(block) ?: return false
        return true
    }

    internal fun callAreaPrompt(block: Block, action: Action): Boolean {
        return areaPrompt?.accept(block, action) != null
    }

    /**
     * @throws RuntimeException Thrown if it's unable to save map for some reason.
     */
    fun saveAndClose() {
        val scheduler = Bukkit.getScheduler()
        val plugin = Main.instance
        val source = game.map.worldPath
        val targetOrigin = game.resource.mapRegistry[mapID]!!.repository
        val gameModule = Module.getGameModule(game)

        mainActionbar?.clear()
        gameModule.broadcast("&e${player.displayName} closed the session.")
        gameModule.broadcast("&eSaving files! Please wait...")

        // Save world
        try {
            game.map.world!!.save()
        } catch (e: NullPointerException) {
            throw RuntimeException("Unable to save map because the world is null.", e)
        }

        val target = targetOrigin.parent!!
        val renameTo: Path

        if (source == null || !Files.isDirectory(source))
            throw RuntimeException("Unable to locate world files to save!")

        try {
            if (Files.isDirectory(targetOrigin)) {
                FileUtil.deleteFileTree(targetOrigin)
            }

            renameTo = targetOrigin.fileName

            fun cloneProcess(atomic: Boolean) {
                FileUtil.cloneFileTree(source, target, StandardCopyOption.REPLACE_EXISTING)

                if (atomic) {
                    Files.move(target.resolve(source.fileName), target.resolve(renameTo), StandardCopyOption.ATOMIC_MOVE)
                } else {
                    Files.move(target.resolve(source.fileName), target.resolve(renameTo), StandardCopyOption.REPLACE_EXISTING)
                }

                scheduler.runTask(plugin, Runnable {
                    gameModule.broadcast("&aChanges are saved!")

                    // Inform to editor if incomplete tag were found.
                    CoordTag.getAll(game).forEach { tag ->
                        val maps = tag.scanIncompleteMaps().toMutableList()
                        maps.remove(game.resource.lobbyMap.id)

                        if (maps.isNotEmpty()) {
                            val hov1 = arrayOf(TextComponent("Click here to capture the tag."))
                            val hov2 = arrayOf(TextComponent("Click here to edit the map."))
                            val arr = ArrayList<TextComponent>()
                            arr.add(0, TextComponent("You forgot to capture "))
                            arr.add(1, TextComponent(tag.name))
                            arr.add(2, TextComponent(" tag from "))
                            arr.addAll(maps.mapIndexed { index, mapID ->
                                val c4 = if (index == maps.size - 1) {
                                    TextComponent(mapID)
                                } else {
                                    TextComponent(mapID.plus(", "))
                                }
                                c4.color = ChatColor.WHITE
                                c4.hoverEvent = HoverEvent(HoverEvent.Action.SHOW_TEXT, hov2)
                                c4.clickEvent = ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,
                                        "/game edit ${game.name} $mapID")
                                c4.isUnderlined = true
                                c4
                            })
                            arr.add(TextComponent("!"))
                            arr[0].color = ChatColor.YELLOW
                            arr[1].color = ChatColor.WHITE
                            arr[1].isUnderlined = true
                            arr[1].hoverEvent = HoverEvent(HoverEvent.Action.SHOW_TEXT, hov1)
                            arr[1].clickEvent = ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/ctag capture ${tag.name}")
                            arr[2].color = ChatColor.YELLOW
                            arr[arr.lastIndex].color = ChatColor.YELLOW
                            player.sendMessage(*arr.toTypedArray())
                        }
                    }

                    // Close the game
                    game.close()
                })
            }

            // Clone map files to disk
            scheduler.runTaskAsynchronously(plugin, Runnable {
                try {
                    cloneProcess(true)
                } catch (e: AtomicMoveNotSupportedException) {
                    scheduler.runTask(plugin, Runnable {
                        cloneProcess(false)
                    })
                    Main.logger.warning("Failed to process files in atomic move.")
                }
            })
        } catch (e: Exception) {
            throw RuntimeException("Unable to clone world files.", e)
        }
    }
}