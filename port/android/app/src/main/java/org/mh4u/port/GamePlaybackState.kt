package org.mh4u.port

/** Menus and the layout editor are live overlays. Leaving the app pauses solo play. */
class GamePlaybackState {
    private var foreground = false

    fun onResume() { foreground = true }
    fun onPause() { foreground = false }
    fun shouldPause(inRoom: Boolean): Boolean = !inRoom && !foreground
}
