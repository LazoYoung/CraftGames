package com.github.lazoyoung.craftgames.game.script.jsr223

import com.github.lazoyoung.craftgames.Main
import com.github.lazoyoung.craftgames.game.script.ScriptBase
import org.codehaus.groovy.jsr223.GroovyScriptEngineFactory
import java.io.*
import java.nio.file.Path
import javax.script.*
import javax.script.ScriptContext.ENGINE_SCOPE

class ScriptGroovyLegacy(
        path: Path,
        mainFile: File
) : ScriptBase(path, mainFile, regex = "^Script\\d+\\.groovy$".toRegex()) {
    private val engine = GroovyScriptEngineFactory().scriptEngine
    private var script: CompiledScript? = null
    private val reader = mainFile.bufferedReader(Main.charset)
    private var logger: PrintWriter? = null
    private val context = SimpleScriptContext()
    private val bindings: Bindings

    init {
        bindings = engine.createBindings()
        engine.context = context
        engine.setBindings(bindings, ENGINE_SCOPE)
    }

    override fun bind(arg: String, obj: Any) {
        bindings[arg] = obj
    }

    override fun startLogging() {
        val format = getFilenameFormat()
        val logFile = logPath.resolve("Log_$format.txt").toFile()
        val writer = OutputStreamWriter(FileOutputStream(logFile, true), Main.charset)
        logFile.mkdirs()
        logFile.createNewFile()
        logger = PrintWriter(BufferedWriter(writer), true)
        context.writer = logger
    }

    override fun print(message: String) {
        logger?.println(message)
    }

    override fun parse() {
        super.parse()

        script = (engine as Compilable).compile(reader)
    }

    fun execute(script: String) {
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

    override fun clear() {
        bindings.clear()
        logger?.close()
        context.writer.close()
        reader.close()
    }
}