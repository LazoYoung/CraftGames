package com.github.lazoyoung.craftgames.api

import com.github.lazoyoung.craftgames.Main
import net.md_5.bungee.api.chat.TextComponent
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitRunnable
import java.util.*
import kotlin.collections.HashMap

class MessageTask(
        private val player: Player,
        private val textCases: List<String>,
        private var repeat: Int = Int.MAX_VALUE,
        private val interval: Timer? = null
) {
    private var i = 0
    private var index = 0
    private val task: BukkitRunnable

    init {
        task = object : BukkitRunnable() {
            override fun run() {
                if (!player.isOnline) {
                    clear()
                    return
                }

                if (i++ == textCases.size - 1)
                    i = 0

                player.sendMessage(*TextComponent.fromLegacyText(
                        textCases[i].replace('&', '\u00A7')
                ))

                if (repeat-- < 1)
                    clear()
            }
        }
    }

    companion object {
        private val chatTask = HashMap<UUID, MutableList<MessageTask>>()

        fun clearAll(player: Player) {
            chatTask[player.uniqueId]?.forEach(MessageTask::clear)
        }
    }

    /**
     * Start messaging service.
     *
     * @return whether it succeed or not (confined to ActionBar).
     */
    fun start(): Boolean {
        val plugin = Main.instance
        val id = player.uniqueId
        var list = chatTask[id]

        if (list?.isNotEmpty() == true) {
            index = list.lastIndex + 1
            list.add(this)
        } else {
            list = mutableListOf(this)
        }
        chatTask[id] = list

        if (interval != null) {
            task.runTaskTimer(plugin, 0L, interval.toTick())
        } else {
            task.runTask(plugin)
        }
        return true
    }

    fun clear() {
        if (!task.isCancelled) {
            task.cancel()
        }

        chatTask[player.uniqueId]?.removeAt(index)
    }
}