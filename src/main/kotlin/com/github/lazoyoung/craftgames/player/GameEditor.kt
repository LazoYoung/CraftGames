package com.github.lazoyoung.craftgames.player

import com.github.lazoyoung.craftgames.Main
import com.github.lazoyoung.craftgames.coordtag.CoordTag
import com.github.lazoyoung.craftgames.exception.FaultyConfiguration
import com.github.lazoyoung.craftgames.exception.GameNotFound
import com.github.lazoyoung.craftgames.exception.MapNotFound
import com.github.lazoyoung.craftgames.game.Game
import com.github.lazoyoung.craftgames.game.GameResource
import com.github.lazoyoung.craftgames.util.FileUtil
import com.github.lazoyoung.craftgames.util.MessageTask
import com.github.lazoyoung.craftgames.util.TimeUnit
import com.github.lazoyoung.craftgames.util.Timer
import net.md_5.bungee.api.ChatColor
import net.md_5.bungee.api.ChatMessageType
import net.md_5.bungee.api.chat.ClickEvent
import net.md_5.bungee.api.chat.HoverEvent
import net.md_5.bungee.api.chat.TextComponent
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

    val mapID = game.map.mapID

    private var blockPrompt: Consumer<Block>? = null

    private val actionBar: MessageTask = MessageTask(
            player = player,
            type = ChatMessageType.ACTION_BAR,
            interval = Timer(TimeUnit.SECOND, 2),
            textCases = listOf(
                    "&bEDIT MODE - &e${game.map.mapID} &bin &e${game.name}",
                    "&bEDIT MODE - &e${game.map.mapID} &bin &e${game.name}",
                    "&aType &b/game save &ato save changes and exit.",
                    "&aType &b/game save &ato save changes and exit."
            )
    )

    init {
        if (!actionBar.start()) {
            MessageTask.clear(player, ChatMessageType.ACTION_BAR)
            actionBar.start()
        }
    }

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
         * @throws RuntimeException
         */
        fun start(player: Player, gameName: String, mapID: String) {
            val pid = player.uniqueId
            val report = TextComponent()
            var mapSel: String? = mapID
            report.color = ChatColor.RED

            if (registry.containsKey(pid)) {
                report.text = "Unexpected error: Concurrent GameEditor entries."
                player.sendMessage(report)
                Main.logger.warning(report.toPlainText())
                return
            }

            if (mapID == GameResource(gameName).lobbyMap.mapID)
                mapSel = null

            try {
                Game.openNew(gameName, editMode = true, mapID = mapSel, consumer = Consumer { game ->
                    val instance = GameEditor(player, game)
                    registry[pid] = instance
                    game.startEdit(instance)
                })
            } catch (e: FaultyConfiguration) {
                throw FaultyConfiguration(e.localizedMessage, e)
            }
        }
    }

    internal fun requestBlockPrompt(consumer: Consumer<Block>) {
        blockPrompt = Consumer{
            consumer.accept(it)
            blockPrompt = null
        }
    }

    internal fun callBlockPrompt(block: Block): Boolean {
        blockPrompt?.accept(block) ?: return false
        return true
    }

    /**
     * @throws RuntimeException Thrown if it's unable to save map for some reason.
     */
    fun saveAndLeave() {
        val scheduler = Bukkit.getScheduler()
        val plugin = Main.instance
        val source = game.map.worldPath
        val targetOrigin = game.resource.mapRegistry[mapID]!!.repository

        game.leave(this)
        actionBar.clear()

        // Save world
        try {
            game.map.world!!.save()
        } catch (e: NullPointerException) {
            throw RuntimeException("Unable to save map because the world is null.", e)
        }

        // Clone map files to disk
        scheduler.runTaskAsynchronously(plugin, Runnable{
            val target = targetOrigin.parent ?: targetOrigin.root!!
            val renameTo: Path

            if (source == null || !Files.isDirectory(source))
                throw RuntimeException("Unable to locate world files to save!")

            try {
                if (Files.isDirectory(targetOrigin)) {
                    FileUtil.deleteFileTree(targetOrigin)
                }

                renameTo = targetOrigin.fileName
                FileUtil.cloneFileTree(source, target, StandardCopyOption.REPLACE_EXISTING)
                Files.move(target.resolve(source.fileName), target.resolve(renameTo))
                scheduler.runTask(plugin, Runnable{
                    player.sendMessage("Saved the changes! Leaving editor mode...")

                    // Inform to editor if incomplete tag were found.
                    CoordTag.getAll(game).forEach { tag ->
                        val maps = tag.scanIncompleteMaps().toMutableList()
                        maps.remove(game.resource.lobbyMap.mapID)

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
            } catch (e: Exception) {
                throw RuntimeException("Unable to clone world files.", e)
            }
        })
    }
}