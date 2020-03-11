package com.github.lazoyoung.craftgames.script

import com.github.lazoyoung.craftgames.exception.ScriptEngineNotFound
import org.bukkit.command.CommandSender
import java.io.File

class ScriptFactory {
    companion object {
        /**
         * @throws ScriptEngineNotFound thrown if file extension is not recognized.
         */
        fun getInstance(file: File, sender: CommandSender?) : ScriptBase {
            return when (file.extension) {
                "groovy" -> ScriptGroovy(file, sender)
                else -> throw ScriptEngineNotFound("Unsupported type: .${file.extension}")
            }
        }
    }
}