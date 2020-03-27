package com.github.lazoyoung.craftgames.util

import com.github.lazoyoung.craftgames.Main
import net.md_5.bungee.api.ChatMessageType
import net.md_5.bungee.api.chat.TextComponent
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitRunnable
import java.util.*
import kotlin.collections.HashMap

class MessageTask(
        private val player: Player,
        private val type: ChatMessageType,
        private val textCases: List<String>,
        private var repeat: Int = Int.MAX_VALUE,
        private val interval: Timer? = null
) {

    companion object {
        private val actionTask = HashMap<UUID, MessageTask>()
        private val chatTask = HashMap<UUID, MutableList<MessageTask>>()

        fun clear(player: Player, type: ChatMessageType) {
            when (type) {
                ChatMessageType.ACTION_BAR -> actionTask[player.uniqueId]?.clear()
                ChatMessageType.CHAT -> chatTask[player.uniqueId]?.forEach(MessageTask::clear)
                else -> {}
            }
        }
    }

    private var i = 0
    private var index = 0
    private val task = object : BukkitRunnable() {
        override fun run() {
            if (!player.isOnline) {
                clear()
                return
            }

            if (i++ == textCases.size - 1)
                i = 0

            when (type) {
                ChatMessageType.ACTION_BAR -> player.sendActionBar('&', textCases[i])
                ChatMessageType.CHAT -> player.sendMessage(
                        *TextComponent.fromLegacyText(textCases[i].replace('&', '\u00A7'))
                )
                else -> {}
            }

            if (repeat-- < 1)
                clear()
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

        when (type) {
            ChatMessageType.ACTION_BAR ->
                if (actionTask.containsKey(id)) {
                    return false
                } else {
                    actionTask[id] = this
                }
            ChatMessageType.CHAT -> {
                var list = chatTask[id]

                if (list?.isNotEmpty() == true) {
                    index = list.lastIndex + 1
                    list.add(this)
                } else {
                    list = mutableListOf(this)
                }
                chatTask[id] = list
            }
            else -> return false
        }

        if (interval != null) {
            task.runTaskTimer(plugin, 0L, interval.toTick())
        } else {
            task.runTask(plugin)
        }
        return true
    }

    fun clear() {
        task.cancel()

        when (type) {
            ChatMessageType.ACTION_BAR -> actionTask.remove(player.uniqueId)
            ChatMessageType.CHAT -> {
                chatTask[player.uniqueId]?.removeAt(index)
            }
            else -> {}
        }
    }
}