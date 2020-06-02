package com.github.lazoyoung.craftgames.impl.tag.coordinate

import com.github.lazoyoung.craftgames.api.tag.coordinate.TagMode

enum class CoordTagFilter(val flag: String) {
    TAG("-tag"), MODE("-mode"), MAP("-map");

    companion object {
        /**
         * @param flag to find [TagMode] in case-sensitive way.
         * @throws NoSuchElementException is raised if [flag] is unknown.
         */
        fun getByFlag(flag: String): CoordTagFilter {
            return values().first { it.flag == flag }
        }
    }
}