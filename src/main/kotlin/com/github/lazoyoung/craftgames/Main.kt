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
import java.io.IOException
import java.nio.charset.Charset
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.util.*

class Main : JavaPlugin(), CommandExecutor {

    companion object {
        lateinit var config: FileConfiguration
            private set
        lateinit var scriptFiles: List<File> // TODO Deprecate
            internal set
        lateinit var instance: Main
            private set
        lateinit var charset: Charset
            private set
    }

    override fun onEnable() {
        instance = this

        loadConfig()
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
            val file: File? = scriptFiles.singleOrNull { it.nameWithoutExtension == name }
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

    private fun loadConfig() {
        saveDefaultConfig()
        Main.config = config
        charset = Charset.forName(config.getString("file-encoding"))
    }

    private fun loadAsset() {
        val root = dataFolder
        val sys: FileSystem
        val source: Path
        val target: Path

        if (!config.getBoolean("install-sample"))
            return

        try {
            sys = FileSystems.newFileSystem(file.toPath(), classLoader)
            source = sys.getPath("Sample")
            target = root.toPath()
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
            logger.severe("Failed to install sample configuration.")
            return
        }

        try {
            Files.walkFileTree(source, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Int.MAX_VALUE, object : SimpleFileVisitor<Path>() {
                override fun preVisitDirectory(dir: Path?, attr: BasicFileAttributes?): FileVisitResult {
                    if (dir == null)
                        return FileVisitResult.CONTINUE

                    val targetDir = target.resolve(source.relativize(dir).toString())
                    try {
                        Files.copy(dir, targetDir)
                    } catch (e: FileAlreadyExistsException) {
                        if (!Files.isDirectory(targetDir)) {
                            e.printStackTrace()
                        }
                    }
                    return FileVisitResult.CONTINUE
                }

                override fun visitFile(file: Path?, attrs: BasicFileAttributes?): FileVisitResult {
                    if (file != null) {
                        val targetPath = target.resolve(source.relativize(file).toString())
                        logger.info("Copying ${file.fileName} to ${targetPath.normalize()}")
                        try {
                            Files.copy(file, targetPath)
                        } catch (e: java.lang.Exception) {
                            e.printStackTrace()
                        }
                    }
                    return FileVisitResult.CONTINUE
                }

                override fun visitFileFailed(file: Path?, exc: IOException?): FileVisitResult {
                    exc?.printStackTrace()
                    logger.warning("Failed to copy: ${file?.toRealPath().toString()}")
                    return FileVisitResult.TERMINATE
                }
            })
        } catch (e: SecurityException) {
            e.printStackTrace()
            logger.severe("Access denied! Unable to install sample files.")
            return
        } catch (e: IOException) {
            e.printStackTrace()
            logger.severe("Error occurred! Unable to install sample files.")
            return
        }

        logger.info("Sample files have been installed!")
        config.set("install-sample", false)
        saveConfig()
    }
}