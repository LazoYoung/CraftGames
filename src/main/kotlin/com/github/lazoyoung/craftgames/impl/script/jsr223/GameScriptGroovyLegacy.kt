package com.github.lazoyoung.craftgames.impl.script.jsr223

import com.github.lazoyoung.craftgames.impl.Main
import com.github.lazoyoung.craftgames.api.script.GameScript
import org.codehaus.groovy.jsr223.GroovyScriptEngineFactory
import java.io.*
import javax.script.*
import javax.script.ScriptContext.ENGINE_SCOPE

class GameScriptGroovyLegacy(file: File) : GameScript(file, "^Script\\d+\\.groovy$".toRegex()) {
    private val engine = GroovyScriptEngineFactory().scriptEngine
    private var script: CompiledScript? = null
    private val reader = file.bufferedReader(Main.charset)
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
        val logFile = logRoot.resolve("Log_$format.txt")
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
        printDebug("Script parse is complete.")
    }

    override fun run() {
        if (script != null) {
            script!!.eval()
        } else {
            engine.eval(reader)
        }

        printDebug("Script execution is complete.")
    }

    override fun invokeFunction(name: String, vararg args: Any): Any? {
        if (script != null) {
            script!!.eval()
        } else {
            engine.eval(reader)
        }

        printDebug("Executing function \'$name\' in ${file.name}")
        return if (args.isEmpty()) {
            (script!!.engine as Invocable).invokeFunction(name)
        } else {
            (script!!.engine as Invocable).invokeFunction(name, args)
        }
    }

    override fun clear() {
        bindings.clear()
        logger?.close()
        context.writer.close()
        reader.close()
    }
}