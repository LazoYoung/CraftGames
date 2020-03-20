package com.github.lazoyoung.craftgames.script

import com.github.lazoyoung.craftgames.Main
import org.bukkit.command.CommandSender
import org.codehaus.groovy.jsr223.GroovyScriptEngineFactory
import java.io.*
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.*
import javax.script.Compilable
import javax.script.CompiledScript
import javax.script.ScriptContext.ENGINE_SCOPE
import javax.script.SimpleScriptContext

class ScriptGroovy(val file: File, sender: CommandSender?) : ScriptBase(file, sender) {
    private val engine = GroovyScriptEngineFactory().scriptEngine
    private var script: CompiledScript? = null
    private val charset = Main.charset
    private val dir = file.resolveSibling("log").resolve(file.nameWithoutExtension)
    private val context = SimpleScriptContext()
    private lateinit var logFile: File
    private lateinit var errorFile: File

    init {
        //val shellConfig = CompilerConfiguration().addCompilationCustomizers(SandboxTransformer())
        //shellConfig.sourceEncoding = charset.name()
        dir.mkdirs()
    }

    override fun setVariable(name: String, obj: Any) {
        context.setAttribute(name, obj, ENGINE_SCOPE)
    }

    override fun parse() {
        script = (engine as Compilable).compile(reader)
        Main.logger.info("Script ${file.name} has been parsed.")
    }

    override fun execute() {
        val format = SimpleDateFormat("yyyy-MM-dd_HH-mm").format(Date.from(Instant.now()))
        logFile = dir.resolve("Evaluation_$format.txt")
        errorFile = dir.resolve("Stacktrace_$format.txt")
        logFile.createNewFile()
        errorFile.createNewFile()
        context.reader = BufferedReader(FileReader(file, charset))
        context.writer = PrintWriter(FileWriter(logFile, charset, true), true)
        val error = PrintWriter(FileWriter(errorFile, charset, true), true)

        try {
            if (script != null) {
                script!!.eval(context)
            } else {
                engine.eval(reader, context)
            }
        } catch (e: Exception) {
            val regex = "^Script\\d+\\.groovy$".toRegex()

            error.println("Stacktrace of script code:")
            e.cause?.stackTrace?.find { regex.matches(it.fileName ?: "") }?.let {
                error.println("   at ${file.name}:${it.lineNumber}")
            } ?: error.println("    No source of issue from ${file.name}")
            error.println()
            error.println()
            error.println("Stacktrace of plugin source:")
            e.cause?.printStackTrace(error)
            Main.logger.info("Failed to evaluate script: ${file.name}")
        } finally {
            context.reader.close()
            context.writer.close()
            error.close()
        }
        Main.logger.info("Script ${file.name} has been evaluated.")
    }
}