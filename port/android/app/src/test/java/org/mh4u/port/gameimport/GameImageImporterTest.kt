package org.mh4u.port.gameimport

import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.apache.commons.compress.archivers.zip.Zip64Mode
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.nio.channels.SeekableByteChannel
import java.nio.file.Files
import java.util.concurrent.CancellationException
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Synthetic headers and payloads only; these fixtures contain no game data. */
class GameImageImporterTest {
    @get:Rule val temp = TemporaryFolder()
    private val games get() = File(temp.root, "app/games").also { it.mkdirs() }
    private fun image(size: Int = 4096, magic: String = "NCSD") = ByteArray(size) { (it % 251).toByte() }
        .also { magic.toByteArray().copyInto(it, 0x100) }
    private class Source(private val file: File, override val size: Long = file.length(), override val name: String = file.name) : GameImportSource {
        var streamOpens = 0
        var channelOpens = 0
        override fun openStream() = file.inputStream().also { streamOpens++ }
        override fun openChannel(): SeekableByteChannel = Files.newByteChannel(file.toPath()).also { channelOpens++ }
    }
    private fun source(name: String = "MH4U.3ds", bytes: ByteArray = image()): Source =
        Source(File(temp.root, name).also { it.writeBytes(bytes) })
    private fun zip(entries: Map<String, ByteArray>, stored: Boolean = false): File = temp.newFile("game-${System.nanoTime()}.zip").also { file ->
        ZipOutputStream(file.outputStream()).use { archive ->
            for ((name, bytes) in entries) {
                val entry = ZipEntry(name)
                if (stored) { entry.method = ZipEntry.STORED; entry.size = bytes.size.toLong(); entry.crc = CRC32().apply { update(bytes) }.value }
                archive.putNextEntry(entry); archive.write(bytes); archive.closeEntry()
            }
        }
    }
    private fun sevenZip(entries: Map<String, ByteArray>): File = temp.newFile("game-${System.nanoTime()}.7z").also { file ->
        SevenZOutputFile(file).use { archive ->
            for ((name, bytes) in entries) {
                val entry = SevenZArchiveEntry().apply { this.name = name; size = bytes.size.toLong() }
                archive.putArchiveEntry(entry); archive.write(bytes); archive.closeArchiveEntry()
            }
        }
    }
    private fun importer(available: () -> Long = { 20L * 1024 * 1024 * 1024 }) = GameImageImporter(games, available)
    private fun failure(contains: String, action: () -> Unit) {
        try { action(); fail("Import should have failed") } catch (e: IOException) { assertTrue(e.message, e.message.orEmpty().contains(contains)) }
        assertTrue(games.listFiles().orEmpty().none { it.name.startsWith(".mh4u-import-") })
    }

    @Test fun zipExtractsOnlyOneImageDirectlyAndKeepsDownloadAndSaveSettings() {
        val data = image()
        val archive = zip(linkedMapOf("readme.txt" to "instructions".toByteArray(), "folder/MH4U.3ds" to data))
        val original = archive.readBytes()
        val save = File(games.parentFile, "sdmc/progress").apply { parentFile!!.mkdirs(); writeText("saved progress") }
        val controls = File(games.parentFile, "overlay.json").apply { writeText("custom controls") }
        val source = Source(archive)
        val progress = mutableListOf<GameImportProgress>()
        val added = importer().importGame(source, progress::add)
        assertArrayEquals(data, added.readBytes())
        assertEquals("MH4U.3ds", added.name)
        assertEquals(1, games.listFiles()!!.size)
        assertEquals(0, source.streamOpens) // no cached copy of the compressed archive
        assertEquals(1, source.channelOpens)
        assertArrayEquals(original, archive.readBytes())
        assertEquals("saved progress", save.readText()); assertEquals("custom controls", controls.readText())
        assertEquals(1f, progress.last().fraction)
    }

