package com.github.lazoyoung.craftgames

import com.github.lazoyoung.craftgames.player.GameEditor
import org.bukkit.scheduler.BukkitRunnable

class ActionBarTask(
        private val editor: GameEditor,
        private val text: List<String>,
        interval_sec: Int
) : BukkitRunnable() {
    private var i = 0

    init {
        runTaskTimer(Main.instance, 0L, 20L * interval_sec)
    }

    override fun run() {
        if (i++ == text.size - 1)
            i = 0

        editor.player.sendActionBar('&', text[i])
    }

}