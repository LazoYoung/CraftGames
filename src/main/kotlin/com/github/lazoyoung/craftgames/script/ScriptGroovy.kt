package com.github.lazoyoung.craftgames.script

import com.github.lazoyoung.craftgames.Main
import org.codehaus.groovy.jsr223.GroovyScriptEngineFactory
import java.io.*
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.*
import javax.script.*
import javax.script.ScriptContext.ENGINE_SCOPE

class ScriptGroovy(private val file: File) : ScriptBase(file) {
    private val engine = GroovyScriptEngineFactory().scriptEngine
    private var script: CompiledScript? = null
    private val charset = Main.charset
    private val dir = file.resolveSibling("log").resolve(file.nameWithoutExtension)
    private val reader = BufferedReader(FileReader(file, Main.charset))
    private val context = SimpleScriptContext()
    private val logger: PrintWriter
    private val bindings: Bindings

    init {
        val format = getFilenameFormat()
        val logFile = dir.resolve("Log_$format.txt")

        dir.mkdirs()
        logFile.createNewFile()
        bindings = engine.createBindings()
        logger = PrintWriter(FileWriter(logFile, charset, true), true)
        context.writer = logger
        engine.context = context
        engine.setBindings(bindings, ENGINE_SCOPE)
    }

    override fun getBindings(): Bindings {
        return engine.getBindings(ENGINE_SCOPE)
    }

    override fun parse() {
        script = (engine as Compilable).compile(reader)
    }

    override fun execute() {

        try {
            if (script != null) {
                script!!.eval()
            } else {
                engine.eval(reader)
            }

            logger.println("Script \'$name\' has been executed.")
        } catch (e: Exception) {
            writeStackTrace(e)
            Main.logger.warning("Failed to evaluate script: ${file.name}")
            return
        }
    }

    override fun invokeFunction(name: String, args: Array<Any>?): Any? {
        val result: Any?

        try {
            if (script != null) {
                script!!.eval()
            } else {
                engine.eval(reader)
            }

            result = if (args == null) {
                (script!!.engine as Invocable).invokeFunction(name)
            } else {
                (script!!.engine as Invocable).invokeFunction(name, args)
            }
            logger.println("Function \'$name\' inside ${this.file.name} has been invoked.")
        } catch (e: Exception) {
            writeStackTrace(e)
            Main.logger.warning("Failed to invoke function: $name")
            return null
        }
        return result
    }

    override fun closeIO() {
        context.writer.close()
        reader.close()
    }

    private fun writeStackTrace(e: Exception) {
        val format = getFilenameFormat()
        val errorFile = dir.resolve("Error_$format.txt")
        val error = PrintWriter(FileWriter(errorFile, charset, true), true)
        val regex = "^Script\\d+\\.groovy$".toRegex()

        error.println("Stacktrace of script code:")
        if (e is NoSuchMethodException) {
            val modulePackage = "com.github.lazoyoung.craftgames.module."
            e.localizedMessage.split(' ').firstOrNull { it.startsWith(modulePackage) }?.let {
                val label = it.split('.').last()
                error.println("    $label <- Plugin can't resolve this function.")
            }
        } else {
            e.cause?.stackTrace?.find { regex.matches(it.fileName ?: "") }?.let {
                error.println("   at ${file.name}:${it.lineNumber}")
            } ?: error.println("    N/A")
        }
        error.println()
        error.println()
        error.println("Stacktrace of plugin source:")
        e.printStackTrace(error)
        error.close()
    }

    private fun getFilenameFormat(): String {
        return SimpleDateFormat("yyyy-MM-dd_HH-mm").format(Date.from(Instant.now()))
    }
}