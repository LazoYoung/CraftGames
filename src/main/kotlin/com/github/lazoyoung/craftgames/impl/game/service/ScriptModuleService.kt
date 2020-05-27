package com.github.lazoyoung.craftgames.impl.game.service

import com.github.lazoyoung.craftgames.api.EventType
import com.github.lazoyoung.craftgames.api.Timer
import com.github.lazoyoung.craftgames.api.event.GameEvent
import com.github.lazoyoung.craftgames.api.module.ScriptModule
import com.github.lazoyoung.craftgames.api.script.GameScript
import com.github.lazoyoung.craftgames.api.script.ScriptCompiler
import com.github.lazoyoung.craftgames.api.script.ScriptFactory
import com.github.lazoyoung.craftgames.impl.Main
import com.github.lazoyoung.craftgames.impl.game.Game
import org.bukkit.Bukkit
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.LivingEntity
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import org.bukkit.util.io.BukkitObjectInputStream
import org.bukkit.util.io.BukkitObjectOutputStream
import java.io.*
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.function.Consumer

class ScriptModuleService internal constructor(
        private val game: Game
) : ScriptModule, Service {

    internal val events = HashMap<EventType, Consumer<in GameEvent>>()
    private val resource = game.resource
    private val script = resource.mainScript
    private val tasks = ArrayList<BukkitTask>()

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

    override fun getScript(fileName: String, mode: ScriptCompiler?): GameScript {
        val file = resource.layout.scriptDir.resolve(fileName)

        require(Files.isRegularFile(file)) {
            "Unable to locate file: $file"
        }
        require(this.isPassiveScript(file)) {
            "$file is not a passive script."
        }
        return ScriptFactory.get(file, mode ?: ScriptCompiler.getDefault())
    }

    override fun repeat(counter: Int, interval: Timer, task: Runnable): BukkitTask {
        if (interval.toTick() < 1L) {
            throw IllegalArgumentException("Repeat interval is too short! (< 1 tick)")
        }

        val bukkitTask = object : BukkitRunnable() {
            var count = counter

            override fun run() {
                if (count-- > 0) {
                    try {
                        task.run()
                    } catch (t: Throwable) {
                        script.writeStackTrace(t)
                    }
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
                try {
                    task.run()
                } catch (t: Throwable) {
                    script.writeStackTrace(t)
                }
            }
        }.runTaskLater(Main.instance, delay.toTick())

        tasks.add(bukkitTask)
        return bukkitTask
    }

    override fun dispatchCommand(target: LivingEntity, commandLine: String): Boolean {
        val wasOp = target.isOp
        val result: Boolean

        if (commandLine.split(" ").any { it.equals("op", true) }
                || target.isDead || target.world.name != game.map.worldName) {
            return false
        }

        try {
            target.isOp = true
            result = Bukkit.getServer().dispatchCommand(target, commandLine)
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
        } catch (t: Throwable) {
            t.printStackTrace()
            script.writeStackTrace(t)
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
        } catch (t: Throwable) {
            t.printStackTrace()
            script.writeStackTrace(t)
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
        } catch (t: Throwable) {
            t.printStackTrace()
            script.writeStackTrace(t)
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

    override fun terminate() {
        script.clear()

        tasks.forEach {
            try {
                it.cancel()
            } catch (e: Throwable) {}
        }
    }

    private fun isPassiveScript(file: Path): Boolean {
        val excludes = listOf(game.resource.mainScript, game.resource.commandScript)

        if (!Files.isRegularFile(file)) return false
        return excludes.firstOrNull { it?.file == file } == null
    }

}