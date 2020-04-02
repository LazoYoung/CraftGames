package com.github.lazoyoung.craftgames.api

import com.github.lazoyoung.craftgames.Main
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitRunnable
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.collections.HashMap
import kotlin.math.ceil

class ActionbarTask(
        private val player: Player,
        period: Timer = Timer(TimeUnit.SECOND, 5),
        private val repeat: Boolean = false,
        private vararg val text: String
) {
    private var active = true
    private var id = -1
    private val taskPeriod = 10L
    private val runnable: BukkitRunnable

    init {
        /**
         *  Each text-line is repeated this amount of times
         *  to ensure that actionbar won't fade out between interval.
         */
        val repeatCount = ceil(period.toTick() / taskPeriod.toDouble()).toInt()

        runnable = object : BukkitRunnable() {
            var index = 0
            var repeat = repeatCount

            override fun run() {
                if (!player.isOnline) {
                    clear()
                    return
                }

                if (active) { // Prevent concurrent actions from ovarlapping
                    player.sendActionBar('&', text[index])
                }

                when {
                    (--repeat > 0) -> {
                        return
                    }
                    (++index < text.size) -> {
                        repeat = repeatCount
                        return
                    }
                    this@ActionbarTask.repeat -> {
                        repeat = repeatCount
                        index = 0
                    }
                    else -> {
                        clear()
                    }
                }
            }
        }

        registerTask(player, this)
    }

    companion object {
        private val registry = HashMap<UUID, ConcurrentLinkedQueue<ActionbarTask>>()
        private var nextID = 0

        fun clearAll(player: Player) {
            registry.computeIfPresent(player.uniqueId) { _, list ->
                list.forEach(ActionbarTask::clear)
                list
            }
            registry.remove(player.uniqueId)
        }

        private fun registerTask(player: Player, task: ActionbarTask) {
            val uid = player.uniqueId

            if (registry.containsKey(uid)) {
                registry.compute(uid) { _, value ->
                    value!!.forEach { it.active = false }
                    task.id = nextID++
                    value.add(task)
                    value
                }
            } else {
                registry.computeIfAbsent(uid) {
                    task.id = nextID++
                    val queue = ConcurrentLinkedQueue<ActionbarTask>()
                    queue.add(task)
                    queue
                }
            }
        }

        private fun dropTask(player: Player, task: ActionbarTask) {
            val uid = player.uniqueId

            registry.computeIfPresent(uid) { _, list ->
                val iter = list.iterator()

                while (iter.hasNext()) {
                    if (iter.next().id == task.id) {
                        iter.remove()
                        break
                    }
                }

                list.lastOrNull()?.active = true
                list
            }
        }
    }

    fun start(): ActionbarTask {
        runnable.runTaskTimer(Main.instance, 0L, taskPeriod)
        return this
    }

    fun clear() {
        try {
            runnable.cancel()
        } catch (e: IllegalStateException) {}

        dropTask(player, this)
    }

}