package org.mh4u.port.gameimport

import org.apache.commons.compress.MemoryLimitException
import org.apache.commons.compress.PasswordRequiredException
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipFile
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.channels.SeekableByteChannel
import java.nio.file.Files
import java.util.UUID
import java.util.zip.CRC32

/** Sources are opened on demand. Archives are read in place, not copied to a second cache. */
interface GameImportSource {
    val name: String
    val size: Long
    fun openStream(): InputStream
    fun openChannel(): SeekableByteChannel
}

data class GameImportProgress(val message: String, val bytes: Long = 0, val total: Long = -1) {
    val fraction: Float get() = if (total > 0) (bytes.toDouble() / total).toFloat().coerceIn(0f, 1f) else -1f
}

/** Only the selected image is written, under a temporary name in games/. Saves are never accessed. */
class GameImageImporter(
    private val directory: File,
    private val available: () -> Long = { directory.usableSpace },
) {
    companion object {
        private val lock = Any()
        private val partialName = Regex("\\.mh4u-import-[a-f0-9-]{36}\\.partial")
        private val images = setOf("3ds", "cci", "cxi")
        private const val MAX_IMAGE = 8L * 1024 * 1024 * 1024
        private const val RESERVE = 64L * 1024 * 1024
        private const val MAX_ENTRIES = 4096
        private const val MEMORY_KIB = 128 * 1024

        fun removeAbandoned(directory: File) = synchronized(lock) {
            directory.listFiles().orEmpty().filter { it.isFile && partialName.matches(it.name) }
                .forEach { it.delete() }
        }
    }

    private class ImportProblem(message: String) : IOException(message)
    private fun problem(message: String): Nothing = throw ImportProblem(message)

    fun importGame(
        source: GameImportSource,
        report: (GameImportProgress) -> Unit = {},
        checkCancelled: () -> Unit = {},
    ): File = synchronized(lock) {
        checkCancelled()
        if (!directory.isDirectory && !directory.mkdirs()) problem("The app's game folder could not be opened.")
        removeAbandoned(directory)
        val name = source.name
        if (Regex("(?i).*(?:\\.(?:7z|zip)\\.\\d+|\\.z\\d+|\\.part\\d+\\.(?:zip|7z|rar))$").matches(name) ||
            name.substringAfterLast('.', "").equals("rar", true)) {
            problem("This is a split or RAR archive. Keep all matching parts together, extract part 1 with an archive app, then add the extracted .3ds, .cci or .cxi file.")
        }
        val extension = name.substringAfterLast('.', "").lowercase()
        if (extension !in images && extension !in setOf("zip", "7z")) {
            problem("Choose a decrypted .3ds, .cci or .cxi game image, or a single ZIP / 7Z archive.")
        }
        report(GameImportProgress(if (extension in images) "Copying game image…" else "Reading game archive…"))
        try {
            when (extension) {
                "zip" -> source.openChannel().use { channel ->
                    ZipFile.builder().setSeekableByteChannel(CancellableChannel(channel, checkCancelled)).get().use { archive ->
                        val candidates = mutableListOf<ZipArchiveEntry>()
                        val entries = archive.entries
                        var count = 0
                        while (entries.hasMoreElements()) {
                            checkCancelled()
                            if (++count > MAX_ENTRIES) problem("This archive contains too many files. Extract it manually and choose the game image.")
                            val entry = entries.nextElement()
                            if (!entry.isDirectory && entry.name.substringAfterLast('.', "").lowercase() in images) candidates += entry
                        }
                        val image = single(candidates)
                        if (image.isUnixSymlink || image.method !in setOf(0, 1, 6, 8, 9, 12) || !archive.canReadEntryData(image)) problem("This ZIP uses encryption or an unsupported format. Extract it manually, then add the game image.")
                        val fileName = imageName(image.name)
                        preflight(image.size)
                        archive.getInputStream(image).use { input ->
                            writeImage(input, fileName, image.size, image.crc, "Extracting game image…", report, checkCancelled)
                        }
                    }
                }
                "7z" -> source.openChannel().use { channel ->
                    SevenZFile.builder().setSeekableByteChannel(CancellableChannel(channel, checkCancelled))
                        .setDefaultName(name).setMaxMemoryLimitKiB(MEMORY_KIB).get().use { archive ->
                            val candidates = archive.entries.asSequence().take(MAX_ENTRIES + 1).toList()
                            if (candidates.size > MAX_ENTRIES) problem("This archive contains too many files. Extract it manually and choose the game image.")
                            val image = single(candidates.filter { !it.isDirectory && it.name.orEmpty().substringAfterLast('.', "").lowercase() in images })
                            val fileName = imageName(image.name)
                            preflight(image.size)
                            archive.getInputStream(image).use { input ->
                                writeImage(input, fileName, image.size, if (image.hasCrc) image.crcValue else null,
                                    "Extracting game image…", report, checkCancelled)
                            }
                        }
                }
                else -> {
                    val fileName = imageName(name)
                    preflight(source.size)
                    source.openStream().use { input -> writeImage(input, fileName, source.size, null, "Copying game image…", report, checkCancelled) }
                }
            }
        } catch (e: MemoryLimitException) {
            problem("This 7Z needs more extraction memory than the app allows. Extract it with an archive app, then add the game image.")
        } catch (e: PasswordRequiredException) {
            problem("This archive is password-protected. Extract it with an archive app, then add the game image.")
        } catch (e: IOException) {
            if (e is ImportProblem || extension in images || e.message.orEmpty().contains("Internal storage")) throw e
            problem("The archive could not be read. Check that the download is complete. Split and RAR archives need manual extraction before Add game.")
        }
    }

    private fun <T> single(entries: List<T>): T {
        if (entries.isEmpty()) problem("No .3ds, .cci or .cxi game image was found. Extract a compatible USA MH4U game image and try again.")
        if (entries.size != 1) problem("This archive contains more than one game image. Extract it manually, then choose the image you want.")
        return entries.single()
    }

    private fun imageName(path: String): String {
        val normalized = path.replace('\\', '/')
        if (normalized.startsWith('/') || ':' in normalized || normalized.split('/').any { it == ".." || it == "." } || normalized.any { it.code < 32 }) {
            problem("This archive contains an unsafe game filename. No game was imported.")
        }
        val name = normalized.substringAfterLast('/')
        if (name.isBlank() || name.length > 180) problem("The game filename is not supported. Extract and rename the image, then add it again.")
        return name
    }

    private fun preflight(size: Long) {
        if (size == 0L || size > MAX_IMAGE) problem("The game image has an invalid size. Check that the download is complete.")
        val needed = maxOf(size, 0L) + RESERVE
        if (available() < needed) {
            val mib = (needed + 1024 * 1024 - 1) / (1024 * 1024)
            problem("Not enough free space. Keep at least $mib MB free for the extracted game and temporary working space, then try again.")
        }
    }

    private fun writeImage(
        input: InputStream, name: String, expected: Long, expectedCrc: Long?, message: String,
        report: (GameImportProgress) -> Unit, checkCancelled: () -> Unit,
    ): File {
        val temporary = File(directory, ".mh4u-import-${UUID.randomUUID()}.partial")
        var promoted: File? = null
        var complete = false
        try {
            val header = ByteArray(0x104)
            val crc = CRC32()
            var done = 0L
            var lastPercent = -1
            var lastReport = 0L
            var nextSpaceCheck = 0L
            report(GameImportProgress(message, 0, expected))
            temporary.outputStream().buffered(1024 * 1024).use { output ->
                val buffer = ByteArray(1024 * 1024)
                while (true) {
                    checkCancelled()
                    val n = input.read(buffer)
                    if (n < 0) break
                    if (n == 0) continue
                    if (done + n > MAX_IMAGE || expected >= 0 && done + n > expected) problem("The archive's game size does not match its contents. Check the download and try again.")
                    if (done >= nextSpaceCheck) {
                        if (available() < RESERVE + n) problem("Storage filled up during import. Free some space, then try again.")
                        nextSpaceCheck = done + 8 * 1024 * 1024
                    }
                    if (done < header.size) buffer.copyInto(header, done.toInt(), 0, minOf(n, header.size - done.toInt()))
                    output.write(buffer, 0, n)
                    crc.update(buffer, 0, n)
                    done += n
                    val percent = if (expected > 0) (done * 100 / expected).toInt() else -1
                    val now = System.nanoTime()
                    if (percent != lastPercent || now - lastReport >= 150_000_000) {
                        report(GameImportProgress(message, done, expected)); lastPercent = percent; lastReport = now
                    }
                }
            }
            checkCancelled()
            if (done < header.size || expected >= 0 && done != expected) problem("The game image was not fully copied. Check the download and try again.")
            if (expectedCrc != null && expectedCrc >= 0 && crc.value != expectedCrc) problem("The game image failed its archive integrity check. Download or transfer the archive again.")
            val magic = String(header, 0x100, 4, Charsets.US_ASCII)
            if (magic != "NCSD" && magic != "NCCH") problem("The selected file is not a readable 3DS game image. Check that it is extracted and decrypted.")
            var destination = File(directory, name)
            if (destination.exists()) destination = File(directory, "${destination.nameWithoutExtension}-${UUID.randomUUID().toString().take(8)}.${destination.extension}")
            checkCancelled()
            // Same-directory move publishes a closed, verified file; an existing image is never replaced.
            Files.move(temporary.toPath(), destination.toPath())
            promoted = destination
            checkCancelled()
            report(GameImportProgress("Game image ready", done, done))
            complete = true
            return destination
        } finally {
            temporary.delete()
            if (!complete) promoted?.delete()
        }
    }

    private class CancellableChannel(private val channel: SeekableByteChannel, private val check: () -> Unit) : SeekableByteChannel {
        override fun read(dst: ByteBuffer): Int { check(); return channel.read(dst) }
        override fun position(): Long = channel.position()
        override fun position(newPosition: Long): SeekableByteChannel { check(); channel.position(newPosition); return this }
        override fun size(): Long = channel.size()
        override fun isOpen(): Boolean = channel.isOpen
        override fun close() = channel.close()
        override fun write(src: ByteBuffer): Int = throw UnsupportedOperationException("Read only")
        override fun truncate(size: Long): SeekableByteChannel = throw UnsupportedOperationException("Read only")
    }
}
