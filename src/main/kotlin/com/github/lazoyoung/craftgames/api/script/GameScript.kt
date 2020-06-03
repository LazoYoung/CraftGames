package com.github.lazoyoung.craftgames.api.script

import com.github.lazoyoung.craftgames.impl.Main
import com.github.lazoyoung.craftgames.impl.game.module.ModuleService
import java.io.*
import java.nio.file.Files
import java.nio.file.Path
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.*

abstract class GameScript(
        val engine: ScriptFactory.Engine,
        internal val file: File,
        private val regex: Regex
) {
    var debug = Main.getConfig()?.getBoolean("script.debug", false) ?: false
    protected val name: String = file.nameWithoutExtension
    protected val logRoot: File = requireNotNull(file.parentFile).resolve("log")

    abstract fun bind(arg: String, obj: Any)

    abstract fun print(message: String)

    fun printDebug(message: String) {
        if (debug) {
            print(message)
        }
    }

    /**
     * Compiles the script to achieve efficient executions in the future.
     */
    open fun parse() {
        val tmpFile = Files.createTempFile(file.name, null)
        val reader = file.copyTo(tmpFile.toFile(), overwrite = true).bufferedReader(Main.charset)
        val writer = file.bufferedWriter(Main.charset)
        var line = reader.readLine()

        // Get rid of invisible characters
        while (line != null) {
            val replaced = line.replace(Regex("\\p{Cf}"), "")
            line = reader.readLine()

            writer.write(replaced)
            writer.newLine()
            writer.flush()
        }

        reader.close()
        writer.close()
        Files.delete(tmpFile)
    }

    /**
     * Executes entire script.
     *
     * Some script engine specification requires [ModuleService.injectModules] before execution.
     *
     * @throws IllegalStateException is thrown if this engine
     * requires the script to have [modules injected][ModuleService.injectModules] before execution.
     * @throws Exception Any exception may occur during script evaluation.
     */
    abstract fun run()

    /**
     * Invokes specific function defined at top-most context in the COMPILED SCRIPT.
     *
     * Some script engine specification requires [ModuleService.injectModules] before execution.
     *
     * @param name Name of the function to invoke.
     * @param args Array of arguments passed to this function.
     * @return The invocation result.
     * @throws IllegalStateException is thrown if this engine
     * requires the script to have [modules injected][ModuleService.injectModules] before execution.
     * @throws Exception Any exception may occur during script evaluation.
     */
    abstract fun invokeFunction(name: String, vararg args: Any): Any?

    internal abstract fun startLogging()

    internal abstract fun clear()

    internal fun writeStackTrace(t: Throwable): Path {
        val format = getFilenameFormat()
        val errorFile = logRoot.resolve("Error_$format.txt")
        val writer = OutputStreamWriter(FileOutputStream(errorFile, true), Main.charset)
        val error = PrintWriter(BufferedWriter(writer))

        error.println("Stacktrace of script code:")
        if (t is NoSuchMethodException) {
            val modulePackage = "com.github.lazoyoung.craftgames.impl.game.module."
            t.localizedMessage.split(' ').firstOrNull { it.startsWith(modulePackage) }?.let {
                val label = it.split('.').last()
                error.println("    $label <- Plugin can't resolve this function.")
            }
        } else {
            t.stackTrace.plus(t.cause?.stackTrace ?: emptyArray())
                    .find { regex.matches(it.fileName ?: "") }
                    ?.let { error.println("   at ${file.name}:${it.lineNumber}") }
                    ?: error.println("    N/A")
        }
        error.println()
        error.println()
        error.println("Stacktrace of plugin source:")
        t.printStackTrace(error)
        t.cause?.printStackTrace(error)
        error.close()
        Main.logger.severe("Failed to evaluate ${file.name}")
        Main.logger.severe("Error stacktrace generated: ${errorFile.path}")
        return errorFile.toPath()
    }

    internal fun getFilenameFormat(): String {
        return SimpleDateFormat("yyyy-MM-dd_HHmmss").format(Date.from(Instant.now()))
    }
}