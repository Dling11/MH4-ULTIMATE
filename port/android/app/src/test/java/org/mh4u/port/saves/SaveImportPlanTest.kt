package org.mh4u.port.saves

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipFile

class SaveImportPlanTest {
    @get:Rule val temp = TemporaryFolder()
    private fun character(root: File) = File(root, SaveArchive.SCOPES.getValue("save") + "/00000001/user1")
    private fun save(root: File, text: String) { character(root).apply { parentFile!!.mkdirs(); writeText(text) } }
    private fun incoming(): File {
        val root = temp.newFolder(); save(root, "imported character")
        return temp.newFile().also { SaveArchive(root).export(it) }
    }

    @Test fun waitsForFinalGameFlushAndBacksUpThatProgressBeforeReplacingIt() = runBlocking {
        val root = temp.newFolder(); save(root, "old disk save")
        File(root, "overlay.json").writeText("my controls")
        val stopped = CompletableDeferred<Unit>(); val flushed = CompletableDeferred<Unit>()
        var closed = false
        val repository = SaveArchive(root)
        val archive = incoming()
        val import = async {
            SaveImportPlan(repository).restore(archive, closeAndWait = {
                stopped.complete(Unit)
                flushed.await()
                save(root, "final save flushed by shutdown")
                closed = true
            }, coreClosed = { closed })
        }
        stopped.await()
        assertEquals("old disk save", character(root).readText())
        assertTrue(repository.list().isEmpty())
        flushed.complete(Unit); import.await()
        assertEquals("imported character", character(root).readText())
        assertEquals("my controls", File(root, "overlay.json").readText())
        ZipFile(repository.list().single().file).use {
            assertEquals("final save flushed by shutdown", it.getInputStream(it.getEntry("save/00000001/user1")).bufferedReader().readText())
        }
    }

    @Test fun invalidImportDoesNotCloseTheGameOrTouchItsSave() = runBlocking {
        val root = temp.newFolder(); save(root, "keep me")
        val invalid = temp.newFile().also { it.writeText("not a save archive") }
        var stopped = false
        try {
            SaveImportPlan(SaveArchive(root)).restore(invalid, { stopped = true }, { true })
            fail("Invalid archive was accepted")
        } catch (_: Exception) { }
        assertFalse(stopped); assertEquals("keep me", character(root).readText())
        assertTrue(SaveArchive(root).list().isEmpty())
    }

    @Test fun stopFlagAloneCannotPermitReplacementBeforeFilesystemShutdown() = runBlocking {
        val root = temp.newFolder(); save(root, "still being used")
        var stopRequested = false
        try {
            SaveImportPlan(SaveArchive(root)).restore(incoming(), { stopRequested = true }, { false })
            fail("Open files were replaced")
        } catch (_: IllegalStateException) { }
        assertTrue(stopRequested); assertEquals("still being used", character(root).readText())
        assertTrue(SaveArchive(root).list().isEmpty())
    }

    @Test fun aFailedShutdownLeavesProgressAndBackupsUntouched() = runBlocking {
        val root = temp.newFolder(); save(root, "keep me")
        try {
            SaveImportPlan(SaveArchive(root)).restore(incoming(), { error("shutdown failed") }, { true })
            fail("Failed shutdown was ignored")
        } catch (_: IllegalStateException) { }
        assertEquals("keep me", character(root).readText())
        assertTrue(SaveArchive(root).list().isEmpty())
    }
}
