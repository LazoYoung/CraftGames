package com.github.lazoyoung.craftgames.script

import java.io.File
import java.nio.file.Path
import javax.script.Bindings

abstract class ScriptBase(file: File) {
    protected val name: String = file.nameWithoutExtension

    abstract fun getBindings(): Bindings

    /**
     * Compiles the script to achieve efficient executions in the future.
     *
     * If you want to use invokeFunction(), you must do parse() in advance!
     */
    abstract fun parse()

    /**
     * Execute the script passed to the argument.
     * @param script The source of the script
     */
    abstract fun execute(script: String)

    /**
     * Executes the script by the file passed to constructor.
     */
    abstract fun execute()

    /**
     * Invokes the specific function defined at top-most context in the COMPILED SCRIPT.
     *
     * @param name of the function to be invoked.
     * @param args Array of argument objects to be passed.
     * @return The invocation result.
     * @throws javax.script.ScriptException
     * @throws NoSuchMethodException
     */
    abstract fun invokeFunction(name: String, args: Array<Any>? = null): Any?

    internal abstract fun writeStackTrace(e: Exception): Path

    internal abstract fun closeIO()
}