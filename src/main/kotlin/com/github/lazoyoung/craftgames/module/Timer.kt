package com.github.lazoyoung.craftgames.module

class Timer(
        private val unit: Unit,
        private val value: Int
) {
    enum class Unit {
        TICK, SECOND, MINUTE
    }

    internal fun toTick(): Long {
        return when (unit) {
            Unit.MINUTE -> value * 1200
            Unit.SECOND -> value * 20
            Unit.TICK -> value
        }.toLong()
    }
}