package com.github.lazoyoung.craftgames.impl.util

import com.github.lazoyoung.craftgames.impl.Main
import java.io.IOException
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.util.*
import java.util.concurrent.CompletableFuture

class FileUtil {
    companion object {
        /**
         * Clones the whole content inside the source directory.
         * @param source The root of the content to be cloned.
         * @param target Path to target directory.
         * @param options You may define the [copying behavior][CopyOption] if desired.
         * @return a [CompletableFuture] that is completed with result (Boolean).
         * @throws IllegalArgumentException Thrown if source does not indicate a directory
         * @throws SecurityException Thrown if system denied access to any file.
         * @throws IOException Thrown if copy-paste I/O process has failed.
         */
        fun cloneFileTree(source: Path, target: Path, vararg options: CopyOption?): CompletableFuture<Boolean> {
            val sourcePath = source.normalize()
            val future = CompletableFuture<Boolean>()

            if (!Files.isDirectory(sourcePath))
                throw IllegalArgumentException("source is not a directory!")
            if (!Files.isDirectory(target)) {
                Files.createDirectory(target)
            }

            Files.walkFileTree(sourcePath, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Int.MAX_VALUE,
                    object : SimpleFileVisitor<Path>() {

                override fun preVisitDirectory(dir: Path?, attr: BasicFileAttributes?): FileVisitResult {
                    if (dir == null)
                        return FileVisitResult.CONTINUE

                    val targetDir = when (sourcePath.parent) {
                        null -> target.resolve(dir.toString())
                        else -> target.resolve(sourcePath.parent.relativize(dir).toString())
                    }
                    Main.logger.info("Copying directory: ${dir.fileName} -> $targetDir")
                    try {
                        if (targetDir.toFile().listFiles().isNullOrEmpty())
                            Files.copy(dir, targetDir, *options)
                    } catch (exc: FileAlreadyExistsException) {
                        if (!Files.isDirectory(targetDir)) {
                            Main.logger.warning("Not a valid target: $targetDir")
                        }
                        future.completeExceptionally(exc)
                    }
                    return FileVisitResult.CONTINUE
                }

                override fun visitFile(file: Path?, attrs: BasicFileAttributes?): FileVisitResult {
                    if (file != null) {
                        try {
                            val targetPath = when (sourcePath.parent) {
                                null -> target.resolve(file.toString())
                                else -> target.resolve(sourcePath.parent.relativize(file).toString())
                            }
                            Main.logger.info("Copying file: ${file.fileName} -> ${targetPath.normalize()}")
                            Files.copy(file, targetPath, *options)
                        } catch (exc: Exception) {
                            future.completeExceptionally(exc)
                            Main.logger.warning("Failed to copy file: ${file.fileName}")
                        }
                    }
                    return FileVisitResult.CONTINUE
                }

                override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult {
                    future.completeExceptionally(exc)
                    return FileVisitResult.TERMINATE
                }

                override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                    if (exc != null) {
                        future.completeExceptionally(exc)
                    } else if (dir == sourcePath) {
                        future.complete(true)
                    }

                    return super.postVisitDirectory(dir, exc)
                }
            })

            return future
        }

        /**
         * @param root must be a directory
         */
        fun deleteFileTree(root: Path) {
            if (!Files.isDirectory(root))
                throw IllegalArgumentException("root is not a directory!")

            Files.walkFileTree(root, object : SimpleFileVisitor<Path>(), FileVisitor<Path> {
                override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                    dir.toFile().setWritable(true, true)
                    Files.delete(dir)
                    Main.logger.info("Delete folder: $dir")
                    return FileVisitResult.CONTINUE
                }

                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    file.toFile().setWritable(true, true)
                    Files.delete(file)
                    Main.logger.info("Delete file: $file")
                    return FileVisitResult.CONTINUE
                }

                override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult {
                    exc.printStackTrace()
                    Main.logger.warning("Failed to delete: $file")
                    return FileVisitResult.CONTINUE
                }
            })
        }
    }
}