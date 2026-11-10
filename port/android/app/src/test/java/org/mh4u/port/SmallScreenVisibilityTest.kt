package org.mh4u.port

import org.junit.Assert.*
import org.junit.Test

class SmallScreenVisibilityTest {
    @Test fun hidingAndShowingRestoresEveryExistingArrangement() {
        for (layout in listOf("overlay", "centered-top", "centered-bottom", "stacked", "large", "side", "hybrid")) {
            val original = ScreenChoice(layout)
            val hidden = SmallScreenVisibility.toggle(original)
            assertTrue(SmallScreenVisibility.hidden(hidden.display))
            assertEquals(original, hidden.previous)
            val shown = SmallScreenVisibility.toggle(hidden.display, hidden.previous)
            assertEquals(original, shown.display)
        }
    }

    @Test fun hidingAlwaysShowsMainScreenAndRestoresAnExistingSwap() {
        val original = ScreenChoice("centered-bottom", swapped = true)
        val hidden = SmallScreenVisibility.toggle(original)
        assertEquals(ScreenChoice("single", swapped = false), hidden.display)
        assertEquals(original, SmallScreenVisibility.toggle(hidden.display, hidden.previous).display)
    }

    @Test fun anExistingMainOnlyChoiceCanShowTheTouchScreenWithoutHistory() {
        assertEquals(ScreenChoice("overlay"), SmallScreenVisibility.toggle(ScreenChoice("single")).display)
    }

    @Test fun invalidOrHiddenHistoryFallsBackToAVisibleTouchScreen() {
        for (previous in listOf(ScreenChoice("unknown"), ScreenChoice("single"))) {
            val shown = SmallScreenVisibility.toggle(ScreenChoice("single"), previous)
            assertEquals(ScreenChoice("overlay"), shown.display)
            assertFalse(SmallScreenVisibility.hidden(shown.display))
        }
    }

    @Test fun aBottomOnlyChoiceIsVisibleAndCanBeRestored() {
        val original = ScreenChoice("single", swapped = true)
        assertFalse(SmallScreenVisibility.hidden(original))
        val hidden = SmallScreenVisibility.toggle(original)
        assertEquals(original, SmallScreenVisibility.toggle(hidden.display, hidden.previous).display)
    }

    @Test fun hidingANewlyChosenLayoutReplacesOldRestoreHistory() {
        val newer = ScreenChoice("stacked")
        val hidden = SmallScreenVisibility.toggle(newer, ScreenChoice("side"))
        assertEquals(newer, SmallScreenVisibility.toggle(hidden.display, hidden.previous).display)
    }
}
