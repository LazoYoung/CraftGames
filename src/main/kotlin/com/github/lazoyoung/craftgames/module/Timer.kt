package com.github.lazoyoung.craftgames.module

import java.text.DecimalFormat

class Timer(
        private val unit: Unit,
        private val value: Long
) {
    enum class Unit {
        TICK, SECOND, MINUTE
    }

    fun toTick(): Long {
        return when (unit) {
            Unit.MINUTE -> value * 1200
            Unit.SECOND -> value * 20
            Unit.TICK -> value
        }
    }

    fun toSecond(): Long {
        return when (unit) {
            Unit.TICK -> value / 20
            Unit.SECOND -> value
            Unit.MINUTE -> value * 60
        }
    }

    fun toMinute(): Long {
        return when (unit) {
            Unit.TICK -> value / (20 * 60)
            Unit.SECOND -> value / 60
            Unit.MINUTE -> value
        }
    }

    /**
     * Returns a fancy formatted [String] based on this [Timer].
     *
     * If [verbose] is true, expect something like: 1 hour 35 minutes
     *
     * If [verbose] is otherwise false, expect: 01:35:00
     */
    fun format(verbose: Boolean): String {
        val hourUnit = 60*60
        val minUnit = 60
        var min = 0
        var sec = toTick().toInt() / 20
        val text = StringBuilder()

        if (sec > hourUnit) {
            val hour = sec / hourUnit
            sec -= hour * hourUnit

            if (!verbose) {
                text.append(DecimalFormat("00").format(hour)).append(':')
            } else if (hour > 1) {
                text.append("$hour hours ")
            } else if (hour == 1) {
                text.append("$hour hour ")
            }
        }

        if (sec > minUnit) {
            min = sec / minUnit
            sec -= min * minUnit

            if (verbose) {
                if (min > 1) {
                    text.append("$min minutes ")
                } else if (min == 1) {
                    text.append("$min minute ")
                }
            }
        }

        if (!verbose) {
            val dec = DecimalFormat("00")
            text.append(dec.format(min)).append(":").append(dec.format(sec))
        } else if (sec > 1) {
            text.append("$sec seconds")
        } else if (sec == 1) {
            text.append("$sec second")
        } else {
            text.removeSuffix(" ")
        }

        return text.toString()
    }
}