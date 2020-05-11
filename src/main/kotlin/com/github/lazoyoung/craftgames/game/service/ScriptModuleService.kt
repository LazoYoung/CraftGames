package com.github.lazoyoung.craftgames.game.service

import com.github.lazoyoung.craftgames.Main
import com.github.lazoyoung.craftgames.api.EventType
import com.github.lazoyoung.craftgames.api.Timer
import com.github.lazoyoung.craftgames.api.module.ScriptModule
import com.github.lazoyoung.craftgames.event.GameEvent
import com.github.lazoyoung.craftgames.game.Game
import com.github.lazoyoung.craftgames.game.player.PlayerData
import com.github.lazoyoung.craftgames.game.script.GameScript
import com.github.lazoyoung.craftgames.game.script.ScriptFactory
import org.bukkit.Bukkit
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.player.AsyncPlayerChatEvent
import org.bukkit.event.player.PlayerCommandPreprocessEvent
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import org.bukkit.util.io.BukkitObjectInputStream
import org.bukkit.util.io.BukkitObjectOutputStream
import java.io.*
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.function.BiConsumer
import java.util.function.Consumer
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

class ScriptModuleService internal constructor(
        private val game: Game
) : ScriptModule, Service {

    internal val events = HashMap<EventType, Consumer<in GameEvent>>()
    internal val commandLabels = HashMap<String, BiConsumer<Player, Array<String>>>()
    private val resource = game.resource
    private val script = resource.gameScript
    private val tasks = ArrayList<BukkitTask>()

    companion object {
        private val registeredLabels: MutableSet<String> = Collections.newSetFromMap(ConcurrentHashMap())

        /**
         * This function guarantees thread safety.
         *
         * @return true if the command referred by [label] is registered in one of running games.
         */
        internal fun isCommandRegistered(label: String): Boolean {
            return registeredLabels.contains(label)
        }

        /**
         * Handle the command referred by [label].
         *
         * This function is designed to be called from [AsyncPlayerChatEvent].
         * It takes asynchronous chat in account.
         */
        internal fun handleCommand(label: String, event: PlayerCommandPreprocessEvent) {
            val args = event.message.split(" ").drop(1).toTypedArray()
            val player = event.player
            val playerData = PlayerData.get(player)
                    ?: return
            val scriptService = playerData.getGame().module.getScriptModule() as ScriptModuleService
            val handler =  scriptService.commandLabels[label]

            if (handler != null) {
                handler.accept(player, args)
                event.isCancelled = true
            }
        }
    }

    override fun attachEventMonitor(eventType: EventType, callback: Consumer<in GameEvent>): Boolean {
        return events.put(eventType, callback) == null
    }

    override fun attachEventMonitor(eventType: String, callback: Consumer<in GameEvent>): Boolean {
        return attachEventMonitor(EventType.forName(eventType), callback)
    }

    override fun detachEventMonitor(eventType: EventType): Boolean {
        return events.remove(eventType) != null
    }

    override fun detachEventMonitor(eventType: String) {
        detachEventMonitor(EventType.forName(eventType))
    }

    override fun setLogVerbosity(verbose: Boolean) {
        script.debug = verbose
    }

    override fun getScript(fileName: String): GameScript {
        val file = resource.scriptRoot.resolve(fileName)

        require(Files.isRegularFile(file))
        return ScriptFactory.get(file)
    }

    override fun repeat(counter: Int, interval: Timer, task: Runnable): BukkitTask {
        if (interval.toTick() < 1L) {
            throw IllegalArgumentException("Repeat interval is too short! (< 1 tick)")
        }

        val bukkitTask = object : BukkitRunnable() {
            var count = counter

            override fun run() {
                if (count-- > 0) {
                    task.run()
                } else {
                    this.cancel()
                }
            }
        }.runTaskTimer(Main.instance, 0L, interval.toTick())

        tasks.add(bukkitTask)
        return bukkitTask
    }

    override fun wait(delay: Timer, task: Runnable): BukkitTask {
        if (delay.toTick() < 1L) {
            throw IllegalArgumentException("Wait delay is too short! (< 1 tick)")
        }

        val bukkitTask = object : BukkitRunnable() {
            override fun run() {
                task.run()
            }
        }.runTaskLater(Main.instance, delay.toTick())

        tasks.add(bukkitTask)
        return bukkitTask
    }

    override fun registerCommand(label: String, handler: BiConsumer<Player, Array<String>>): Boolean {
        val commandLabel = "/$label"

        if (commandLabels.containsKey(commandLabel)) {
            return false
        }

        commandLabels[commandLabel] = handler
        registeredLabels.add(commandLabel)
        return true
    }

    override fun unregisterCommand(label: String): Boolean {
        val commandLabel = "/$label"

        if (!commandLabels.containsKey(commandLabel)) {
            return false
        }

        commandLabels.remove(commandLabel)
        registeredLabels.remove(commandLabel)
        return true
    }

    override fun dispatchCommand(target: LivingEntity, commandLine: String): Boolean {
        val wasOp = target.isOp
        var result = false

        if (commandLine.split(" ").any { it.equals("op", true) }
                || target.isDead || target.world.name != game.map.worldName) {
            return false
        }

        try {
            target.isOp = true
            result = Bukkit.getServer().dispatchCommand(target, commandLine)
        } catch (e: Exception) {
            script.writeStackTrace(e)
            game.forceStop(error = true)
        } finally {
            if (!wasOp) {
                target.isOp = false
            }
        }

        if (result) {
            script.printDebug("Successfully dispatched command: $commandLine")
        } else {
            script.print("Failed to dispatch command: $commandLine")
        }

        return result
    }

    override fun getFile(path: String): File {
        if (Paths.get(path).isAbsolute)
            throw IllegalArgumentException("Absolute path is not allowed.")

        val file = resource.layout.root.resolve(path).toFile()

        if (!file.isFile) {
            file.parentFile!!.mkdirs()
            file.createNewFile()
        }

        return file
    }

    override fun readObjectStream(file: File, reader: Consumer<BukkitObjectInputStream>) {
        var stream: BukkitObjectInputStream? = null

        if (!file.isFile)
            throw FileNotFoundException("Unable to locate file: ${file.toPath()}")

        try {
            val wrapper = BufferedInputStream(FileInputStream(file))
            stream = BukkitObjectInputStream(wrapper)
            reader.accept(stream)
        } catch (e: IOException) {
            e.printStackTrace()
            script.writeStackTrace(e)
        } finally {
            try {
                stream?.close()
            } catch (e: IOException) {
                e.printStackTrace()
                script.writeStackTrace(e)
            }
        }
    }

    override fun writeObjectStream(file: File, writer: Consumer<BukkitObjectOutputStream>) {
        var stream: BukkitObjectOutputStream? = null

        if (!file.isFile)
            throw FileNotFoundException("Unable to locate file: ${file.toPath()}")

        try {
            val wrapper = BufferedOutputStream(FileOutputStream(file))
            stream = BukkitObjectOutputStream(wrapper)
            writer.accept(stream)
        } catch (e: IOException) {
            e.printStackTrace()
            script.writeStackTrace(e)
            stream?.reset()
        } finally {
            try {
                stream?.close()
            } catch (e: IOException) {
                e.printStackTrace()
                script.writeStackTrace(e)
            }
        }
    }

    override fun getYamlConfiguration(file: File, consumer: Consumer<YamlConfiguration>) {
        val ext = file.extension
        var fileReader: BufferedReader? = null

        if (!file.isFile)
            throw FileNotFoundException("Unable to locate file: ${file.toPath()}")

        if (!ext.equals("yml", true))
            throw IllegalArgumentException("Illegal file format: $ext")

        try {
            fileReader = file.bufferedReader(Main.charset)
            val config = YamlConfiguration.loadConfiguration(fileReader)
            consumer.accept(config)
            config.save(file)
        } catch (e: IOException) {
            e.printStackTrace()
            script.writeStackTrace(e)
        } finally {
            try {
                fileReader?.close()
            } catch (e: IOException) {
                e.printStackTrace()
                script.writeStackTrace(e)
            }
        }
    }

    override fun start() {}

    override fun restart() {}

    override fun terminate() {
        script.clear()
        tasks.forEach {
            try {
                it.cancel()
            } catch (e: Exception) {}
        }
    }

}