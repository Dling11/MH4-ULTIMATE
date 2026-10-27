package org.mh4u.port.saves

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class SaveArchiveTest {
    @get:Rule val folder = TemporaryFolder()
    private fun put(root: File, relative: String, text: String) = File(root, relative).also { checkNotNull(it.parentFile).mkdirs(); it.writeText(text) }
    private fun save(root: File, text: String) = put(root, "${SaveArchive.SCOPES.getValue("save")}/00000001/user1", text)
    private fun extra(root: File, text: String) = put(root, "${SaveArchive.SCOPES.getValue("extra")}/user/quest1", text)
    private fun scopeFiles(root: File): Map<String, String> = SaveArchive.SCOPES.values.flatMap { relative ->
        File(root, relative).walkTopDown().filter { it.isFile }.map { it.relativeTo(root).invariantSeparatorsPath to it.readText() }.toList()
    }.toMap()
    private fun fails(block: () -> Unit) { try { block(); fail("Expected a rejected operation") } catch (_: Exception) {} }
    private fun mutate(source: File, edit: (MutableMap<String, ByteArray>) -> Unit): File {
        val files = ZipFile(source).use { zip -> zip.entries().asSequence().associate { it.name to zip.getInputStream(it).readBytes() }.toMutableMap() }
        edit(files)
        return folder.newFile().also { target -> ZipOutputStream(target.outputStream()).use { zip ->
            files.forEach { (name, bytes) -> zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry() }
        } }
    }

    @Test fun restoresProgressAndExtraDataWithoutTouchingSettingsRomOrSystemFiles() {
        val root = folder.newFolder(); val repository = SaveArchive(root)
        save(root, "old character"); extra(root, "old quests")
        val backup = repository.create()
        save(root, "new character"); extra(root, "new quests")
        put(root, "${SaveArchive.SCOPES.getValue("extra")}/user/stale", "remove me")
        val unrelated = listOf("overlay.json", "mh4u-app.ini", "nand/config", "games/game.3ds", "sdmc/unrelated", "${SaveArchive.SCOPES.getValue("save").substringBeforeLast('/')}/content/update.app")
            .map { put(root, it, "unchanged $it") }
        repository.restore(backup.file)
        assertEquals("old character", saveFile(root).readText())
        assertEquals("old quests", File(root, "${SaveArchive.SCOPES.getValue("extra")}/user/quest1").readText())
        assertFalse(File(root, "${SaveArchive.SCOPES.getValue("extra")}/user/stale").exists())
        unrelated.forEach { assertTrue(it.readText().startsWith("unchanged ")) }
        val safety = repository.list().first()
        assertEquals("Before restore", safety.reason)
        ZipFile(safety.file).use { assertEquals("new character", it.getInputStream(it.getEntry("save/00000001/user1")).bufferedReader().readText()) }
    }
    private fun saveFile(root: File) = File(root, "${SaveArchive.SCOPES.getValue("save")}/00000001/user1")

    @Test fun anEmptyCharacterFileIsNotPublishedAsAValidBackup() {
        val root = folder.newFolder(); save(root, "")
        val repository = SaveArchive(root)
        fails { repository.create() }
        assertTrue(repository.list().isEmpty())
        assertEquals("", saveFile(root).readText())
    }

    @Test fun identicalBackupsReuseACopyAndDifferentBackupsKeepOnlyNewestThreeEvenInOneMillisecond() {
        val root = folder.newFolder(); val repository = SaveArchive(root) { 123456L }
        save(root, "one"); val first = repository.create()
        assertEquals(first.file, repository.create().file)
        assertEquals(1, repository.list().size)
        save(root, "two"); repository.create()
        save(root, "three"); repository.create()
        save(root, "four"); val last = repository.create()
        assertEquals(3, repository.list().size)
        assertFalse(first.file.exists())
        assertTrue(last.file.exists())
        assertEquals(last, repository.list().first())
    }

    @Test fun firstImportWorksWithNoExistingCharacterAndDoesNotCreateAnEmptyBackup() {
        val source = folder.newFolder(); save(source, "imported"); extra(source, "quests")
        val archive = folder.newFile(); SaveArchive(source).export(archive)
        val destination = folder.newFolder(); SaveArchive(destination).restore(archive)
        assertEquals(scopeFiles(source), scopeFiles(destination))
        assertTrue(SaveArchive(destination).list().isEmpty())
    }

    @Test fun corruptPayloadIsRejectedBeforeCurrentProgressOrBackupListChanges() {
        val root = folder.newFolder(); val repository = SaveArchive(root)
        save(root, "good"); val backup = repository.create()
        val damaged = mutate(backup.file) { it["save/00000001/user1"] = "bad!".toByteArray() }
        save(root, "current"); val before = scopeFiles(root)
        fails { repository.restore(damaged) }
        assertEquals(before, scopeFiles(root)); assertEquals(1, repository.list().size)
    }

    @Test fun traversalAndWrongTitleAreRejectedWithoutWritingOutsideSaveFolders() {
        val root = folder.newFolder(); val repository = SaveArchive(root)
        save(root, "good"); val backup = repository.create()
        val before = scopeFiles(root)
        val traversal = mutate(backup.file) { map ->
            val manifest = JSONObject(String(map.getValue("manifest.json")))
            manifest.getJSONArray("files").getJSONObject(0).put("path", "save/../../overlay.json")
            map["save/../../overlay.json"] = map.remove("save/00000001/user1")!!
            map["manifest.json"] = manifest.toString().toByteArray()
        }
        fails { repository.restore(traversal) }
        assertFalse(File(root, "overlay.json").exists()); assertEquals(before, scopeFiles(root))
        val wrongTitle = mutate(backup.file) { map ->
            val manifest = JSONObject(String(map.getValue("manifest.json"))).put("titleId", "another-game")
            map["manifest.json"] = manifest.toString().toByteArray()
        }
        fails { repository.restore(wrongTitle) }; assertEquals(before, scopeFiles(root))
    }

    @Test fun sizeLimitIsCheckedBeforeExtractingOrReplacingAnything() {
        val root = folder.newFolder(); save(root, "good"); val repository = SaveArchive(root)
        val archive = repository.create()
        val huge = mutate(archive.file) { map ->
            val manifest = JSONObject(String(map.getValue("manifest.json")))
            manifest.getJSONArray("files").getJSONObject(0).put("bytes", 1024L * 1024 * 1024)
            map["manifest.json"] = manifest.toString().toByteArray()
        }
        fails { repository.restore(huge) }; assertEquals("good", saveFile(root).readText())
    }

    private class Interrupted : Error()
    @Test fun interruptedRestoreRecoversTheOriginalProgressAtEveryMoveBoundary() {
        for (boundary in listOf("old-save", "new-save", "old-extra", "new-extra")) {
            val source = folder.newFolder(); save(source, "archive"); extra(source, "archive quests")
            val archive = folder.newFile(); SaveArchive(source).export(archive)
            val root = folder.newFolder(); save(root, "current"); extra(root, "current quests")
            val before = scopeFiles(root)
            try { SaveArchive(root).restore(archive) { if (it == boundary) throw Interrupted() }; fail("Expected interruption") }
            catch (_: Interrupted) {}
            assertTrue(File(root, ".save-restore/journal.json").exists())
            SaveArchive(root).recover()
            assertEquals(boundary, before, scopeFiles(root))
            assertFalse(File(root, ".save-restore").exists())
        }
    }

    @Test fun ordinaryWriteFailureRollsBackImmediately() {
        val source = folder.newFolder(); save(source, "archive")
        val archive = folder.newFile(); SaveArchive(source).export(archive)
        val root = folder.newFolder(); save(root, "current"); extra(root, "keep")
        val before = scopeFiles(root)
        fails { SaveArchive(root).restore(archive) { if (it == "new-save") error("Injected move failure") } }
        assertEquals(before, scopeFiles(root)); assertFalse(File(root, ".save-restore").exists())
    }

    @Test fun restoringAnOlderBackupStillWorksWhenSafetyBackupPrunesThatArchive() {
        val root = folder.newFolder(); var clock = 100L; val repository = SaveArchive(root) { clock++ }
        save(root, "one"); val oldest = repository.create()
        save(root, "two"); repository.create()
        save(root, "three"); repository.create()
        save(root, "four unsnapshotted")
        repository.restore(oldest.file)
        assertEquals("one", saveFile(root).readText()); assertFalse(oldest.file.exists())
        assertEquals(3, repository.list().size)
    }

    @Test fun anArchiveWithoutExtraDataRemovesOnlyTheCurrentMh4uExtraData() {
        val source = folder.newFolder(); save(source, "archive")
        val archive = folder.newFile(); SaveArchive(source).export(archive)
        val root = folder.newFolder(); save(root, "current"); extra(root, "later quests")
        put(root, "sdmc/other-game/data", "untouched")
        SaveArchive(root).restore(archive)
        assertFalse(File(root, SaveArchive.SCOPES.getValue("extra")).exists())
        assertEquals("untouched", File(root, "sdmc/other-game/data").readText())
    }
}
