package com.github.lazoyoung.craftgames.api.script

import com.github.lazoyoung.craftgames.impl.Main
import com.github.lazoyoung.craftgames.impl.exception.ScriptEngineNotFound
import com.github.lazoyoung.craftgames.impl.script.groovy.GameScriptGroovy
import com.github.lazoyoung.craftgames.impl.script.jsr223.GameScriptGroovyLegacy
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

class ScriptFactory {
    enum class Engine(vararg val extension: String) {
        GROOVY("groovy") {
            override fun load(file: File, mode: ScriptCompiler): GameScriptGroovy {
                return GameScriptGroovy(this, file, mode)
            }
        },

        JSR223("groovy") {
            override fun load(file: File, mode: ScriptCompiler): GameScriptGroovyLegacy {
                return GameScriptGroovyLegacy(this, file)
            }
        };

        abstract fun load(file: File, mode: ScriptCompiler): GameScript
    }

    companion object {
        /**
         * Get [GameScript] instance by existing file's [path].
         *
         * @param path Path to script file.
         * @param mode Compiler to interpret the script. (No effect on legacy script engine)
         * @throws IllegalArgumentException is thrown if file indicated by [path] doesn't exist.
         * @throws ScriptEngineNotFound is thrown if either file extension or script engine is not valid.
         * @throws RuntimeException is thrown if plugin fails to load script
         */
        fun get(path: Path, mode: ScriptCompiler): GameScript {
            val file = path.toFile()
            val engine: Engine
            val engineName = Main.getConfig()
                    ?.getString("script.engine", "Groovy")?.toUpperCase()
                    ?: "GROOVY"

            require(Files.isRegularFile(path)) {
                "File not found: $path"
            }

            try {
                engine = Engine.valueOf(engineName)
            } catch (e: IllegalArgumentException) {
                throw ScriptEngineNotFound("Unknown engine: $engineName")
            }

            if (!engine.extension.contains(file.extension)) {
                throw ScriptEngineNotFound("Unsupported type of script: ${file.name}")
            }

            try {
                return engine.load(file, mode)
            } catch (e: Exception) {
                throw RuntimeException("Failed to load script: ${file.name}", e)
            }
        }
    }
}