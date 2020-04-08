package com.github.lazoyoung.craftgames.api.module

import com.github.lazoyoung.craftgames.api.EventType
import com.github.lazoyoung.craftgames.api.Timer
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.configuration.serialization.ConfigurationSerializable
import org.bukkit.event.Event
import org.bukkit.scheduler.BukkitTask
import org.bukkit.util.io.BukkitObjectInputStream
import org.bukkit.util.io.BukkitObjectOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.util.function.Consumer

interface ScriptModule {

    fun attachEventMonitor(eventType: EventType, callback: Consumer<in Event>)

    fun attachEventMonitor(eventType: String, callback: Consumer<in Event>)

    fun detachEventMonitor(eventType: EventType)

    fun detachEventMonitor(eventType: String)

    fun setLogVerbosity(verbose: Boolean)

    fun repeat(counter: Int, interval: Timer, task: Runnable): BukkitTask

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