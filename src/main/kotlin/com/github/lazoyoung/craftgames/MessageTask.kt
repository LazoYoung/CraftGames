package com.github.lazoyoung.craftgames

import com.github.lazoyoung.craftgames.module.Timer
import net.md_5.bungee.api.ChatMessageType
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitRunnable

class MessageTask(
        private val player: Player,
        private val type: ChatMessageType,
        private val textCases: List<String>,
        private var repeat: Int = Int.MAX_VALUE,
        private val interval: Timer
) : BukkitRunnable() {

    private var i = 0
    override fun run() {
        if (i++ == textCases.size - 1)
            i = 0

        when (type) {
            ChatMessageType.ACTION_BAR -> player.sendActionBar('&', textCases[i])
            ChatMessageType.CHAT -> player.sendMessage()
            else -> {}
        }

        if (--repeat < 1)
            this.cancel()
    }

    fun start(): MessageTask {
        runTaskTimer(Main.instance, 0L, interval.toTick())
        return this
    }
}