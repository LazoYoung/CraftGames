package com.github.lazoyoung.craftgames.script

import com.github.lazoyoung.craftgames.util.FileUtil
import com.github.lazoyoung.craftgames.Main
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
    private val context = SimpleScriptContext()
    private val logger: PrintWriter
    private val bindings: Bindings

    init {
        val format = getFilenameFormat()
        val logFile = dir.resolve("Log_$format.txt")

        dir.mkdirs()
        logFile.createNewFile()
        bindings = engine.createBindings()
        logger = PrintWriter(FileUtil.getBufferedWriter(logFile, true), true)
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

    override fun execute(script: String) {
        try {
            engine.eval(script)
        } catch (e: ScriptException) {
            e.printStackTrace()
            Main.logger.warning("Failed to evaluate internal script.")
        }
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
        }
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

        logger.println("Function \'$name\' inside ${this.file.name} has been invoked.")
        return result
    }

    override fun closeIO() {
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
        return errorFile.toPath()
    }

    private fun getFilenameFormat(): String {
        return SimpleDateFormat("yyyy-MM-dd_HHmmss").format(Date.from(Instant.now()))
    }
}