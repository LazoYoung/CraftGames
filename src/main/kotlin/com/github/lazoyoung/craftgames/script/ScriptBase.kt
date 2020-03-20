package com.github.lazoyoung.craftgames.script

import com.github.lazoyoung.craftgames.Main
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.Reader

abstract class ScriptBase(file: File, sender: CommandSender?) {
    protected val reader: Reader
    protected val name: String
    private val ext: String
    private val sender: CommandSender

    init {
        reader = BufferedReader(FileReader(file, Main.charset))
        name = file.nameWithoutExtension
        ext = file.extension
        this.sender = sender ?: Bukkit.getConsoleSender()
    }

    abstract fun setVariable(name: String, obj: Any)

    /**
     * Reads the file and compile it into a script.
     */
    abstract fun parse()

    /**
     * Executes the parsed script.
     */
    abstract fun execute()
}