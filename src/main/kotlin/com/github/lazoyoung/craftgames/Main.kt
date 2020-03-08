package com.github.lazoyoung.craftgames

import com.github.lazoyoung.craftgames.exception.ScriptEngineNotFound
import com.github.lazoyoung.craftgames.script.ScriptBase
import com.github.lazoyoung.craftgames.script.ScriptFactory
import groovy.lang.GroovyRuntimeException
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Files
import java.util.function.Consumer

class Main : JavaPlugin(), CommandExecutor {

    companion object {
        var assetFiles: List<File> = ArrayList()
        lateinit var config: FileConfiguration
    }

    override fun onEnable() {
        saveDefaultConfig()
        Companion.config = config
        extractAsset()
        loadAsset()
    }

    override fun onCommand(
            sender: CommandSender,
            command: Command,
            label: String,
            args: Array<out String>
    ): Boolean {
        if (command.name != "script")
            return true

        if (args.size == 2 && args[0] == "execute") {
            val name: String = args[1]
            val file: File? = assetFiles.singleOrNull { it.nameWithoutExtension == name }
            val script: ScriptBase

            try {
                if (file == null) {
                    sender.sendMessage("That does not exist.")
                    return true
                }

                try {
                    script = ScriptFactory.getInstance(file, sender)!!
                    script.parse()
                    script.execute()
                } catch (e: NullPointerException) {
                    sender.sendMessage("Unsupported type: .${file.extension}")
                } catch (e: GroovyRuntimeException) {
                    sender.sendMessage("Compilation error: ${e.message}")
                    e.printStackTrace()
                }
            } catch (e: ScriptEngineNotFound) {
                sender.sendMessage("Failed to execute: ${e.message}")
            } catch (e: Exception) {
                e.printStackTrace()
                sender.sendMessage("Failed to execute: ${e.message}")
            }
            return true
        }
        return false
    }

    private fun extractAsset() {
        val root = dataFolder.resolve("asset")

        if (root.isDirectory)
            return

        try {
            val sys = FileSystems.newFileSystem(file.toPath(), classLoader)
            val path = sys.getPath("asset")

            root.mkdirs()
            Files.walk(path, 1).forEach(Consumer {
                if (it == path) return@Consumer
                Files.copy(it, root.toPath().resolve(it.fileName.toString()))
                logger.info("Extracted file: ${it.fileName}")
            })
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadAsset() {
        assetFiles = dataFolder.resolve("asset").listFiles().orEmpty().toList()
    }

}