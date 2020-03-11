package com.github.lazoyoung.craftgames

import java.io.IOException
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.util.*
import java.util.logging.Logger

class FileUtil(val logger: Logger?) {
    /**
     * Clones the whole content inside the source directory.
     * @param source The root of the content to be cloned. (This folder itself is not cloned)
     * @param target Path to target directory.
     * @throws IllegalArgumentException Thrown if either source or target does not indicate a directory
     * @throws SecurityException Thrown if system denied access to any file.
     * @throws IOException Thrown if copy-paste I/O process has failed.
     */
    fun cloneFileTree(source: Path, target: Path) {
        if (!Files.isDirectory(source))
            throw IllegalArgumentException("source is not a directory!")
        if (!Files.isDirectory(target))
            throw IllegalArgumentException("target is not a directory!")


        Files.walkFileTree(source, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Int.MAX_VALUE, object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path?, attr: BasicFileAttributes?): FileVisitResult {
                if (dir == null)
                    return FileVisitResult.CONTINUE

                val targetDir = target.resolve(source.relativize(dir).toString())
                try {
                    Files.copy(dir, targetDir)
                } catch (e: FileAlreadyExistsException) {
                    if (!Files.isDirectory(targetDir)) {
                        e.printStackTrace()
                        logger?.severe("$targetDir is not a valid directory.")
                    }
                }
                return FileVisitResult.CONTINUE
            }

            override fun visitFile(file: Path?, attrs: BasicFileAttributes?): FileVisitResult {
                if (file != null) {
                    val targetPath = target.resolve(source.relativize(file).toString())
                    logger?.info("Copying ${file.fileName} to ${targetPath.normalize()}")
                    Files.copy(file, targetPath)
                }
                return FileVisitResult.CONTINUE
            }

            override fun visitFileFailed(file: Path?, exc: IOException?): FileVisitResult {
                logger?.warning("Failed to copy: ${file?.toRealPath().toString()}")
                return FileVisitResult.TERMINATE
            }
        })
    }
}