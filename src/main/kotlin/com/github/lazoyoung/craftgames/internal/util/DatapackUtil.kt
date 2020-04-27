package com.github.lazoyoung.craftgames.internal.util

import com.github.lazoyoung.craftgames.Main
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.stream.JsonWriter
import org.bukkit.Bukkit
import org.bukkit.World
import java.io.File
import java.io.IOException
import java.nio.file.Files

class DatapackUtil {

    companion object {
        /**
         * Create a datapack inside Main world.
         *
         * @param name Name of the pack to create.
         * @param replace Decide to replace a duplicate pack if found.
         * @return [File] representing the root directory of new pack.
         * @throws IllegalStateException is thrown
         * if [name] overlaps with an existing datapack and [replace] is false.
         * @throws IllegalArgumentException is thrown if [name] is not a word (alphanumeric & underscore).
         * @throws IOException is thrown if plugin fails to read/write file.
         */
        fun createPack(name: String, replace: Boolean): File {
            require(!"\\W".toRegex().containsMatchIn(name)) {
                "$name contains special character. Alphanumeric & underscore is only accepted."
            }

            val root = Bukkit.getWorlds().first().worldFolder.resolve("datapacks")

            if (!root.isDirectory && !root.mkdir()) {
                error("Failed to create datapacks directory.")
            }

            try {
                Files.newDirectoryStream(root.toPath()).use {
                    for (path in it) {
                        val file = path.toFile()

                        if (file.parentFile != root)
                            continue

                        if (file.isDirectory && file.name == name) {
                            require(replace) {
                                "A duplicate datapack is found: $name"
                            }

                            FileUtil.deleteFileTree(path)
                        }
                    }
                }
            } catch (e: Exception) {
                throw IOException("Failed to read contents in datapacks directory.", e)
            }

            try {
                val packRoot = root.resolve(name)
                val packMetaFile = packRoot.resolve("pack.mcmeta")
                val parser = JsonParser()
                val format = getFormatVersion()
                val json = "{\"pack\": {\"description\": " +
                        "\"Data pack for resources provided by CraftGames plugin.\", \"pack_format\": $format}}"

                packRoot.mkdir()
                packMetaFile.createNewFile()
                packMetaFile.writer(Main.charset).use {
                    Gson().toJson(parser.parse(json), JsonWriter(it))
                }
                return packRoot
            } catch (e: Exception) {
                throw IOException("Failed to create new datapack: $name", e)
            }
        }

        /**
         * Get directory including contents of the pack.
         *
         * @param world World to get pack from.
         * @param name Name of the pack.
         * @return [File] representing the root directory of pack.
         * This returns null if pack is not found or is invalid.
         */
        fun getPackDirectory(world: World, name: String): File? {
            val container = world.worldFolder.resolve("datapacks")
            val root = container.resolve(name)
            val validation = try {
                root.isDirectory && root.resolve("pack.mcmeta").isFile
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }

            return if (validation) {
                root
            } else {
                null
            }
        }

        fun getInternalPackName(): String {
            return Main.getConfig()?.getString("datapack.internal-pack-name") ?: "craftgames"
        }

        private fun getFormatVersion(): Int {
            val format = Main.getConfig()?.getString("datapack.format-version")

            return if (format == null || format == "auto") {
                val version = Bukkit.getServer().javaClass.`package`.name.split(".")[3]
                        .replace("_R1", "")
                        .replace("_R2", "")
                        .replace("_R3", "")
                        .replace("_R4", "")
                        .replace("_R5", "")

                when (version) {
                    "v1_6", "v1_7", "v1_8" -> 1
                    "v1_9", "v1_10" -> 2
                    "v1_11", "v1_12" -> 3
                    "v1_13", "v1_14" -> 4
                    "v1_15", "v1_16" -> 5
                    else -> error("Unable to identify server version! ($format)")
                }
            } else {
                val formatNum = format.toIntOrNull()

                require(formatNum != null) {
                    "Datapack format version should be a number!"
                }
                formatNum
            }
        }
    }
}