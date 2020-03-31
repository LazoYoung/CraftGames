package com.github.lazoyoung.craftgames.internal.util

import com.github.lazoyoung.craftgames.Main
import org.bukkit.entity.Player
import org.bukkit.plugin.messaging.PluginMessageListener
import java.io.*
import java.util.*
import java.util.function.Consumer
import kotlin.collections.HashMap

class MessengerUtil : PluginMessageListener {

    companion object {
        internal var inbox = HashMap<UUID, Consumer<String?>>()

        internal fun request(player: Player, message: Array<String>, callback: Consumer<String?>? = null) {
            try {
                val byteStream = ByteArrayOutputStream()
                val dataStream = DataOutputStream(byteStream)
                message.forEach { dataStream.writeUTF(it) }
                player.sendPluginMessage(Main.instance, "BungeeCord", byteStream.toByteArray())
                callback?.let { inbox[player.uniqueId] = it }
            } catch (e: Exception) {
                e.printStackTrace()
                callback?.accept(null)
            }
        }
    }

    override fun onPluginMessageReceived(channel: String, player: Player, message: ByteArray) {
        if (channel != "BungeeCord")
            return

        try {
            val uid = player.uniqueId
            val stream = DataInputStream(ByteArrayInputStream(message))
            val subChannel = stream.readUTF()

            if (subChannel == "GetServers") {
                inbox[uid]?.accept(stream.readUTF())
            }
        } catch (e: IOException) {
            e.printStackTrace()
            Main.logger.severe("Error occurred during communication with BungeeCord.")
        }
    }

}