package com.github.lazoyoung.craftgames.api.module

import com.github.lazoyoung.craftgames.api.EventType
import com.github.lazoyoung.craftgames.api.Timer
import com.github.lazoyoung.craftgames.event.GameEvent
import com.github.lazoyoung.craftgames.game.script.GameScript
import com.github.lazoyoung.craftgames.internal.exception.ScriptEngineNotFound
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.configuration.serialization.ConfigurationSerializable
import org.bukkit.entity.LivingEntity
import org.bukkit.scheduler.BukkitTask
import org.bukkit.util.io.BukkitObjectInputStream
import org.bukkit.util.io.BukkitObjectOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.util.function.Consumer

interface ScriptModule {

    /**
     * Attach an event monitor.
     *
     * [Callback][Consumer] is executed whenever a certain event occurs.
     * Callback accepts 1 parameter which is the instance of event.
     *
     * @param eventType [EventType] designating the event that [callback] is executed upon.
     */
    fun attachEventMonitor(eventType: EventType, callback: Consumer<in GameEvent>)

    /**
     * Attach an event monitor.
     *
     * [Callback][Consumer] is executed whenever a certain event occurs.
     * Callback accepts 1 parameter which is the instance of event.
     *
     * @param eventType [EventType] (represented by [String]) which designates the event
     * that [callback] is executed upon.
     */
    fun attachEventMonitor(eventType: String, callback: Consumer<in GameEvent>)

    /**
     * Detach an event monitor.
     *
     * This incapacitates the previous [callback][Consumer] attached to the monitor.
     *
     * @param eventType [EventType] which identifies event monitor.
     */
    fun detachEventMonitor(eventType: EventType)

    /**
     * Detach an event monitor.
     *
     * This incapacitates the previous [callback][Consumer] attached to the monitor.
     *
     * @param eventType [EventType] (represented by [String]) which identifies event monitor.
     */
    fun detachEventMonitor(eventType: String)

    /**
     * Set whether or not to print out debugging message inside script log.
     */
    fun setLogVerbosity(verbose: Boolean)

    /**
     * Get [GameScript] of the file with given [fileName].
     *
     * @throws IllegalArgumentException is thrown if the file indicated by [fileName] does not exist.
     * @throws ScriptEngineNotFound is thrown if either file extension or script engine is invalid.
     * @throws RuntimeException is thrown if plugin fails to load script
     */
    fun getScript(fileName: String): GameScript

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
     * Dispatch the [command][commandLine] to [target][LivingEntity] and execute it.
     *
     * @param target [LivingEntity] who executes the command. e.g. Player
     * @param commandLine Command + Arguments, without slash: /
     * @return false if dispatch is failed.
     */
    fun dispatchCommand(target: LivingEntity, commandLine: String): Boolean

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