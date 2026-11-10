package org.mh4u.port

import org.junit.Assert.*
import org.junit.Test

class SettingsApplyGateTest {
    private val settings = "[Display]\nresolution=2\nscreens=overlay\nfps_counter=off\n[Audio]\nvolume=80\n[Android]\noverlay_opacity=90\n"
    private fun snapshot(text: String = settings, extras: String = "swap=0", touch: String = "top-right 25 85") = SettingsApplyGate.snapshot(text, extras, touch)

    @Test fun unchangedMenuResumeDoesNotRestartCoreAudioOrShaders() {
        val gate = SettingsApplyGate(); var calls = 0
        repeat(20) { gate.apply(snapshot()) { calls++ } }
        assertEquals(1, calls)
    }
    @Test fun realResolutionAudioOrScreenChangesStillApply() {
        val gate = SettingsApplyGate(); var calls = 0
        for (state in listOf(snapshot(), snapshot(settings.replace("resolution=2", "resolution=3")),
            snapshot(settings.replace("volume=80", "volume=40")), snapshot(extras = "swap=1"), snapshot(touch = "bottom-right 40 85"))) {
            assertTrue(gate.apply(state) { calls++ })
        }
        assertEquals(5, calls)
    }
    @Test fun overlayOrCounterChangesDoNotRebuildTheCore() {
        assertEquals(snapshot(), snapshot(settings.replace("overlay_opacity=90", "overlay_opacity=30")))
        assertEquals(snapshot(), snapshot(settings.replace("fps_counter=off", "fps_counter=on")))
        assertNotEquals(snapshot(), snapshot(settings + "[Multiplayer]\nnickname=Hunter\n"))
    }
    @Test fun aNewGameAndAnExplicitWindowRefreshStillApply() {
        val gate = SettingsApplyGate(); var calls = 0
        gate.apply(snapshot()) { calls++ }; gate.reset(); gate.apply(snapshot()) { calls++ }
        gate.apply(snapshot(), force = true) { calls++ }
        assertEquals(3, calls)
    }
    @Test fun failedSubmissionDoesNotMarkTheSettingsAsApplied() {
        val gate = SettingsApplyGate()
        try { gate.apply(snapshot()) { error("submission failed") }; fail("Expected failure") } catch (_: IllegalStateException) {}
        assertTrue(gate.apply(snapshot()) {})
    }

    @Test fun repeatedHideShowAndAnUnchangedResumeNeverRestartAudio() {
        val gate = SettingsApplyGate(); var coreCalls = 0; var layoutCalls = 0
        fun submit(ini: String) = gate.apply(snapshot(ini), coreSnapshot = SettingsApplyGate.coreSnapshot(ini),
            layoutAction = { layoutCalls++ }) { coreCalls++ }
        submit(settings)
        repeat(5) {
            submit(settings.replace("screens=overlay", "screens=single"))
            submit(settings.replace("screens=overlay", "screens=single")) // Resume after hiding
            submit(settings)
        }
        assertEquals(1, coreCalls)
        assertEquals(10, layoutCalls)
    }

    @Test fun layoutExtrasAndCornerChangesUseTheLightweightPath() {
        val gate = SettingsApplyGate(); var coreCalls = 0; var layoutCalls = 0
        val core = SettingsApplyGate.coreSnapshot(settings)
        for (state in listOf(snapshot(), snapshot(extras = "swap=1"), snapshot(touch = "bottom-right 40 85"))) {
            gate.apply(state, coreSnapshot = core, layoutAction = { layoutCalls++ }) { coreCalls++ }
        }
        assertEquals(1, coreCalls)
        assertEquals(2, layoutCalls)
    }

    @Test fun realAudioResolutionAndInputChangesStillUseTheFullPath() {
        for (modified in listOf(settings.replace("volume=80", "volume=40"),
            settings.replace("resolution=2", "resolution=3"), settings + "[Controls]\nprofile=alternate\n")) {
            val gate = SettingsApplyGate(); var coreCalls = 0; var layoutCalls = 0
            for (ini in listOf(settings, modified)) {
                gate.apply(snapshot(ini), coreSnapshot = SettingsApplyGate.coreSnapshot(ini),
                    layoutAction = { layoutCalls++ }) { coreCalls++ }
            }
            assertEquals(2, coreCalls)
            assertEquals(0, layoutCalls)
        }
    }

    @Test fun forceAndNewSessionsCannotReuseAnOldLayoutOnlyDecision() {
        val gate = SettingsApplyGate(); var coreCalls = 0; var layoutCalls = 0
        fun submit(ini: String, force: Boolean = false) = gate.apply(snapshot(ini), force,
            coreSnapshot = SettingsApplyGate.coreSnapshot(ini), layoutAction = { layoutCalls++ }) { coreCalls++ }
        submit(settings)
        submit(settings.replace("screens=overlay", "screens=single"), force = true)
        gate.reset()
        submit(settings)
        assertEquals(3, coreCalls)
        assertEquals(0, layoutCalls)
    }

    @Test fun failedLayoutSubmissionCanBeRetriedWithoutRecreatingAudio() {
        val gate = SettingsApplyGate(); var coreCalls = 0
        val core = SettingsApplyGate.coreSnapshot(settings)
        gate.apply(snapshot(), coreSnapshot = core, layoutAction = {}) { coreCalls++ }
        val hidden = snapshot(settings.replace("screens=overlay", "screens=single"))
        try {
            gate.apply(hidden, coreSnapshot = core, layoutAction = { error("queue rejected") }) { coreCalls++ }
            fail("Expected submission failure")
        } catch (_: IllegalStateException) { }
        var layoutCalls = 0
        assertTrue(gate.apply(hidden, coreSnapshot = core, layoutAction = { layoutCalls++ }) { coreCalls++ })
        assertEquals(1, coreCalls)
        assertEquals(1, layoutCalls)
    }
}
