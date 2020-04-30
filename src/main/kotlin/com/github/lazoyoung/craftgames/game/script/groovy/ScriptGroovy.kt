package com.github.lazoyoung.craftgames.game.script.groovy

import com.github.lazoyoung.craftgames.Main
import com.github.lazoyoung.craftgames.game.script.ScriptBase
import groovy.lang.Binding
import groovy.lang.Script
import groovy.transform.CompileStatic
import groovy.util.GroovyScriptEngine
import groovy.util.ResourceException
import org.codehaus.groovy.control.customizers.ASTTransformationCustomizer
import org.codehaus.groovy.control.customizers.ImportCustomizer
import java.io.*
import java.net.URI
import java.nio.file.Path

class ScriptGroovy(
        path: Path,
        mainFile: File
) : ScriptBase(path, mainFile, regex = "^${mainFile.name}$".toRegex()) {

    companion object {
        internal val registry = HashMap<URI, ScriptGroovy>()
    }

    private var engine = GroovyScriptEngine(arrayOf(path.toUri().toURL()))
    private val bindings = Binding()
    private var script: Script? = null
    // TODO Separate external scripts output
    private var printWriter: PrintWriter? = null

    init {
        registry[mainFile.toURI()] = this
    }

    override fun bind(arg: String, obj: Any) {
        bindings.setVariable(arg, obj)
    }

    override fun startLogging() {
        try {
            val format = getFilenameFormat()
            val logFile = logPath.resolve("Log_$format.txt").toFile()
            val fileStream: FileOutputStream

            logFile.parentFile?.mkdirs()
            logFile.createNewFile()
            fileStream = FileOutputStream(logFile, true)
            printWriter = PrintWriter(BufferedWriter(OutputStreamWriter(fileStream, Main.charset)), true)
            bindings.setProperty("out", printWriter)
        } catch (e: Exception) {
            e.printStackTrace()
            Main.logger.severe("Failed to establish script logging system.")
        }
    }

    override fun print(message: String) {
        printWriter?.println(message)
        printWriter?.flush()
    }

    override fun parse() {
        super.parse()

        val extensionClass = GroovyASTExtension::class.qualifiedName!!
        val transform = ASTTransformationCustomizer(CompileStatic::class.java)
        val imports = ImportCustomizer()

        transform.setAnnotationParameters(mapOf(Pair("extensions", extensionClass)))
        imports.addStarImports("com.github.lazoyoung.craftgames.api")
        imports.addStarImports("com.github.lazoyoung.craftgames.event")
        imports.addImport("BukkitTask", "org.bukkit.scheduler.BukkitTask")
        imports.addImport("ChatColor", "org.bukkit.ChatColor")
        imports.addImport("Material", "org.bukkit.Material")
        engine.config.addCompilationCustomizers(imports)
        engine.config.addCompilationCustomizers(transform)
        script = engine.createScript(mainFile.name, bindings)
    }

    override fun execute() {
        if (script == null) {
            error("Cannot execute script which isn't parsed yet.")
        }

        script!!.run()
        printDebug("Script execution is complete.")
    }

    @Suppress("UNCHECKED_CAST")
    override fun execute(fileName: String, binding: Map<String, Any>): Any? {
        printDebug("Executing $fileName")

        try {
            val map = binding.toMutableMap()
            map.putAll(bindings.variables as Map<out String, Any>)

            return engine.createScript(fileName, Binding(map)).run()
        } catch (e: ResourceException) {
            throw IllegalArgumentException("This is not a groovy file: $fileName")
        }
    }

    override fun invokeFunction(name: String, vararg args: Any): Any? {
        printDebug("Executing function \'$name\' in ${mainFile.name}")
        return script?.invokeMethod(name, args)
    }

    override fun clear() {
        printWriter?.close()
        bindings.variables.clear()
        registry.clear()
    }
}