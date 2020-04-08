package com.github.lazoyoung.craftgames.game.script

import com.github.lazoyoung.craftgames.Main
import com.github.lazoyoung.craftgames.game.script.groovy.ScriptGroovy
import com.github.lazoyoung.craftgames.game.script.jsr223.ScriptGroovyLegacy
import com.github.lazoyoung.craftgames.internal.exception.ScriptEngineNotFound
import java.io.File
import java.nio.file.Path

class ScriptFactory {
    enum class Engine {
        GROOVY, JSR223
    }

    companion object {
        /**
         * @throws ScriptEngineNotFound is thrown if either file extension or script engine is not valid.
         * @throws RuntimeException is thrown if plugin fails to load script
         */
        fun get(path: Path, main: File) : ScriptBase {
            val script = main.name
            val engine: Engine
            val engineName = Main.getConfig()
                    ?.getString("script.engine", "Groovy")?.toUpperCase()
                    ?: "GROOVY"

            try {
                engine = Engine.valueOf(engineName)
            } catch (e: IllegalArgumentException) {
                throw ScriptEngineNotFound("Unknown engine: $engineName")
            }

            return when (main.extension) {
                "groovy" -> try {
                    when (engine) {
                        Engine.GROOVY -> {
                            ScriptGroovy(path, main)
                        }
                        Engine.JSR223 -> {
                            ScriptGroovyLegacy(path, main)
                        }
                    }
                } catch (e: Exception) {
                    throw RuntimeException("Failed to load script: $script", e)
                }
                else -> throw ScriptEngineNotFound("Unsupported script: $script")
            }
        }
    }
}