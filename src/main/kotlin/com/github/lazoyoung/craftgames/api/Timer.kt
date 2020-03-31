package com.github.lazoyoung.craftgames.api

import java.text.DecimalFormat

class Timer(
        private val timeUnit: TimeUnit,
        private val value: Long
) {
    init {
        if (value <= 0)
            throw IllegalArgumentException("Value must be greater than 0.")
    }

    fun toTick(): Long {
        return when (timeUnit) {
            TimeUnit.HOUR -> value * 20 * 60 * 60
            TimeUnit.MINUTE -> value * 20 * 60
            TimeUnit.SECOND -> value * 20
            TimeUnit.TICK -> value
        }
    }

    fun toSecond(): Long {
        return when (timeUnit) {
            TimeUnit.HOUR -> value * 60 * 60
            TimeUnit.MINUTE -> value * 60
            TimeUnit.SECOND -> value
            TimeUnit.TICK -> Math.floorDiv(value, 20L)
        }
    }

    fun toMinute(): Long {
        return when (timeUnit) {
            TimeUnit.HOUR -> value * 60
            TimeUnit.MINUTE -> value
            TimeUnit.SECOND -> Math.floorDiv(value, 60L)
            TimeUnit.TICK -> Math.floorDiv(value, 60 * 20L)
        }
    }

    fun toHour(): Long {
        return when (timeUnit) {
            TimeUnit.HOUR -> value
            TimeUnit.MINUTE -> Math.floorDiv(value, 60L)
            TimeUnit.SECOND -> Math.floorDiv(value, 60 * 60L)
            TimeUnit.TICK -> Math.floorDiv(value, 60 * 60 * 20L)
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
            val hour = toHour().toInt()
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
            min = toMinute().toInt()
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