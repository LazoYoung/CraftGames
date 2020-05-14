package com.github.lazoyoung.craftgames.api.module

import com.github.lazoyoung.craftgames.api.CommandHandler
import com.github.lazoyoung.craftgames.api.Timer
import io.github.jorelali.commandapi.api.CommandPermission
import io.github.jorelali.commandapi.api.arguments.Argument
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.configuration.serialization.ConfigurationSerializable
import org.bukkit.scheduler.BukkitTask
import org.bukkit.util.io.BukkitObjectInputStream
import org.bukkit.util.io.BukkitObjectOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.util.function.Consumer

/**
 * Command module allows you to register custom commands.
 * Those commands are only available to this game. They won't be accessible from outside.
 * Access to this module is limited to Command-typed scripts.
 *
 * @throws IllegalAccessException is raised if this module is accessed from a script other than Command-typed script.
 */
interface CommandModule {

    /**
     * Set whether or not to print out debugging message inside script log.
     */
    fun setLogVerbosity(verbose: Boolean)

    /**
     * Register a command which is only available in this game.
     *
     * @param name Name of command without prefix-slash(/).
     * @param permissions Permission applied to whole command.
     * @param aliases Array of possible aliases. (optional, can be null)
     * @param args Command arguments. (optional, can be null)
     * @param handler Definition of command operation.
     */
    fun register(name: String, permissions: CommandPermission,
                 aliases: Array<String>?, args: LinkedHashMap<String, Argument>?, handler: CommandHandler)

    /**
     * Repeat to execute [task][Runnable].
     *
     * @param counter The number of times to repeat.
     * @param interval The [frequency][Timer] of repeating task.
     * @param task The [task][Runnable] to be executed.
     * @throws IllegalArgumentException is thrown if [interval] is less than 1 tick.
     */
    fun repeat(counter: Int, interval: Timer, task: Runnable): BukkitTask

    /**
     * Wait before executing the [task][Runnable].
     *
     * @param delay The amount of [time][Timer] to wait.
     * @param task The [task][Runnable] to be executed.
     * @throws IllegalArgumentException is thrown if [delay] is less than 1 tick.
     */
    fun wait(delay: Timer, task: Runnable): BukkitTask

    /**
     * Returns the [File] which is silently created if it didn't exist there.
     *
     * The file is resolved by given [path] - a __relative path__ that is joined
     * to the root folder of the game layout where this module is derived from.
     * To illustrate this, let's assume that our game 'skywars'
     * is rooted from 'plugins/CraftGames/skywars/'.
     * If your [path] were 'data/test.txt', then actual outcome would be
     * 'plugins/CraftGames/skywars/data/test.txt'
     *
     * @param path The [File] is located by this path.
     * @throws IllegalArgumentException is thrown if [path] is absolute.
     */
    fun getFile(path: String): File

    /**
     * Read data from your custom [File][getFile] through [BukkitObjectInputStream].
     *
     * You can deserialize file contents into various types of primitive data
     * as well as [ConfigurationSerializable] objects.
     * i.e. Boolean, String, ItemStack, PotionEffect, etc.
     *
     * @param file The file to read data from. Use [getFile] to get one.
     * @param reader This lends you the [InputStream][BukkitObjectInputStream] to read stuff.
     *   You don't need to flush/close the stream afterwards as plugin does it for you.
     * @throws FileNotFoundException is thrown if [file] does not exist.
     */
    fun readObjectStream(file: File, reader: Consumer<BukkitObjectInputStream>)

    /**
     * Write data to your custom [File][getFile] through [BukkitObjectOutputStream].
     *
     * You can serialize primitive data and [ConfigurationSerializable] objects
     * to save those into the file.
     *
     * @param file The file to write data to. Use [getFile] to get one.
     * @param writer This lends you the [OutputStream][BukkitObjectOutputStream] to write stuff.
     *   You don't need to flush/close the stream afterwards as plugin does it for you.
     * @throws FileNotFoundException is thrown if [file] does not exist.
     */
    fun writeObjectStream(file: File, writer: Consumer<BukkitObjectOutputStream>)

    /**
     * Read or write data of your custom YAML configuration.
     *
     * @param file The file you want to treat as YAML. Use [getFile] to get one.
     * @param consumer This lends you to the [YamlConfiguration] for you to take control of the file.
     *   You don't have to worry about saving the file because the plugin does it for you.
     */
    fun getYamlConfiguration(file: File, consumer: Consumer<YamlConfiguration>)

}