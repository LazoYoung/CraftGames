package com.github.lazoyoung.craftgames

import com.github.lazoyoung.craftgames.command.*
import com.github.lazoyoung.craftgames.game.Game
import com.github.lazoyoung.craftgames.internal.listener.ScriptListener
import com.github.lazoyoung.craftgames.internal.listener.ServerListener
import com.github.lazoyoung.craftgames.internal.util.FileUtil
import com.github.lazoyoung.craftgames.internal.util.MessengerUtil
import com.github.lazoyoung.craftgames.internal.util.enum.Dependency
import org.bukkit.Bukkit
import org.bukkit.command.CommandExecutor
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
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
        val joinCmd = getCommand("join")!!
        val leaveCmd = getCommand("leave")!!
        val forceJoinCmd = getCommand("forcejoin")!!
        val voteCmd = getCommand("mapvote")!!
        val kitCmd = getCommand("kit")!!
        val infoExecutor = InfoCommand()
        val gameExecutor = GameCommand()
        val ctExecutor = CoordtagCommand()
        val accessExecutor = GameAccessCommand()
        val voteExecutor = VoteCommand()
        val kitExecutor = KitCommand()
        val manager = Bukkit.getPluginManager()
        val messenger = Bukkit.getMessenger()
        instance = this
        Main.logger = logger

        loadConfig()
        loadAsset()
        Dependency.loadAll(server.pluginManager)
        infoCmd.setExecutor(infoExecutor)
        gameCmd.setExecutor(gameExecutor)
        ctCmd.setExecutor(ctExecutor)
        joinCmd.setExecutor(accessExecutor)
        leaveCmd.setExecutor(accessExecutor)
        forceJoinCmd.setExecutor(accessExecutor)
        voteCmd.setExecutor(voteExecutor)
        kitCmd.setExecutor(kitExecutor)
        infoCmd.tabCompleter = infoExecutor
        gameCmd.tabCompleter = gameExecutor
        ctCmd.tabCompleter = ctExecutor
        joinCmd.tabCompleter = accessExecutor
        leaveCmd.tabCompleter = accessExecutor
        forceJoinCmd.tabCompleter = accessExecutor
        voteCmd.tabCompleter = voteExecutor
        kitCmd.tabCompleter = kitExecutor
        manager.registerEvents(ServerListener(), this)
        manager.registerEvents(ScriptListener(), this)
        messenger.registerOutgoingPluginChannel(this, "BungeeCord")
        messenger.registerIncomingPluginChannel(this, "BungeeCord", MessengerUtil())
    }

    override fun onDisable() {
        // Close games
        Game.find().forEach { it.forceStop(async = false, error = false) }
    }

    private fun loadConfig() {
        saveDefaultConfig()
        config.options().copyDefaults(true)
        pluginFolder = dataFolder
        Main.dataFolder = pluginFolder.resolve("_data")

        val charset = config.getString("file-encoding") ?: "default"

        Main.charset = try {
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