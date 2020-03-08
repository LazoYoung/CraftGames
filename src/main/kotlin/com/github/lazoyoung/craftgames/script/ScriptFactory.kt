package com.github.lazoyoung.craftgames.script

import org.bukkit.command.CommandSender
import java.io.File

class ScriptFactory {
    companion object {
        fun getInstance(file: File, sender: CommandSender?) : ScriptBase? {
            return when (file.extension) {
                "groovy" -> ScriptGroovy(file, sender)
                else -> null
            }
        }
    }
}