package com.github.lazoyoung.craftgames.game.script.groovy

import com.github.lazoyoung.craftgames.Main
import com.github.lazoyoung.craftgames.game.script.ScriptBase
import com.github.lazoyoung.craftgames.internal.exception.ScriptNotParsed
import groovy.lang.Binding
import groovy.lang.Script
import groovy.transform.CompileStatic
import groovy.util.GroovyScriptEngine
import org.codehaus.groovy.control.customizers.ASTTransformationCustomizer
import org.codehaus.groovy.control.customizers.ImportCustomizer
import java.io.*
import java.nio.file.Path

class ScriptGroovy(
        path: Path,
        mainFile: File
) : ScriptBase(path, mainFile, regex = "^${mainFile.name}$".toRegex()) {
    private var engine = GroovyScriptEngine(arrayOf(path.toUri().toURL()))
    private val bindings = Binding()
    private var script: Script? = null
    private var printWriter: PrintWriter? = null

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
    }

    override fun parse() {
        val extensionClass = GroovyASTExtension::class.qualifiedName!!
        val transform = ASTTransformationCustomizer(CompileStatic::class.java)
        val imports = ImportCustomizer()

        transform.setAnnotationParameters(mapOf(Pair("extensions", extensionClass)))
        imports.addStarImports("com.github.lazoyoung.craftgames.api")
        engine.config.addCompilationCustomizers(imports)
        engine.config.addCompilationCustomizers(transform)
        script = engine.createScript(mainFile.name, bindings)
    }

    override fun execute() {
        if (script == null) {
            throw ScriptNotParsed()
        }

        script!!.run()
    }

    override fun invokeFunction(name: String, args: Array<Any>?): Any? {
        return script?.invokeMethod(name, args)
    }

    override fun clear() {
        printWriter?.close()
        bindings.variables.clear()
    }
}