package com.github.lazoyoung.craftgames.game.module

import com.github.lazoyoung.craftgames.Main
import com.github.lazoyoung.craftgames.api.EventType
import com.github.lazoyoung.craftgames.api.Timer
import com.github.lazoyoung.craftgames.api.module.ScriptModule
import com.github.lazoyoung.craftgames.event.GameEvent
import com.github.lazoyoung.craftgames.game.GameResource
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import org.bukkit.util.io.BukkitObjectInputStream
import org.bukkit.util.io.BukkitObjectOutputStream
import java.io.*
import java.nio.file.Paths
import java.util.function.Consumer

class ScriptModuleService internal constructor(
        private val resource: GameResource
) : ScriptModule {

    internal val events = HashMap<EventType, Consumer<in GameEvent>>()
    private val script = resource.script
    private val tasks = ArrayList<BukkitTask>()

    override fun attachEventMonitor(eventType: EventType, callback: Consumer<in GameEvent>) {
        val legacy = this.events.put(eventType, callback)

        if (legacy == null) {
            script.printDebug("Attached an event monitor: $eventType")
        } else {
            script.printDebug("Replaced an event monitor: $eventType")
        }
    }

    override fun attachEventMonitor(eventType: String, callback: Consumer<in GameEvent>) {
        attachEventMonitor(EventType.forName(eventType), callback)
    }

    override fun detachEventMonitor(eventType: EventType) {
        if (events.containsKey(eventType)) {
            events.remove(eventType)
            script.printDebug("Detached an event monitor: $eventType")
        }
    }

    override fun detachEventMonitor(eventType: String) {
        detachEventMonitor(EventType.forName(eventType))
    }

    override fun setLogVerbosity(verbose: Boolean) {
        script.debug = verbose
    }

    override fun repeat(counter: Int, interval: Timer, task: Runnable): BukkitTask {
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
        val bukkitTask = object : BukkitRunnable() {
            override fun run() {
                task.run()
            }
        }.runTaskLater(Main.instance, delay.toTick())

        tasks.add(bukkitTask)
        return bukkitTask
    }

    override fun getFile(path: String): File {
        if (Paths.get(path).isAbsolute)
            throw IllegalArgumentException("Absolute path is not allowed.")

        val file = resource.root.resolve(path).toFile()

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

    internal fun terminate() {
        script.clear()
        events.clear()
        tasks.forEach(BukkitTask::cancel)
    }

}