package com.github.lazoyoung.craftgames

import groovy.lang.GroovyShell
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.plugin.java.JavaPlugin
import org.codehaus.groovy.control.CompilationFailedException
import org.codehaus.groovy.control.CompilerConfiguration
import org.kohsuke.groovy.sandbox.GroovyValueFilter
import org.kohsuke.groovy.sandbox.SandboxTransformer
import java.io.File
import java.io.FileReader
import java.nio.file.FileSystems
import java.nio.file.Files
import java.util.function.Consumer
import javax.script.ScriptEngineManager

class Main : JavaPlugin(), CommandExecutor {

    companion object {
        var assetFiles: List<File> = ArrayList()
    }

    override fun onEnable() {
        saveDefaultConfig()
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
            val file: File? = assetFiles.singleOrNull { it.name == name }
            val reader: FileReader

            if (file == null) {
                sender.sendMessage("The asset does not exist.")
                return true
            }

            reader = FileReader(file)
            if (file.extension == "groovy") {
                loadGroovyShell(reader, name, sender)
            } else {
                loadJSR223(reader, name, sender)
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

    private fun loadGroovyShell(reader: FileReader, name: String, sender: CommandSender) {
        val shell = GroovyShell(CompilerConfiguration().addCompilationCustomizers(SandboxTransformer()))
        val filter = object : GroovyValueFilter() {
            // TODO Intercept abusive actions
        }

        sender.sendMessage("Loading script engine: GroovyShell")
        try {
            filter.register()
            shell.evaluate(reader)
            filter.unregister()
        } catch (e: CompilationFailedException) {
            e.printStackTrace()
            sender.sendMessage("Failed to compile script: $name")
            return
        }
        sender.sendMessage("Script '$name' has been executed.")
    }

    private fun loadJSR223(reader: FileReader, name: String, sender: CommandSender) {
        val engine = ScriptEngineManager().getEngineByName("groovy")
        val factory = engine.factory

        sender.sendMessage("Executing script '$name' with engine: ${factory.engineName} ${factory.engineVersion}")
        sender.sendMessage("This is not supported yet.")

        // TODO JSR223 script handling not implemented.
    }

}