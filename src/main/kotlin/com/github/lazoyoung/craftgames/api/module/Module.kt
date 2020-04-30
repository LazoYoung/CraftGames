package com.github.lazoyoung.craftgames.api.module

interface Module {

    fun getGameModule(): GameModule

    fun getItemModule(): ItemModule

    fun getLobbyModule(): LobbyModule

    fun getMobModule(): MobModule

    fun getPlayerModule(): PlayerModule

    fun getScriptModule(): ScriptModule

    fun getTeamModule(): TeamModule

    fun getWorldModule(): WorldModule

}