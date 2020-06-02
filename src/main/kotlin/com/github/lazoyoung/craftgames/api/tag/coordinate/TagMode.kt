package com.github.lazoyoung.craftgames.api.tag.coordinate

enum class TagMode(val label: String) {
    BLOCK("block"), SPAWN("spawn"), AREA("area");

    companion object {
        /**
         * @param label to find [TagMode] in case-insensitive way.
         * @throws NoSuchElementException is raised if [label] is unknown.
         */
        fun getByLabel(label: String): TagMode {
            return values().first { it.label.equals(label, true) }
        }
    }
}