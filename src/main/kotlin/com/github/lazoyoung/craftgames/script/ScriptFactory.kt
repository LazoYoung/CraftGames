package com.github.lazoyoung.craftgames.script

import com.github.lazoyoung.craftgames.exception.ScriptEngineNotFound
import java.io.File

class ScriptFactory {
    companion object {
        /**
         * @throws ScriptEngineNotFound thrown if file extension is not recognized.
         */
        fun getInstance(file: File) : ScriptBase {
            return when (file.extension) {
                "groovy" -> ScriptGroovy(file)
                else -> throw ScriptEngineNotFound("Unsupported type: .${file.extension}")
            }
        }
    }
}