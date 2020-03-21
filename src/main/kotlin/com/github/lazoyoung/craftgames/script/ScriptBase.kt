package com.github.lazoyoung.craftgames.script

import java.io.File
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
     * Executes the script.
     */
    abstract fun execute()

    /**
     * Invokes the specific function defined at top-most context in the COMPILED SCRIPT.
     *
     * @param name of the function to be invoked.
     * @param args Array of argument objects to be passed.
     * @return The invocation result.
     */
    abstract fun invokeFunction(name: String, args: Array<Any>? = null): Any?

    abstract fun closeIO()
}