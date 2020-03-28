package com.github.lazoyoung.craftgames.module.api

import org.bukkit.entity.Player
import java.util.function.Consumer

interface WorldModule {

    fun setAreaTrigger(tag: String, task: Consumer<Player>)

}