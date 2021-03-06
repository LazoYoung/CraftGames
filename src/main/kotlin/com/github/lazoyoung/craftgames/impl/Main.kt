package com.github.lazoyoung.craftgames.impl

import com.github.lazoyoung.craftgames.impl.command.*
import com.github.lazoyoung.craftgames.impl.game.Game
import com.github.lazoyoung.craftgames.impl.listener.MessangerListener
import com.github.lazoyoung.craftgames.impl.listener.ScriptListener
import com.github.lazoyoung.craftgames.impl.listener.ServerListener
import com.github.lazoyoung.craftgames.impl.util.DependencyUtil
import com.github.lazoyoung.craftgames.impl.util.FileUtil
import org.bukkit.Bukkit
import org.bukkit.command.CommandExecutor
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.event.HandlerList
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.io.IOException
import java.nio.charset.Charset
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.logging.Logger

class Main : JavaPlugin(), CommandExecutor {

    companion object {
        lateinit var instance: Main
            private set
        lateinit var pluginFolder: File
            private set
        lateinit var dataFolder: File
            private set
        lateinit var charset: Charset
            private set
        lateinit var logger: Logger
            private set
        lateinit var config: FileConfiguration
            private set

        internal fun getConfig(): FileConfiguration? {
            var config: FileConfiguration? = null

            try {
                val file = pluginFolder.resolve("config.yml")
                val reader = file.bufferedReader(charset)

                config = YamlConfiguration.loadConfiguration(reader)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            return config
        }
    }

    override fun onEnable() {
        val infoCmd = getCommand("craftgames")!!
        val gameCmd = getCommand("game")!!
        val ctCmd = getCommand("ctag")!!
        val itCmd = getCommand("itag")!!
        val joinCmd = getCommand("join")!!
        val leaveCmd = getCommand("leave")!!
        val forceJoinCmd = getCommand("forcejoin")!!
        val voteCmd = getCommand("mapvote")!!
        val kitCmd = getCommand("kit")!!
        val infoExecutor = InfoCommand()
        val gameExecutor = GameCommand()
        val ctExecutor = CoordTagCommand()
        val itExecutor = ItemTagCommand()
        val accessExecutor = GameAccessCommand()
        val voteExecutor = VoteCommand()
        val kitExecutor = KitCommand()
        val manager = Bukkit.getPluginManager()
        val messenger = Bukkit.getMessenger()
        instance = this
        Companion.logger = logger

        loadConfig()
        loadAsset()
        DependencyUtil.load(server.pluginManager)
        infoCmd.setExecutor(infoExecutor)
        gameCmd.setExecutor(gameExecutor)
        ctCmd.setExecutor(ctExecutor)
        itCmd.setExecutor(itExecutor)
        joinCmd.setExecutor(accessExecutor)
        leaveCmd.setExecutor(accessExecutor)
        forceJoinCmd.setExecutor(accessExecutor)
        voteCmd.setExecutor(voteExecutor)
        kitCmd.setExecutor(kitExecutor)
        infoCmd.tabCompleter = infoExecutor
        gameCmd.tabCompleter = gameExecutor
        ctCmd.tabCompleter = ctExecutor
        itCmd.tabCompleter = itExecutor
        joinCmd.tabCompleter = accessExecutor
        leaveCmd.tabCompleter = accessExecutor
        forceJoinCmd.tabCompleter = accessExecutor
        voteCmd.tabCompleter = voteExecutor
        kitCmd.tabCompleter = kitExecutor
        manager.registerEvents(ServerListener(), this)
        manager.registerEvents(ScriptListener(), this)
        messenger.registerOutgoingPluginChannel(this, "BungeeCord")
        messenger.registerIncomingPluginChannel(this, "BungeeCord", MessangerListener())
    }

    override fun onDisable() {
        // Close games
        Game.find().forEach { it.forceStop(async = false, error = false) }
        HandlerList.unregisterAll(this)
        server.scheduler.cancelTasks(this)
    }

    private fun loadConfig() {
        saveDefaultConfig()
        config.options().copyDefaults(true)
        pluginFolder = dataFolder
        Companion.dataFolder = pluginFolder.resolve("_data")

        val charset = config.getString("file-encoding") ?: "default"

        Companion.charset = try {
            if (charset.equals("default", true)) {
                Charset.defaultCharset()
            } else {
                Charset.forName(charset)
            }
        } catch (e: Exception) {
            val default = Charset.defaultCharset()
            logger.warning("Charset \'$charset\' is not available. Using system default: ${default.name()}")
            default
        }
    }

    private fun loadAsset() {
        val root = dataFolder
        val sys: FileSystem
        val source: Path
        val target: Path

        if (config.getBoolean("install-sample")) {
            logger.info("Installing sample files...")

            try {
                sys = FileSystems.newFileSystem(file.toPath(), classLoader)
                source = sys.getPath("/Sample")
                target = root.toPath()
            } catch (e: Exception) {
                e.printStackTrace()
                logger.severe("Unable to read jar files.")
                return
            }

            try {
                FileUtil.cloneFileTree(source, target, StandardCopyOption.REPLACE_EXISTING).handle {
                    result, t ->

                    if (result) {
                        logger.info("Sample files have been installed!")
                    } else {
                        t?.printStackTrace()
                        logger.warning("Failed to install Sample files.")
                    }

                    config.set("install-sample", false)
                }
            } catch (e: SecurityException) {
                e.printStackTrace()
                logger.severe("Access denied! Unable to install sample files.")
                return
            } catch (e: IOException) {
                e.printStackTrace()
                logger.severe("Error occurred! Unable to install sample files.")
                return
            }

            saveConfig()
        }
    }
}