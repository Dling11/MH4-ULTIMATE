package org.mh4u.port

import org.junit.Assert.*
import org.junit.Test

class GamePlaybackStateTest {
    @Test fun soloPlayDoesNotStartBeforeTheActivityIsForeground() {
        assertTrue(GamePlaybackState().shouldPause(inRoom = false))
    }

    @Test fun foregroundOverlaysNeverPauseSoloPlay() {
        val state = GamePlaybackState()
        state.onResume()
        // Menus and editing do not alter foreground state.
        repeat(10) { assertFalse(state.shouldPause(inRoom = false)) }
    }

    @Test fun leavingTheAppPausesSoloPlayAndReturningResumesIt() {
        val state = GamePlaybackState()
        state.onResume(); state.onPause()
        assertTrue(state.shouldPause(inRoom = false))
        state.onResume()
        assertFalse(state.shouldPause(inRoom = false))
    }

    @Test fun roomConnectionsRetainTheirExistingNoPauseBehaviour() {
        val state = GamePlaybackState()
        state.onResume(); assertFalse(state.shouldPause(inRoom = true))
        state.onPause(); assertFalse(state.shouldPause(inRoom = true))
        assertTrue(state.shouldPause(inRoom = false))
    }
}
