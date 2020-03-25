package com.github.lazoyoung.craftgames

import com.github.lazoyoung.craftgames.module.Timer
import net.md_5.bungee.api.ChatMessageType
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitRunnable
import java.util.*
import kotlin.collections.HashMap

class MessageTask(
        private val player: Player,
        private val type: ChatMessageType,
        private val textCases: List<String>,
        private var repeat: Int = Int.MAX_VALUE,
        private val interval: Timer
) {

    companion object {
        private val actionTask = HashMap<UUID, MessageTask>()
        private val chatTask = HashMap<UUID, MessageTask>()

        fun clear(player: Player, type: ChatMessageType) {
            when (type) {
                ChatMessageType.ACTION_BAR -> actionTask[player.uniqueId]?.clear()
                ChatMessageType.CHAT -> chatTask[player.uniqueId]?.clear()
                else -> {}
            }
        }
    }

    private var i = 0
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
                ChatMessageType.CHAT -> player.sendMessage(textCases[i])
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
        val id = player.uniqueId

        when (type) {
            ChatMessageType.ACTION_BAR
                    -> if (actionTask.containsKey(id)) return false
            else    -> {}
        }

        task.runTaskTimer(Main.instance, 0L, interval.toTick())
        return true
    }

    fun clear() {
        task.cancel()

        when (type) {
            ChatMessageType.ACTION_BAR -> actionTask.remove(player.uniqueId)
            ChatMessageType.CHAT -> chatTask.remove(player.uniqueId)
            else -> {}
        }
    }
}