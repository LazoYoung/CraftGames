package com.github.lazoyoung.craftgames.internal.exception

class MapNotFound : Exception {
    constructor() : super("World is not loaded yet.")
    constructor(message: String) : super(message)
}