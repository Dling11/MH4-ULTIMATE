package org.mh4u.port.saves

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipFile

class NativeSaveImportTest {
    @get:Rule val temporary = TemporaryFolder()

    private class Entry(private val file: File, private val sizes: (File) -> Long? = { null }) : SaveImportEntry {
        override val name get() = file.name
        override val directory get() = file.isDirectory
        override val size get() = sizes(file) ?: file.length()
        override val identity get() = file.canonicalPath
        override fun children(): List<SaveImportEntry> = file.listFiles().orEmpty().map { Entry(it, sizes) }
        override fun open() = file.inputStream()
    }

    private fun put(root: File, path: String, bytes: ByteArray) = File(root, path).also { it.parentFile.mkdirs(); it.writeBytes(bytes) }
    private fun save(root: File) {
        val base = SaveArchive.SCOPES.getValue("save")
        put(root, "$base/00000001/system", ByteArray(512) { 3 })
        put(root, "$base/00000001/user1", "hunter one".toByteArray())
        put(root, "$base/00000001/user2", "hunter two".toByteArray())
        put(root, "$base/00000001.metadata", ByteArray(16))
        put(root, SaveArchive.SCOPES.getValue("extra") + "/user/card1", "guild card".toByteArray())
        put(root, SaveArchive.SCOPES.getValue("extra") + "/user/otomo1", "palico".toByteArray())
    }
    private fun files(root: File) = root.walkTopDown().filter { it.isFile }.associate { it.relativeTo(root).invariantSeparatorsPath to it.readBytes().toList() }
    private fun fails(block: () -> Unit) { try { block(); fail("Expected rejection") } catch (_: IllegalStateException) {} }

    @Test fun nativeFolderImportPreservesAllSlotsMetadataExtraDataAndExistingSettings() {
        val source = temporary.newFolder(); save(source); val original = files(source)
        val destination = temporary.newFolder(); save(destination)
        put(destination, "overlay.json", "my controls".toByteArray()); put(destination, "mh4u-app.ini", "my settings".toByteArray())
        val prepared = NativeSaveImport().prepare(Entry(source), temporary.newFolder())
        assertEquals(listOf(1, 2), prepared.slots); assertTrue(prepared.extraData)
        SaveArchive(destination).restore(prepared.backup.file)
        original.forEach { (path, bytes) -> assertEquals(path, bytes, File(destination, path).readBytes().toList()) }
        assertEquals("my controls", File(destination, "overlay.json").readText())
        assertEquals("my settings", File(destination, "mh4u-app.ini").readText())
        assertEquals(original, files(source)); assertEquals(1, SaveArchive(destination).list().size)
    }

    @Test fun selectingSdmcItselfAlsoWorks() {
        val source = temporary.newFolder(); save(source)
        val prepared = NativeSaveImport().prepare(Entry(File(source, "sdmc")), temporary.newFolder())
        assertEquals(listOf(1, 2), prepared.slots); assertTrue(prepared.backup.file.isFile)
    }

    @Test fun parentGameAndEuropeanFoldersAreRejectedWithoutChangingSource() {
        val source = temporary.newFolder(); save(source); val before = files(source)
        fails { NativeSaveImport().prepare(Entry(temporary.newFolder()), temporary.newFolder()) }
        val title = File(source, SaveArchive.SCOPES.getValue("save")).parentFile
        assertTrue(title.renameTo(File(title.parentFile, "00126100")))
        fails { NativeSaveImport().prepare(Entry(source), temporary.newFolder()) }
        assertTrue(File(title.parentFile, "00126100").renameTo(title)); assertEquals(before, files(source))
    }

    @Test fun brokenOrIncompleteCharacterSavesAreRejectedBeforeRestore() {
        for (bad in listOf("system", "user1")) {
            val source = temporary.newFolder(); save(source)
            File(source, SaveArchive.SCOPES.getValue("save") + "/00000001/$bad").writeBytes(byteArrayOf())
            val cache = temporary.newFolder()
            fails { NativeSaveImport().prepare(Entry(source), cache) }
            assertTrue(cache.listFiles().orEmpty().isEmpty())
        }
    }

    @Test fun providerSizeMismatchOrOversizeIsRejectedAndTemporaryDataIsRemoved() {
        for (badSize in listOf(1000L, 64L * 1024 * 1024 + 1)) {
            val source = temporary.newFolder(); save(source); val before = files(source); val cache = temporary.newFolder()
            fails { NativeSaveImport().prepare(Entry(source) { if (it.name == "user1") badSize else null }, cache) }
            assertEquals(before, files(source)); assertTrue(cache.listFiles().orEmpty().isEmpty())
        }
    }

    @Test fun unsafeProviderNamesAreRejected() {
        val source = temporary.newFolder(); save(source)
        val bad = object : SaveImportEntry by Entry(source) {
            override fun children(): List<SaveImportEntry> = listOf(object : SaveImportEntry by Entry(source) { override val name = "../overlay.json" })
        }
        fails { NativeSaveImport().prepare(bad, temporary.newFolder()) }
    }

    @Test fun missingExtraDataIsReportedExplicitly() {
        val source = temporary.newFolder(); save(source)
        File(source, SaveArchive.SCOPES.getValue("extra")).deleteRecursively()
        val prepared = NativeSaveImport().prepare(Entry(source), temporary.newFolder())
        assertFalse(prepared.extraData)
    }

    @Test fun tooManyProfilesAreRejectedRatherThanSilentlyIgnored() {
        val source = temporary.newFolder(); save(source)
        for (number in 1..8) File(source, "sdmc/Nintendo 3DS/" + number.toString(16).padStart(32, '0')).mkdirs()
        val before = files(source); val cache = temporary.newFolder()
        fails { NativeSaveImport().prepare(Entry(source), cache) }
        assertEquals(before, files(source)); assertTrue(cache.listFiles().orEmpty().isEmpty())
    }

    @Test fun exportedPathsAreNativeDiscoverableAndKeepItsExtraDataRelation() {
        val archiveRoot = temporary.newFolder(); save(archiveRoot)
        val archive = temporary.newFile(); SaveArchive(archiveRoot).export(archive)
        val exported = temporary.newFolder()
        ZipFile(archive).use { zip -> zip.entries().asSequence().filter { it.name != "manifest.json" }.forEach { entry ->
            SaveArchive.transferPaths(entry.name).forEach { path -> put(exported, path, zip.getInputStream(entry).readBytes()) }
        } }
        val compact = File(exported, "00040000/00126300/data/00000001")
        assertTrue(File(compact, "system").isFile()); assertTrue(File(compact, "user2").isFile())
        val nativeId1 = compact.parentFile.parentFile.parentFile.parentFile
        assertEquals(exported.canonicalPath, nativeId1.canonicalPath)
        assertTrue(File(nativeId1, "extdata/00000000/00001263/user/otomo1").isFile)
        // One top-level destination plus the dated export and four compact levels: depth six.
        assertEquals(6, 2 + compact.relativeTo(exported).invariantSeparatorsPath.split('/').size)
        val prepared = NativeSaveImport().prepare(Entry(exported), temporary.newFolder())
        assertEquals(listOf(1, 2), prepared.slots)
        SaveArchive(exported).validate(prepared.backup.file)
        fails { SaveArchive.transferPaths("save/../../overlay.json") }
    }
}
