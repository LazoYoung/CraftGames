package com.github.lazoyoung.craftgames.api.module

import java.util.function.Consumer

interface EventModule {

    /**
     * Monitor command execution inside this game.
     * TODO: Replace this with /game script (fileName) execute [key1:val1, key2:val2, ...]
     *
     * @param callback The event will trigger this function.
     * [Consumer] accepts an array of [String] (equivalent to command arguments),
     * which can be empty if nothing is passed to this command.
     */
    fun onCommandExecute(callback: Consumer<Array<String>>)

}