package org.mh4u.port.overlay

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LayoutToolsTest {
    @get:Rule val folder = TemporaryFolder()
    private fun sample() = OverlayLayout(mutableListOf(Control("a", Control.Kind.BUTTON, "A", 1, .8f, .6f, .12f)))

    @Test fun aWholeDragGestureIsOneUndoAndCancelStillReturnsTheOriginal() {
        val original = sample(); val session = LayoutEditSession(original)
        session.checkpoint()
        repeat(100) { session.draft.controls[0].x = it / 100f }
        assertTrue(session.canUndo); assertTrue(session.undo())
        assertEquals(original.controls, session.draft.controls)
        assertFalse(session.canUndo)
        session.checkpoint(); session.draft.addCombo("R+A", 3, false)
        assertEquals(original.controls, session.cancel().controls)
    }
    @Test fun undoRestoresDeletedCombosAndAppearanceWithoutWritingTheSavedFile() {
        val original = sample(); original.addCombo("R+X+A", 37, true)
        val file = folder.newFile(); original.saveTo(file); val bytes = file.readBytes()
        val session = LayoutEditSession(original)
        session.checkpoint(); session.draft.controls.removeAt(1)
        session.checkpoint(); session.draft.opacity = .2f
        session.undo(); assertEquals(original.opacity, session.draft.opacity)
        session.undo(); assertEquals(original.controls, session.draft.controls)
        assertArrayEquals(bytes, file.readBytes())
    }
    @Test fun undoHistoryIsBoundedAndUnchangedTapsDoNotConsumeUndoSteps() {
        val session = LayoutEditSession(sample())
        repeat(30) { session.checkpoint(); session.draft.controls[0].x = it / 100f }
        session.checkpoint() // no change
        var count = 0; while (session.undo()) count++
        assertTrue(count in 1..20)
        assertFalse(session.canUndo)
    }
    @Test fun namedLayoutsAreIndependentAndKeepButtonsCombosAndAppearance() {
        val repository = LayoutProfiles(folder.newFolder())
        val original = sample().apply { palette = "gold"; opacity = .4f; haptics = false; addCombo("R+X+A", 37, true) }
        val profile = repository.save("Great Sword", original)
        original.controls[0].x = .1f
        val loaded = repository.load(profile.id)
        assertEquals(.8f, loaded.controls[0].x, .001f)
        assertTrue(loaded.controls.any { it.kind == Control.Kind.COMBO && it.latch })
        assertEquals("gold", loaded.palette); assertFalse(loaded.haptics)
        assertEquals(1, loaded.controls.count { it.kind == Control.Kind.SCREEN })
        assertEquals("Great Sword", repository.list().single().name)
        repository.delete(profile.id); assertTrue(repository.list().isEmpty())
        assertEquals(.1f, original.controls[0].x, .001f)
    }
    @Test fun addingTheSecondScreenShortcutDoesNotRewriteOrDuplicateExistingControls() {
        val file = folder.newFile(); val original = sample(); original.saveTo(file); val bytes = file.readBytes()
        val loaded = OverlayLayout.loadFrom(file).ensureUtilityButtons(false).ensureUtilityButtons(false)
        assertEquals(original.controls[0], loaded.controls[0])
        assertEquals(1, loaded.controls.count { it.kind == Control.Kind.SCREEN })
        assertArrayEquals(bytes, file.readBytes())
    }
    @Test fun profileNamesCannotSilentlyOverwriteOtherProfilesOrEscapeTheirFolder() {
        val repository = LayoutProfiles(folder.newFolder()); repository.save("My layout", sample())
        try { repository.save("my layout", sample()); fail("Expected duplicate name rejection") } catch (_: IllegalArgumentException) {}
        try { repository.load("../../other"); fail("Expected path rejection") } catch (_: IllegalArgumentException) {}
        assertEquals(1, repository.list().size)
    }
}
