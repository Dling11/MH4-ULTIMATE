package org.mh4u.port.overlay

import org.junit.Assert.*
import org.junit.Test

class TapFeedbackTest {
    @Test fun quickTapRemainsVisibleThenStopsSchedulingFrames() {
        val feedback = TapFeedback()
        feedback.release("combo", 1000)
        assertEquals(1f, feedback.strength("combo", false, 1000), .001f)
        assertEquals(.5f, feedback.strength("combo", false, 1050), .001f)
        assertEquals(0f, feedback.strength("combo", false, 1100), .001f)
        assertFalse(feedback.pending(1100))
    }

    @Test fun heldButtonsAndRepeatedTapsRemainIndependent() {
        val feedback = TapFeedback()
        feedback.release("first", 1000)
        assertEquals(1f, feedback.strength("second", true, 1050), .001f)
        assertEquals(.5f, feedback.strength("first", false, 1050), .001f)
        feedback.release("first", 1070)
        assertEquals(.9f, feedback.strength("first", false, 1080), .001f)
        assertEquals(1f, feedback.strength("first", true, 1080), .001f)
        assertEquals(0f, feedback.strength("first", false, 1081), .001f)
    }

    @Test fun cancellingTouchesClearsAllVisualFeedback() {
        val feedback = TapFeedback()
        feedback.release("first", 1000)
        feedback.release("second", 1001)
        feedback.clear()
        assertEquals(0f, feedback.strength("first", false, 1002), .001f)
        assertFalse(feedback.pending(1002))
    }
}
