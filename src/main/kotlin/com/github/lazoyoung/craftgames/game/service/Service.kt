package com.github.lazoyoung.craftgames.game.service

internal interface Service {

    fun start()

    @Deprecated("Redundancy")
    fun restart()

    fun terminate()

}