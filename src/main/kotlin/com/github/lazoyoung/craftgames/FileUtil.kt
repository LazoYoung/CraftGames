package com.github.lazoyoung.craftgames

import java.io.IOException
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.util.*
import java.util.logging.Logger

class FileUtil(val logger: Logger?) {
    /**
     * Clones the whole content inside the source directory.
     * @param source The root of the content to be cloned.
     * @param target Path to target directory.
     * @param options You may define the copying behavior if desired.
     * @throws IllegalArgumentException Thrown if source does not indicate a directory
     * @throws SecurityException Thrown if system denied access to any file.
     * @throws IOException Thrown if copy-paste I/O process has failed.
     */
    fun cloneFileTree(source: Path, target: Path, vararg options: CopyOption?) {
        if (!Files.isDirectory(source))
            throw IllegalArgumentException("source is not a directory!")
        if (!Files.isDirectory(target)) {
            Files.createDirectory(target)
        }


        Files.walkFileTree(source, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Int.MAX_VALUE, object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path?, attr: BasicFileAttributes?): FileVisitResult {
                if (dir == null)
                    return FileVisitResult.CONTINUE

                val targetDir = when (source.parent) {
                    null -> target.resolve(dir.toString())
                    else -> target.resolve(source.parent.relativize(dir).toString())
                }
                logger?.info("Copying directory: ${dir.fileName} -> $targetDir")
                try {
                    if(targetDir.toFile().listFiles().isNullOrEmpty())
                        Files.copy(dir, targetDir, *options)
                } catch (e: FileAlreadyExistsException) {
                    if (!Files.isDirectory(targetDir)) {
                        logger?.severe("Not a valid target: $targetDir")
                    }
                    e.printStackTrace()
                }
                return FileVisitResult.CONTINUE
            }

            override fun visitFile(file: Path?, attrs: BasicFileAttributes?): FileVisitResult {
                if (file != null) {
                    val targetPath = when (source.parent) {
                        null -> target.resolve(file.toString())
                        else -> target.resolve(source.parent.relativize(file).toString())
                    }
                    logger?.info("Copying file: ${file.fileName} -> ${targetPath.normalize()}")
                    Files.copy(file, targetPath, *options)
                }
                return FileVisitResult.CONTINUE
            }

            override fun visitFileFailed(file: Path?, exc: IOException?): FileVisitResult {
                logger?.warning("Failed to copy: ${file?.toRealPath().toString()}")
                return FileVisitResult.TERMINATE
            }
        })
    }

    /**
     * @param root must be a directory
     */
    fun deleteFileTree(root: Path) {
        if (!Files.isDirectory(root))
            throw IllegalArgumentException("root is not a directory!")

        Files.walkFileTree(root, object : SimpleFileVisitor<Path>(), FileVisitor<Path> {
            override fun postVisitDirectory(dir: Path?, exc: IOException?): FileVisitResult {
                Files.delete(dir!!)
                logger?.info("Delete folder: $dir")
                return FileVisitResult.CONTINUE
            }

            override fun visitFile(file: Path?, attrs: BasicFileAttributes?): FileVisitResult {
                Files.delete(file!!)
                logger?.info("Delete file: $file")
                return FileVisitResult.CONTINUE
            }

            override fun visitFileFailed(file: Path?, exc: IOException?): FileVisitResult {
                logger?.warning("Failed to delete: $file")
                return FileVisitResult.CONTINUE
            }
        })
    }
}