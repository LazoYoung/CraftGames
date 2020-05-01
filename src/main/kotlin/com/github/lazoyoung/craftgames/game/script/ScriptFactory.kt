package com.github.lazoyoung.craftgames.game.script

import com.github.lazoyoung.craftgames.Main
import com.github.lazoyoung.craftgames.game.script.groovy.GameScriptGroovy
import com.github.lazoyoung.craftgames.game.script.jsr223.GameScriptGroovyLegacy
import com.github.lazoyoung.craftgames.internal.exception.ScriptEngineNotFound
import java.io.File

class ScriptFactory {
    enum class Engine {
        GROOVY, JSR223
    }

    companion object {
        /**
         * @throws ScriptEngineNotFound is thrown if either file extension or script engine is not valid.
         * @throws RuntimeException is thrown if plugin fails to load script
         */
        fun get(file: File) : GameScript {
            val engine: Engine
            val engineName = Main.getConfig()
                    ?.getString("script.engine", "Groovy")?.toUpperCase()
                    ?: "GROOVY"

            try {
                engine = Engine.valueOf(engineName)
            } catch (e: IllegalArgumentException) {
                throw ScriptEngineNotFound("Unknown engine: $engineName")
            }

            return when (file.extension) {
                "groovy" -> try {
                    when (engine) {
                        Engine.GROOVY -> {
                            GameScriptGroovy(file)
                        }
                        Engine.JSR223 -> {
                            GameScriptGroovyLegacy(file)
                        }
                    }
                } catch (e: Exception) {
                    throw RuntimeException("Failed to load script: ${file.name}", e)
                }
                else -> throw ScriptEngineNotFound("Unsupported script: ${file.name}")
            }
        }
    }
}