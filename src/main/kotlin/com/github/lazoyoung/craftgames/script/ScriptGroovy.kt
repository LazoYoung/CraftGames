package com.github.lazoyoung.craftgames.script

import com.github.lazoyoung.craftgames.Main
import com.github.lazoyoung.craftgames.util.FileUtil
import org.codehaus.groovy.jsr223.GroovyScriptEngineFactory
import java.io.BufferedReader
import java.io.File
import java.io.PrintWriter
import java.nio.file.Path
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.*
import javax.script.*
import javax.script.ScriptContext.ENGINE_SCOPE

class ScriptGroovy(private val file: File) : ScriptBase(file) {
    private val engine = GroovyScriptEngineFactory().scriptEngine
    private var script: CompiledScript? = null
    private val dir = file.resolveSibling("log").resolve(file.nameWithoutExtension)
    private val reader = BufferedReader(FileUtil.getBufferedReader(file))
    private var logger: PrintWriter? = null
    private val context = SimpleScriptContext()
    private val bindings: Bindings

    init {
        bindings = engine.createBindings()
        engine.context = context
        engine.setBindings(bindings, ENGINE_SCOPE)
    }

    override fun getBindings(): Bindings {
        return engine.getBindings(ENGINE_SCOPE)
    }

    override fun startLogging() {
        val format = getFilenameFormat()
        val logFile = dir.resolve("Log_$format.txt")
        dir.mkdirs()
        logFile.createNewFile()
        logger = PrintWriter(FileUtil.getBufferedWriter(logFile, true), true)
        context.writer = logger
    }

    override fun getLogger(): PrintWriter? {
        return logger
    }

    override fun parse() {
        script = (engine as Compilable).compile(reader)
    }

    override fun execute(script: String) {
        try {
            engine.eval(script)
        } catch (e: ScriptException) {
            e.printStackTrace()
            Main.logger.severe("Failed to evaluate internal script.")
        }
    }

    override fun execute() {
        if (script != null) {
            script!!.eval()
        } else {
            engine.eval(reader)
        }

        logger?.println("Script evaluation complete.")
    }

    override fun invokeFunction(name: String, args: Array<Any>?): Any? {
        if (script != null) {
            script!!.eval()
        } else {
            engine.eval(reader)
        }

        val result = if (args == null) {
            (script!!.engine as Invocable).invokeFunction(name)
        } else {
            (script!!.engine as Invocable).invokeFunction(name, args)
        }

        logger?.println("Function \'$name\' execution complete.")
        return result
    }

    override fun closeIO() {
        logger?.close()
        context.writer.close()
        reader.close()
    }

    override fun writeStackTrace(e: Exception): Path {
        val format = getFilenameFormat()
        val errorFile = dir.resolve("Error_$format.txt")
        val error = PrintWriter(FileUtil.getBufferedWriter(errorFile, true), true)
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
        Main.logger.severe("Failed to evaluate \'${file.name}\' script!")
        Main.logger.severe("Stacktrace location: ${file.toPath()}")
        return errorFile.toPath()
    }

    private fun getFilenameFormat(): String {
        return SimpleDateFormat("yyyy-MM-dd_HHmmss").format(Date.from(Instant.now()))
    }
}