    @Test fun sevenZipExtractsLzmaImageAndSkipsOtherEntries() {
        val data = image(magic = "NCCH")
        val archive = sevenZip(linkedMapOf("readme.txt" to "notes".toByteArray(), "nested/MH4U.cxi" to data))
        val original = archive.readBytes()
        val source = Source(archive)
        assertArrayEquals(data, importer().importGame(source).readBytes())
        assertArrayEquals(original, archive.readBytes())
        assertEquals(0, source.streamOpens); assertEquals(1, source.channelOpens)
        assertEquals(listOf("MH4U.cxi"), games.list()!!.toList())
    }

    @Test fun zip64MetadataIsSupportedForLargeGameArchives() {
        val archive = temp.newFile("zip64.zip")
        val data = image()
        ZipArchiveOutputStream(archive).use { output ->
            output.setUseZip64(Zip64Mode.Always)
            output.putArchiveEntry(ZipArchiveEntry("MH4U.3ds"))
            output.write(data); output.closeArchiveEntry()
        }
        assertArrayEquals(data, importer().importGame(Source(archive)).readBytes())
    }

    @Test fun zipSymlinkIsNotTreatedAsAGameImage() {
        val archive = temp.newFile("symlink.zip")
        ZipArchiveOutputStream(archive).use { output ->
            val entry = ZipArchiveEntry("game.3ds").apply { unixMode = 0xA1FF }
            output.putArchiveEntry(entry); output.write(image()); output.closeArchiveEntry()
        }
        failure("unsupported format") { importer().importGame(Source(archive)) }
        assertTrue(games.list()!!.isEmpty())
    }

    @Test fun directImageWithUnknownProviderSizeStillWorks() {
        val data = image()
        val direct = File(temp.root, "game.cci").apply { writeBytes(data) }
        val source = Source(direct, -1)
        assertArrayEquals(data, importer().importGame(source).readBytes())
        assertEquals(1, source.streamOpens); assertEquals(0, source.channelOpens)
    }

    @Test fun importingSameFilenameDoesNotReplaceExistingGame() {
        val previous = File(games, "MH4U.3ds").apply { writeText("previous image") }
        val added = importer().importGame(source())
        assertNotEquals(previous, added); assertEquals("previous image", previous.readText())
        assertArrayEquals(image(), added.readBytes())
    }

    @Test fun rejectsInsufficientSpaceBeforeReadingImageData() {
        val source = source()
        failure("Not enough free space") { importer { 64L * 1024 * 1024 }.importGame(source) }
        assertEquals(0, source.streamOpens); assertTrue(games.list()!!.isEmpty())
    }

    @Test fun advertisedFourGiBSizeDoesNotOverflowStorageCalculation() {
        val source = Source(temp.newFile(), 4L * 1024 * 1024 * 1024, "game.3ds")
        failure("4160 MB") { importer { 3L * 1024 * 1024 * 1024 }.importGame(source) }
        assertEquals(0, source.streamOpens)
    }

    @Test fun storageFillingDuringCopyRemovesPartialImage() {
        var calls = 0
        failure("Storage filled up") { importer { if (++calls == 1) Long.MAX_VALUE else 0 }.importGame(source()) }
        assertTrue(games.list()!!.isEmpty())
    }

    @Test fun cancellationMidExtractionRemovesOnlyPartialOutput() {
        val previous = File(games, "existing.3ds").apply { writeText("keep") }
        val archive = zip(mapOf("game.3ds" to image(3 * 1024 * 1024)))
        var cancel = false
        try {
            importer().importGame(Source(archive), report = { if (it.bytes > 0) cancel = true },
                checkCancelled = { if (cancel) throw CancellationException() })
            fail("Import was not cancelled")
        } catch (_: CancellationException) { }
        assertEquals(listOf(previous.name), games.list()!!.toList())
        assertEquals("keep", previous.readText()); assertTrue(archive.exists())
    }

    @Test fun cancellationAtPromotionRemovesNewImageButKeepsExistingOne() {
        val previous = File(games, "existing.cci").apply { writeText("keep") }
        try {
            importer().importGame(source(), checkCancelled = {
                if (games.listFiles()!!.any { it.name == "MH4U.3ds" }) throw CancellationException()
            })
            fail("Import was not cancelled")
        } catch (_: CancellationException) { }
        assertEquals(listOf(previous.name), games.list()!!.toList())
    }

