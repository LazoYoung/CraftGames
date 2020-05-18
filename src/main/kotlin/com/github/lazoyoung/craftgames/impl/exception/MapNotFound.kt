package com.github.lazoyoung.craftgames.impl.exception

class MapNotFound : Exception {
    constructor() : super("World is not loaded yet.")
    constructor(message: String) : super(message)
}