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
import net.md_5.bungee.api.chat.ComponentBuilder
import net.md_5.bungee.api.chat.HoverEvent
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
        private val game: Game
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
         * @throws RuntimeException is raised if plugin fails to write player's data.
         */
        fun start(player: Player, gameName: String, mapID: String) {
            val pid = player.uniqueId

            if (registry.containsKey(pid)) {
                player.sendMessage("\u00A7cYou're already in editor mode.")
                return
            }

            val present = Game.find(gameName, true).firstOrNull { mapID == it.map.id }
            val mapSel = if (mapID == GameResource(gameName).lobbyMap.id) {
                null
            } else {
                mapID
            }

            if (present == null) {
                Game.openNew(gameName, editMode = true, mapID = mapSel, consumer = Consumer { game ->
                    val instance = GameEditor(player, game)
                    registry[pid] = instance

                    instance.captureState()
                    game.joinEditor(instance)
                })
            } else {
                val instance = GameEditor(player, present)
                registry[pid] = instance

                instance.captureState()
                present.joinEditor(instance)
            }
        }
    }

    /**
     * Returns the [Game] this editor belongs to.
     */
    override fun getGame(): Game {
        return game
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
                            player = getPlayer(),
                            repeat = true,
                            text = *arrayOf("&eCapture another block with &6Left click&e!")
                    ).start()
                }
                block2 == null -> {
                    dialogActionbar = ActionbarTask(
                            player = getPlayer(),
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
        val player = getPlayer()

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
                    informIncompleteTags(player)
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

    private fun informIncompleteTags(player: Player) {
        CoordTag.getAll(game).forEach { tag ->
            val incomplMap = tag.scanIncompleteMaps().minus(game.resource.lobbyMap.id)

            if (incomplMap.isEmpty()) {
                return@forEach
            }

            val gameName = game.name
            val tagName = tag.name
            val reset = ComponentBuilder.FormatRetention.NONE
            var builder = ComponentBuilder()
                    .append("You forgot to capture ", reset)
                    .color(ChatColor.YELLOW)
                    .append(tagName)
                    .event(HoverEvent(HoverEvent.Action.SHOW_TEXT, ComponentBuilder("Click to capture this tag.").create()))
                    .event(ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/ctag capture $tagName"))
                    .underlined(true)
                    .color(ChatColor.WHITE)
                    .append(" from ", reset)
                    .color(ChatColor.YELLOW)

            incomplMap.mapIndexed { index, mapID ->
                builder = if (index == incomplMap.lastIndex) {
                    builder.append(mapID, reset)
                } else {
                    builder.append(mapID.plus(", "), reset)
                }

                builder = builder.event(HoverEvent(HoverEvent.Action.SHOW_TEXT, ComponentBuilder("Click to edit this map.").create()))
                        .event(ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/game edit $gameName $mapID"))
                        .color(ChatColor.WHITE).underlined(true)
            }

            builder = builder
                    .append("! ", reset)
                    .color(ChatColor.YELLOW)
                    .append("×")
                    .color(ChatColor.DARK_RED)
                    .underlined(true)
                    .event(HoverEvent(HoverEvent.Action.SHOW_TEXT,
                            ComponentBuilder("Click to hide this warning.").color(ChatColor.RED).create()))
                    .event(ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/ctag suppress $gameName $tagName true"))

            player.sendMessage(*builder.create())
        }
    }
}