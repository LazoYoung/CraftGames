package com.github.lazoyoung.craftgames.api

import com.github.lazoyoung.craftgames.Main
import com.github.lazoyoung.craftgames.internal.exception.FaultyConfiguration

enum class ScriptCompiler {
    STATIC, DYNAMIC;

    companion object {
        /**
         * Get a [ScriptCompiler] by its [name].
         *
         * @param name Name of [ScriptCompiler] to get. Default value is returned if null is passed.
         * @throws IllegalArgumentException is raised if [name] doesn't indicate any.
         * @throws FaultyConfiguration is raised if default value is not configured properly.
         */
        fun get(name: String?): ScriptCompiler {
            return if (name != null) {
                valueOf(name.toUpperCase())
            } else {
                getDefault()
            }
        }

        /**
         * Get default [ScriptCompiler].
         *
         * @throws FaultyConfiguration is raised if default value is not configured properly.
         */
        fun getDefault(): ScriptCompiler {
            try {
                val def = Main.getConfig()?.getString("script.compiler", "STATIC")

                return valueOf(checkNotNull(def).toUpperCase())
            } catch (e: Throwable) {
                throw FaultyConfiguration("Illegal script compiler is defined at config.yml", e)
            }
        }
    }
}