    @Test fun rejectsTraversalEntryWithoutWritingOutsideGames() {
        failure("unsafe game filename") { importer().importGame(Source(zip(mapOf("../../overwrite.3ds" to image())))) }
        assertFalse(File(temp.root, "overwrite.3ds").exists()); assertTrue(games.list()!!.isEmpty())
    }

    @Test fun rejectsAmbiguousArchiveInsteadOfGuessing() {
        failure("more than one game image") { importer().importGame(Source(zip(mapOf("one.3ds" to image(), "two.cci" to image())))) }
        assertTrue(games.list()!!.isEmpty())
    }

    @Test fun rejectsArchiveWithoutAnyGameImage() {
        failure("No .3ds") { importer().importGame(Source(zip(mapOf("update.cia" to image())))) }
        assertTrue(games.list()!!.isEmpty())
    }

    @Test fun rejectsFakeRenamedGameAndRemovesTemporaryOutput() {
        failure("not a readable 3DS game image") { importer().importGame(source(bytes = ByteArray(1024))) }
        assertTrue(games.list()!!.isEmpty())
    }

    @Test fun corruptedStoredZipFailsIntegrityCheck() {
        val archive = zip(mapOf("game.3ds" to image()), stored = true)
        val raw = archive.readBytes()
        raw[30 + "game.3ds".length + 1000] = (raw[30 + "game.3ds".length + 1000].toInt() xor 1).toByte()
        archive.writeBytes(raw)
        failure("integrity check") { importer().importGame(Source(archive)) }
        assertTrue(games.list()!!.isEmpty())
    }

    @Test fun zstandardZipIsRejectedWithoutLoadingAnUnbundledNativeDecoder() {
        val archive = zip(mapOf("game.3ds" to image()), stored = true)
        val bytes = archive.readBytes()
        bytes[8] = 93; bytes[9] = 0
        val central = (0..bytes.size - 4).first { i -> bytes[i] == 0x50.toByte() && bytes[i + 1] == 0x4b.toByte() && bytes[i + 2] == 1.toByte() && bytes[i + 3] == 2.toByte() }
        bytes[central + 10] = 93; bytes[central + 11] = 0
        archive.writeBytes(bytes)
        failure("unsupported format") { importer().importGame(Source(archive)) }
        assertTrue(games.list()!!.isEmpty())
    }

    @Test fun truncatedArchiveDoesNotReplaceExistingImage() {
        val previous = File(games, "existing.3ds").apply { writeText("keep") }
        val archive = sevenZip(mapOf("game.3ds" to image()))
        archive.writeBytes(archive.readBytes().dropLast(10).toByteArray())
        failure("archive could not be read") { importer().importGame(Source(archive)) }
        assertEquals(listOf(previous.name), games.list()!!.toList()); assertEquals("keep", previous.readText())
    }

    @Test fun incompleteDirectCopyIsNotPublished() {
        val direct = File(temp.root, "game.3ds").apply { writeBytes(image()) }
        failure("not fully copied") { importer().importGame(Source(direct, direct.length() + 1)) }
        assertTrue(games.list()!!.isEmpty())
    }

    @Test fun splitAndRarInputsGiveExtractionInstructionsBeforeOpening() {
        val file = temp.newFile()
        for (name in listOf("game.7z.001", "game.part1.rar", "game.rar", "game.z01", "game.zip.002")) {
            val source = Source(file, name = name)
            failure("extract part 1") { importer().importGame(source) }
            assertEquals(0, source.channelOpens); assertEquals(0, source.streamOpens)
        }
    }

    @Test fun abandonedExtractionCleanupLeavesOtherFilesAlone() {
        val stale = File(games, ".mh4u-import-00000000-0000-0000-0000-000000000000.partial").apply { writeText("partial") }
        val unrelated = File(games, "keep.partial").apply { writeText("keep") }
        GameImageImporter.removeAbandoned(games)
        assertFalse(stale.exists()); assertEquals("keep", unrelated.readText())
    }
}
