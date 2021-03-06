package com.github.lazoyoung.craftgames.impl.script.groovy

import com.github.lazoyoung.craftgames.api.script.GameScript
import com.github.lazoyoung.craftgames.api.script.ScriptCompiler
import com.github.lazoyoung.craftgames.api.script.ScriptFactory
import com.github.lazoyoung.craftgames.impl.Main
import groovy.lang.Binding
import groovy.lang.Script
import groovy.transform.CompileStatic
import groovy.util.GroovyScriptEngine
import org.codehaus.groovy.control.customizers.ASTTransformationCustomizer
import org.codehaus.groovy.control.customizers.ImportCustomizer
import java.io.*
import java.net.URI

class GameScriptGroovy(
        engine: ScriptFactory.Engine,
        file: File,
        private val mode: ScriptCompiler
) : GameScript(engine, file, "^${file.name}$".toRegex()) {

    companion object {
        internal val registry = HashMap<URI, GameScriptGroovy>()
    }

    private var groovy = GroovyScriptEngine(arrayOf(file.toURI().toURL()))
    private val bindings = Binding()
    private val imports = ImportCustomizer()
    private var script: Script? = null
    private var printWriter: PrintWriter? = null

    init {
        registry[file.toURI()] = this
    }

    override fun bind(arg: String, obj: Any) {
        bindings.setProperty(arg, obj)
    }

    override fun startLogging() {
        try {
            val format = getFilenameFormat()
            val logFile = logRoot.resolve("Log_$format.txt")
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

        if (mode == ScriptCompiler.STATIC) {
            val extensionClass = GroovyASTExtension::class.qualifiedName!!
            val transform = ASTTransformationCustomizer(CompileStatic::class.java)
            transform.setAnnotationParameters(mapOf(Pair("extensions", extensionClass)))
            groovy.config.addCompilationCustomizers(transform)
        }

        imports.addStarImports("com.github.lazoyoung.craftgames.api")
        imports.addStarImports("com.github.lazoyoung.craftgames.api.event")
        imports.addStarImports("com.github.lazoyoung.craftgames.api.script")
        imports.addStarImports("com.github.lazoyoung.craftgames.api.shopkeepers")
        imports.addStarImports("com.github.lazoyoung.craftgames.api.tag.coordinate")
        imports.addStarImports("com.github.lazoyoung.craftgames.api.tag.item")
        imports.addImport("Module", "com.github.lazoyoung.craftgames.api.module.Module")
        imports.addImport("BukkitTask", "org.bukkit.scheduler.BukkitTask")
        imports.addImport("ChatColor", "org.bukkit.ChatColor")
        imports.addImport("Material", "org.bukkit.Material")
        imports.addImport("CommandSender", "org.bukkit.command.CommandSender")
        imports.addImport("EntityType", "org.bukkit.entity.EntityType")
        groovy.config.addCompilationCustomizers(imports)
        script = groovy.createScript(file.name, bindings)
    }

    override fun run() {
        if (script == null) {
            error("Cannot execute script which isn't parsed yet.")
        }

        script!!.run()
        printDebug("Script execution is complete.")
    }

    override fun invokeFunction(name: String, vararg args: Any): Any? {
        printDebug("Executing function \'$name\' in ${file.name}")
        return script?.invokeMethod(name, args)
    }

    override fun clear() {
        printWriter?.close()
        bindings.variables.clear()
        registry.clear()
    }

    fun addImports(star: Boolean, vararg imports: String) {
        if (star) {
            this.imports.addStarImports(*imports)
        } else {
            this.imports.addImports(*imports)
        }
    }
}