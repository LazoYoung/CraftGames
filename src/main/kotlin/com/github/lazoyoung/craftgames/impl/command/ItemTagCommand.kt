package com.github.lazoyoung.craftgames.impl.command

import com.github.lazoyoung.craftgames.api.ActionbarTask
import com.github.lazoyoung.craftgames.impl.command.page.HOVER_TEXT
import com.github.lazoyoung.craftgames.impl.command.page.Page
import com.github.lazoyoung.craftgames.impl.command.page.PageBody
import com.github.lazoyoung.craftgames.impl.command.page.RUN_CMD
import com.github.lazoyoung.craftgames.impl.game.player.GameEditor
import com.github.lazoyoung.craftgames.impl.game.player.PlayerData
import net.md_5.bungee.api.ChatColor
import net.md_5.bungee.api.chat.ClickEvent
import net.md_5.bungee.api.chat.ComponentBuilder
import net.md_5.bungee.api.chat.HoverEvent
import org.bukkit.Material
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.util.*

class ItemTagCommand : CommandBase("ItemTag") {

    private val helpPage = Page(
            "ItemTag Command Manual", "/itag help",
            PageBody {
                val pdata = PlayerData.get(it as Player)
                val list = LinkedList(listOf(
                        PageBody.Element(
                                "\u25cb /itag create (name)",
                                "Create a new item tag which represents \n" +
                                        "the data of your item in main-hand.",
                                "/itag create "
                        ),
                        PageBody.Element(
                                "\u25cb /itag remove (name)",
                                "Delete an item tag.",
                                "/itag remove "
                        )
                ))

                if (pdata !is GameEditor) {
                    list.addFirst(PageBody.Element(
                            "&eNote: You must be in editor mode.",
                            "&6Click here to start editing.",
                            "/game edit "
                    ))
                }

                list
            },
            PageBody {
                val list = LinkedList(listOf(
                        PageBody.Element(
                                "\u25cb /itag list",
                                "Show every item tags.",
                                "/itag list"
                        ),
                        PageBody.Element(
                                "\u25cb /itag get (name)",
                                "Get item into your inventory.",
                                "/itag get "
                        )
                ))

                if (PlayerData.get(it as Player) == null) {
                    list.addFirst(
                            PageBody.Element(
                                    "&eNote: You must be in a game.",
                                    "&6Click here to play game.",
                                    "/join "
                            )
                    )
                }

                list
            }
    )

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        when {
            sender !is Player -> {
                sender.sendMessage("$error This cannot be run by console.")
                return true
            }
            Page.isPrompted(args) -> {
                return helpPage.display(sender, args)
            }
        }

        val pdata = PlayerData.get(sender as Player)
        val game = if (pdata?.isOnline() == true) {
            pdata.getGame()
        } else {
            val text = PageBody(
                    PageBody.Element(
                            "$error You must be in a game.",
                            "&6Click here to play game.",
                            "/join "
                    )
            ).getBodyText(sender)
            sender.sendMessage(*text)
            return true
        }

        when (args[0].toLowerCase()) {
            "list" -> {
                val tags = game.resource.tagRegistry.getItemTags()

                sender.sendMessage("$info Searching for item tags...")

                if (tags.isEmpty()) {
                    sender.sendMessage("$warn No result found.")
                } else {
                    val hoverText = ComponentBuilder()
                            .color(ChatColor.GOLD)
                            .append("Click to get this item.")
                            .create()

                    for (name in tags.map { it.name }) {
                        sender.sendMessage(
                                *ComponentBuilder()
                                        .append("● $name")
                                        .event(HoverEvent(HOVER_TEXT, hoverText))
                                        .event(ClickEvent(RUN_CMD, "/itag get $name"))
                                        .create()
                        )
                    }
                }
                return true
            }
            "get" -> {
                val name = if (args.size == 2) {
                    args[1]
                } else {
                    return false
                }
                val tag = game.resource.tagRegistry.getItemTag(name)
                val inv = sender.inventory

                if (tag != null) {
                    if (inv.itemInMainHand.type == Material.AIR) {
                        inv.setItemInMainHand(tag.itemStack)
                        ActionbarTask(sender, "&f$name &6is added to your inventory.").start()
                    } else {
                        sender.sendMessage("$error Your main-hand slot isn't empty!")
                    }
                } else {
                    sender.sendMessage("$error $name does not exist!")
                }
                return true
            }
        }

        if (pdata !is GameEditor) {
            val text = PageBody(
                    PageBody.Element(
                            "$error You must be in editor mode.",
                            "&6Click here to start editing.",
                            "/game edit "
                    )
            ).getBodyText(sender)
            sender.sendMessage(*text)
            return true
        }

        when (args[0].toLowerCase()) {
            "create" -> {
                val itemStack = sender.inventory.itemInMainHand
                val name = if (args.size == 2) {
                    args[1]
                } else {
                    return false
                }

                if (itemStack.type == Material.AIR) {
                    sender.sendMessage("$error You must be holding an item in main-hand!")
                    return true
                }

                try {
                    game.resource.tagRegistry.createItemTag(name, itemStack)
                    ActionbarTask(sender, "&6Item tag &f$name &6has been created.").start()
                } catch (e: IllegalArgumentException) {
                    sender.sendMessage("$error ${e.message}")
                }
                return true
            }
            "remove" -> {
                val name = if (args.size == 2) {
                    args[1]
                } else {
                    return false
                }
                val tag = game.resource.tagRegistry.getItemTag(name)

                if (tag != null) {
                    tag.remove()
                    ActionbarTask(sender, "&eItem tag &f$name &ehas been removed.").start()
                } else {
                    sender.sendMessage("$error Item tag not found: $name")
                }
                return true
            }
            else -> return false
        }
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<String>): List<String> {
        return when {
            args.size == 1 -> {
                getCompletions(args[0], "help", "create", "remove", "list", "get")
            }
            args[0] == "help" && args.size == 2 -> {
                getCompletions(args[1], helpPage.range.map { it.toString() })
            }
            (args[0] == "remove" || args[0] == "get") && args.size == 2 -> {
                val pdata = PlayerData.get(sender as Player) ?: return emptyList()
                val registry = if (pdata.isOnline()) {
                    pdata.getGame().resource.tagRegistry
                } else {
                    return emptyList()
                }

                getCompletions(args[1], registry.getItemTags().map { it.name })
            }
            else -> {
                emptyList()
            }
        }
    }
}