package com.github.lazoyoung.craftgames.game.script

import com.github.lazoyoung.craftgames.Main
import com.github.lazoyoung.craftgames.internal.exception.ScriptNotParsed
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
     * If you want to use invokeFunction(), you must do parse() in advance!
     */
    open fun parse() {
        val tmpPath = Files.createTempFile(mainFile.name, null)
        val reader = mainFile.copyTo(tmpPath.toFile(), overwrite = true).bufferedReader(Main.charset)
        val writer = mainFile.bufferedWriter(Main.charset)
        var line = reader.readLine()

        while (line != null) {
            val replaced = line.replace(Regex("\\p{Cf}"), "")
            line = reader.readLine()

            writer.write(replaced)
            writer.newLine()
            writer.flush()
        }

        reader.close()
        writer.close()
        Files.delete(tmpPath)
    }

    /**
     * Executes the script by the file passed to constructor.
     *
     * @throws ScriptNotParsed is thrown if engine requires the script to be [parse]d before execution.
     * @throws Exception
     */
    abstract fun execute()

    /**
     * Invokes the specific function defined at top-most context in the COMPILED SCRIPT.
     *
     * @param name of the function to be invoked.
     * @param args Array of argument objects to be passed.
     * @return The invocation result.
     * @throws ScriptNotParsed is thrown if script isn't [parse]d yet.
     * @throws Exception
     */
    abstract fun invokeFunction(name: String, args: Array<Any>? = null): Any?

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