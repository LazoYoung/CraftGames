package com.github.lazoyoung.craftgames.api

import java.text.DecimalFormat

class Timer(
        private val timeUnit: TimeUnit,
        private var time: Long
) {
    init {
        if (time < 0)
            throw IllegalArgumentException("Negative time is not acceptable.")
    }

    fun toTick(): Long {
        return toTick(timeUnit, time)
    }

    fun toSecond(): Long {
        return toSecond(timeUnit, time)
    }

    fun toMinute(): Long {
        return toMinute(timeUnit, time)
    }

    fun toHour(): Long {
        return toHour(timeUnit, time)
    }

    fun subtract(unit: TimeUnit, amount: Long) {
        val subtract = when (timeUnit) {
            TimeUnit.HOUR -> toHour(unit, amount)
            TimeUnit.MINUTE -> toMinute(unit, amount)
            TimeUnit.SECOND -> toSecond(unit, amount)
            TimeUnit.TICK -> toTick(unit, amount)
        }

        this.time -= subtract
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

    private fun toTick(timeUnit: TimeUnit, time: Long): Long {
        return when (timeUnit) {
            TimeUnit.HOUR -> time * 20 * 60 * 60
            TimeUnit.MINUTE -> time * 20 * 60
            TimeUnit.SECOND -> time * 20
            TimeUnit.TICK -> time
        }
    }

    private fun toSecond(timeUnit: TimeUnit, time: Long): Long {
        return when (timeUnit) {
            TimeUnit.HOUR -> time * 60 * 60
            TimeUnit.MINUTE -> time * 60
            TimeUnit.SECOND -> time
            TimeUnit.TICK -> Math.floorDiv(time, 20L)
        }
    }

    private fun toMinute(timeUnit: TimeUnit, time: Long): Long {
        return when (timeUnit) {
            TimeUnit.HOUR -> time * 60
            TimeUnit.MINUTE -> time
            TimeUnit.SECOND -> Math.floorDiv(time, 60L)
            TimeUnit.TICK -> Math.floorDiv(time, 60 * 20L)
        }
    }

    private fun toHour(timeUnit: TimeUnit, time: Long): Long {
        return when (timeUnit) {
            TimeUnit.HOUR -> time
            TimeUnit.MINUTE -> Math.floorDiv(time, 60L)
            TimeUnit.SECOND -> Math.floorDiv(time, 60 * 60L)
            TimeUnit.TICK -> Math.floorDiv(time, 60 * 60 * 20L)
        }
    }
}