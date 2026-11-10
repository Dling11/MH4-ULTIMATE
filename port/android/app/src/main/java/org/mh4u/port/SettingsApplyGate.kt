package org.mh4u.port

import java.util.Locale

/** Unchanged settings do nothing; layout-only changes keep audio and shaders running. */
class SettingsApplyGate {
    private var applied: String? = null
    private var appliedCore: String? = null
    fun reset() { applied = null; appliedCore = null }
    fun apply(snapshot: String, force: Boolean = false, coreSnapshot: String? = null,
        layoutAction: (() -> Unit)? = null, action: () -> Unit): Boolean {
        if (!force && applied == snapshot) return false
        if (!force && coreSnapshot != null && appliedCore == coreSnapshot && layoutAction != null)
            layoutAction()
        else action()
        applied = snapshot
        appliedCore = coreSnapshot
        return true
    }

    companion object {
        fun coreSnapshot(ini: String): String = snapshot(ini, "", "", includeLayout = false)

        fun snapshot(ini: String, screenExtras: String, touchOverlay: String, includeLayout: Boolean = true): String {
            val values = sortedMapOf<String, String>()
            var section = ""
            for (raw in ini.lineSequence()) {
                val line = raw.trim()
                if (line.startsWith('[') && line.endsWith(']')) {
                    section = line.substring(1, line.length - 1).lowercase(Locale.ROOT)
                    continue
                }
                if (section !in setOf("game", "display", "audio", "controls", "multiplayer") || line.startsWith('#') || line.startsWith(';')) continue
                val equals = line.indexOf('='); if (equals <= 0) continue
                val key = line.substring(0, equals).trim().lowercase(Locale.ROOT)
                if (section == "display" && key in setOf("fps_counter", "fullscreen")) continue
                if (!includeLayout && section == "display" && key == "screens") continue
                values["$section/$key"] = line.substring(equals + 1).substringBefore(" #").trim()
            }
            values["screen-extras"] = screenExtras
            values["touch-overlay"] = touchOverlay
            return values.entries.joinToString("") { (key, value) -> "${key.length}:$key${value.length}:$value" }
        }
    }
}
