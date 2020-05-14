package com.github.lazoyoung.craftgames.game.service

import com.github.lazoyoung.craftgames.Main
import com.github.lazoyoung.craftgames.api.CommandHandler
import com.github.lazoyoung.craftgames.api.Timer
import com.github.lazoyoung.craftgames.api.module.CommandModule
import com.github.lazoyoung.craftgames.game.Game
import com.github.lazoyoung.craftgames.game.GameLayout
import com.github.lazoyoung.craftgames.game.player.PlayerData
import com.github.lazoyoung.craftgames.game.script.GameScript
import io.github.jorelali.commandapi.api.CommandAPI
import io.github.jorelali.commandapi.api.CommandExecutor
import io.github.jorelali.commandapi.api.CommandPermission
import io.github.jorelali.commandapi.api.arguments.Argument
import org.bukkit.command.ProxiedCommandSender
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import org.bukkit.util.io.BukkitObjectInputStream
import org.bukkit.util.io.BukkitObjectOutputStream
import java.io.*
import java.nio.file.Paths
import java.util.function.Consumer

class CommandModuleService(
        private val script: GameScript,
        private val layout: GameLayout
) : CommandModule, Service {

    private val tasks = ArrayList<BukkitTask>()

    override fun setLogVerbosity(verbose: Boolean) {
        script.debug = verbose
    }

    override fun register(name: String, permissions: CommandPermission, aliases: Array<String>?, args: LinkedHashMap<String, Argument>?, handler: CommandHandler) {
        val executor = CommandExecutor { sender, arguments ->

            val callee = if (sender is ProxiedCommandSender) {
                sender.callee
            } else {
                sender
            }

            when (callee) {
                is Player -> {
                    val playerData = PlayerData.get(callee)

                    if (playerData?.isOnline() == true) {
                        handler.run(playerData.getGame().module, callee, arguments)
                        return@CommandExecutor
                    }
                }
                is Entity -> {
                    val game = Game.getByWorld(callee.world)

                    if (game != null) {
                        handler.run(game.module, callee, arguments)
                        return@CommandExecutor
                    }
                }
            }

            sender.sendMessage("\u00A7eThis command is inactive.")
        }

        CommandAPI.getInstance().register(name, permissions,
                aliases ?: emptyArray(), args ?: LinkedHashMap(), executor)
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

    override fun getFile(path: String): File {
        if (Paths.get(path).isAbsolute)
            throw IllegalArgumentException("Absolute path is not allowed.")

        val file = layout.root.resolve(path).toFile()

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
        tasks.forEach {
            try {
                it.cancel()
            } catch (e: Throwable) {}
        }
    }
}