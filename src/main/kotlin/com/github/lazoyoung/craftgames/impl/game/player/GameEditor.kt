package com.github.lazoyoung.craftgames.impl.game.player

import com.github.lazoyoung.craftgames.api.ActionbarTask
import com.github.lazoyoung.craftgames.api.TimeUnit
import com.github.lazoyoung.craftgames.api.Timer
import com.github.lazoyoung.craftgames.impl.Main
import com.github.lazoyoung.craftgames.impl.exception.FaultyConfiguration
import com.github.lazoyoung.craftgames.impl.exception.GameNotFound
import com.github.lazoyoung.craftgames.impl.exception.MapNotFound
import com.github.lazoyoung.craftgames.impl.game.Game
import com.github.lazoyoung.craftgames.impl.game.GameMap
import com.github.lazoyoung.craftgames.impl.game.GamePhase
import com.github.lazoyoung.craftgames.impl.util.FileUtil
import net.md_5.bungee.api.ChatColor
import net.md_5.bungee.api.chat.ClickEvent
import net.md_5.bungee.api.chat.ComponentBuilder
import net.md_5.bungee.api.chat.HoverEvent
import org.bukkit.Bukkit
import org.bukkit.GameMode
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
) : PlayerData(player, game, GameMode.CREATIVE) {

    val mapID = game.map.id

    private var mainActionbar: ActionbarTask? = null
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
            val lobbyID = GameMap.Registry(gameName).getLobby().id
            val mapSel = if (mapID == lobbyID) {
                null
            } else {
                mapID
            }

            if (present == null) {
                val game = Game.openNew(gameName, editMode = true, mapID = mapSel)
                val instance = GameEditor(player, game)
                registry[pid] = instance
                instance.captureState()
                game.joinEditor(instance)
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

        if (game.phase == GamePhase.TERMINATE) {
            player.sendMessage("\u00A7cGame is already closing.")
            return
        } else {
            game.updatePhase(GamePhase.TERMINATE)
        }

        val scheduler = Bukkit.getScheduler()
        val plugin = Main.instance
        val source = game.map.worldPath
        val targetOrigin = game.resource.mapRegistry.getMap(mapID)!!.directory
        val gameService = game.getGameService()

        /*
         * Announce
         */
        val actionbar = ActionbarTask(
                player = player,
                period = Timer(TimeUnit.SECOND, 1L),
                repeat = true,
                text = *arrayOf(
                        "&eSaving files! Please wait.",
                        "&eSaving files! Please wait..",
                        "&eSaving files! Please wait..."
                )
        ).start()

        mainActionbar?.clear()
        gameService.broadcast("&e${player.displayName} is closing the session.")

        // Save resources
        game.resource.saveToDisk()

        /*
         * Save world
         */
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

            fun cloneProcess(atomicMove: Boolean) {
                val future = FileUtil.cloneFileTree(source, target, StandardCopyOption.REPLACE_EXISTING)

                fun process(result: Boolean, t: Throwable?) {
                    if (!result || t != null) {
                        t?.printStackTrace()
                        actionbar.clear()
                        gameService.broadcast("&cFailed to save changes!")
                        game.forceStop(error = true)
                    } else {
                        try {
                            if (atomicMove) {
                                Files.move(target.resolve(source.fileName), target.resolve(renameTo),
                                        StandardCopyOption.ATOMIC_MOVE)
                            } else {
                                Files.move(target.resolve(source.fileName), target.resolve(renameTo),
                                        StandardCopyOption.REPLACE_EXISTING)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            actionbar.clear()
                            gameService.broadcast("&cFailed to save changes!")
                            game.forceStop(error = true)
                            return
                        }

                        actionbar.clear()
                        gameService.broadcast("&aChanges are saved!")
                        informIncompleteTags(player)
                        game.close()
                    }
                }

                if (atomicMove) {
                    future.handleAsync { result, t ->
                        scheduler.runTask(plugin, Runnable { process(result, t) })
                    }
                } else {
                    future.handle { result, t ->
                        scheduler.runTask(plugin, Runnable { process(result, t) })
                    }
                }
            }

            // Clone map files to disk
            scheduler.runTaskAsynchronously(plugin, Runnable {
                try {
                    cloneProcess(true)
                } catch (e: AtomicMoveNotSupportedException) {
                    cloneProcess(false)
                    Main.logger.warning("Failed to process files in atomic move.")
                }
            })
        } catch (e: Exception) {
            actionbar.clear()
            throw RuntimeException("Unable to clone world files.", e)
        }
    }

    private fun informIncompleteTags(player: Player) {
        game.resource.tagRegistry.getCoordTags().forEach { tag ->
            val lobby = game.resource.mapRegistry.getLobby()
            val incomplMap = tag.scanIncompleteMaps().minus(lobby)

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

            incomplMap.mapIndexed { index, map ->
                builder = if (index == incomplMap.lastIndex) {
                    builder.append(map.id, reset)
                } else {
                    builder.append(map.id.plus(", "), reset)
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

    internal fun updateActionbar() {
        val coopList = game.getPlayers().map { get(it) }
                .filterIsInstance(GameEditor::class.java).shuffled()

        coopList.forEach { editor ->
            val memberList = coopList.filterNot { it.getPlayer() == editor.getPlayer() }

            var text = arrayOf(
                    "&b&lEDIT MODE &r&b(&e${editor.mapID} &bin &e${game.name}&b)",
                    "&aType &b/game save &ato save and close this session."
            )

            if (memberList.isNotEmpty()) {
                val append = arrayOf(
                        memberList.joinToString(
                                prefix = "&aEditors with you: &r",
                                limit = 3,
                                transform = { it.getPlayer().displayName }),
                        "&aYou may &b/leave &awithout closing it for others.")
                text = text.plus(append)
            }

            editor.mainActionbar?.clear()
            editor.mainActionbar = ActionbarTask(
                    player = editor.getPlayer(),
                    repeat = true,
                    text = *text
            ).start()
        }
    }
}