package com.github.lazoyoung.craftgames.game.script.jsr223

import com.github.lazoyoung.craftgames.Main
import com.github.lazoyoung.craftgames.game.script.ScriptBase
import org.codehaus.groovy.jsr223.GroovyScriptEngineFactory
import java.io.*
import java.nio.file.Files
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
    // TODO Separate external script output
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
        printDebug("Script parse is complete.")
    }

    override fun execute() {
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

        printDebug("Executing function \'$name\' in ${mainFile.name}")
        return if (args.isEmpty()) {
            (script!!.engine as Invocable).invokeFunction(name)
        } else {
            (script!!.engine as Invocable).invokeFunction(name, args)
        }
    }

    override fun execute(fileName: String, binding: Map<String, Any>): Any? {
        val file = path.resolve(fileName)

        require(Files.isRegularFile(file) && fileName.endsWith(".groovy")) {
            "This is not a groovy file: $fileName"
        }

        val tmpEngine = GroovyScriptEngineFactory().scriptEngine
        val tmpContext = SimpleScriptContext()
        val tmpBindings = tmpEngine.createBindings()
        tmpEngine.setBindings(tmpBindings, ENGINE_SCOPE)
        tmpEngine.context = tmpContext
        context.writer = logger
        tmpBindings.putAll(bindings.toMap())
        tmpBindings.putAll(binding)

        printDebug("Executing function \'$name\' in $fileName")
        tmpEngine.eval(file.toFile().bufferedReader(), SimpleBindings(map))
    }

    override fun clear() {
        bindings.clear()
        logger?.close()
        context.writer.close()
        reader.close()
    }
}