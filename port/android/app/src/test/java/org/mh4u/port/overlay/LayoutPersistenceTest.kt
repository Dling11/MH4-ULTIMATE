package org.mh4u.port.overlay

import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder

class LayoutPersistenceTest {
    @Test fun addingSpeedPreservesExistingControlsAndDoesNotRewriteTheFile() {
        val file = folder.newFile("old-layout.json")
        val original = sample()
        original.addCombo("R+X+A", 37, false).apply { x = .71f; y = .32f }
        original.saveTo(file)
        val bytes = file.readBytes()
        val loaded = OverlayLayout.loadFrom(file).ensureSpeedButton(false).ensureSpeedButton(false)
        assertEquals(original.controls, loaded.controls.filter { it.kind != Control.Kind.SPEED })
        assertEquals(1, loaded.controls.count { it.kind == Control.Kind.SPEED })
        assertArrayEquals(bytes, file.readBytes())
        loaded.saveTo(file)
        assertEquals(1, OverlayLayout.loadFrom(file).ensureSpeedButton(false).controls.count { it.kind == Control.Kind.SPEED })
    }
    @get:Rule val folder = TemporaryFolder()

    private fun sample() = OverlayLayout(mutableListOf(Control("a", Control.Kind.BUTTON, "A", 1, .8f, .6f, .12f)))

    @Test fun editedButtonsAndCombosSurviveReload() {
        val file = folder.newFile("overlay.json")
        val session = LayoutEditSession(sample())
        session.draft.controls[0].apply { x = .41f; y = .23f; size = .16f; visible = false }
        session.draft.addCombo("R+X+A", 0x181, true).apply { x = .67f; y = .71f }
        session.draft.opacity = .75f; session.draft.haptics = false; session.draft.palette = "blue"
        session.commit(file)
        val loaded = OverlayLayout.loadFrom(file)
        assertEquals(session.draft.controls, loaded.controls)
        assertEquals(.75f, loaded.opacity, .001f)
        assertFalse(loaded.haptics)
        assertEquals("blue", loaded.palette)
    }

    @Test fun cancelKeepsOriginalAndPreviouslySavedFile() {
        val file = folder.newFile("overlay.json")
        val original = sample(); original.saveTo(file)
        val bytes = file.readBytes()
        val session = LayoutEditSession(original)
        session.draft.controls[0].x = .1f
        session.draft.controls.clear()
        assertEquals(original.controls, session.cancel().controls)
        assertArrayEquals(bytes, file.readBytes())
    }

    @Test fun savingAppearanceAfterReopeningPreservesEditedPosition() {
        val file = folder.newFile("overlay.json")
        val session = LayoutEditSession(sample())
        session.draft.controls[0].x = .32f; session.commit(file)
        val reopened = OverlayLayout.loadFrom(file)
        reopened.opacity = .4f; reopened.saveTo(file)
        assertEquals(.32f, OverlayLayout.loadFrom(file).controls[0].x, .001f)
        assertEquals(1, folder.root.listFiles()!!.size)
    }
}
