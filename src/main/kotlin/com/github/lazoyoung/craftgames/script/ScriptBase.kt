package com.github.lazoyoung.craftgames.script

import com.github.lazoyoung.craftgames.Main
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.Reader
import java.nio.charset.Charset

abstract class ScriptBase(file: File, sender: CommandSender?) {
    protected val reader: Reader
    protected val charset: Charset
    protected val name: String
    private val sender: CommandSender
    private val ext: String

    init {
        charset = Charset.forName(Main.config.getString("files.encoding"))
        reader = BufferedReader(FileReader(file, charset))
        name = file.nameWithoutExtension
        ext = file.extension
        this.sender = sender ?: Bukkit.getConsoleSender()
    }

    abstract fun setVariable(name: String, obj: Any)
    abstract fun parse()
    abstract fun execute()
}