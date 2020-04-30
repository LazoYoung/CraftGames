package com.github.lazoyoung.craftgames.game.script

import com.github.lazoyoung.craftgames.Main
import java.io.*
import java.nio.file.Files
import java.nio.file.Path
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.*

abstract class ScriptBase(
        protected val path: Path,
        protected val mainFile: File,
        private val regex: Regex
) {
    var debug = Main.getConfig()?.getBoolean("script.debug", false) ?: false
    protected val name: String = mainFile.nameWithoutExtension
    protected val logPath: Path = path.resolve("log")

    abstract fun bind(arg: String, obj: Any)

    abstract fun startLogging()

    abstract fun print(message: String)

    fun printDebug(message: String) {
        if (debug) {
            print(message)
        }
    }

    /**
     * Compiles the script to achieve efficient executions in the future.
     *
     * If you want to use invokeFunction(), call this function in advance!
     */
    open fun parse() {
        val tmpFile = Files.createTempFile(mainFile.name, null)
        val reader = mainFile.copyTo(tmpFile.toFile(), overwrite = true).bufferedReader(Main.charset)
        val writer = mainFile.bufferedWriter(Main.charset)
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
     * Executes the script by the file passed to constructor.
     *
     * @throws IllegalStateException is thrown if this engine
     * requires the script to be [parse]d before execution.
     * @throws Exception
     */
    abstract fun execute()

    /**
     * Invokes specific function defined at top-most context in the COMPILED SCRIPT.
     *
     * @param name Name of the function to invoke.
     * @param args Array of arguments passed to this function.
     * @return The invocation result.
     * @throws IllegalStateException is thrown if this engine
     * requires the script to be [parse]d before execution.
     * @throws Exception Any exception may occur during script evaluation.
     */
    abstract fun invokeFunction(name: String, vararg args: Any): Any?

    /**
     * Invokes specific function defined at top-most context in the given script.
     *
     * TODO /game execute (fileName) [key1:val1, key2:val2, ...]
     *
     * @param fileName Name of the script file to execute.
     * @param binding Map paired with String(variable name) and Object(value) will be passed to script context.
     * @return The execution result.
     * @throws IllegalArgumentException is thrown if [fileName] doesn't indicate a script file.
     * @throws Exception Any exception may occur during script evaluation.
     */
    abstract fun execute(fileName: String, binding: Map<String, Any>): Any?

    abstract fun clear()

    internal fun writeStackTrace(e: Exception): Path {
        val format = getFilenameFormat()
        val errorPath = logPath.resolve("Error_$format.txt")
        val writer = OutputStreamWriter(FileOutputStream(errorPath.toFile(), true), Main.charset)
        val error = PrintWriter(BufferedWriter(writer))

        error.println("Stacktrace of script code:")
        if (e is NoSuchMethodException) {
            val modulePackage = "com.github.lazoyoung.craftgames.game.module."
            e.localizedMessage.split(' ').firstOrNull { it.startsWith(modulePackage) }?.let {
                val label = it.split('.').last()
                error.println("    $label <- Plugin can't resolve this function.")
            }
        } else {
            e.stackTrace.plus(e.cause?.stackTrace ?: emptyArray())
                    .find { regex.matches(it.fileName ?: "") }
                    ?.let { error.println("   at ${mainFile.name}:${it.lineNumber}") }
                    ?: error.println("    N/A")
        }
        error.println()
        error.println()
        error.println("Stacktrace of plugin source:")
        e.printStackTrace(error)
        e.cause?.printStackTrace(error)
        error.close()
        Main.logger.severe("Failed to evaluate \'${mainFile.name}\' script!")
        Main.logger.severe("Stacktrace location: $errorPath")
        return errorPath
    }

    internal fun getFilenameFormat(): String {
        return SimpleDateFormat("yyyy-MM-dd_HHmmss").format(Date.from(Instant.now()))
    }
}