package org.mh4u.port.overlay

/** A short visual release fade. This never delays the game's input release. */
internal class TapFeedback(private val durationMs: Long = 100) {
    private val released = HashMap<String, Long>()

    fun release(id: String, nowMs: Long) { released[id] = nowMs }

    fun strength(id: String, pressed: Boolean, nowMs: Long): Float {
        if (pressed) { released.remove(id); return 1f }
        val at = released[id] ?: return 0f
        val remaining = 1f - (nowMs - at).coerceAtLeast(0).toFloat() / durationMs
        if (remaining <= 0f) released.remove(id)
        return remaining.coerceIn(0f, 1f)
    }

    fun pending(nowMs: Long): Boolean {
        released.entries.removeAll { nowMs - it.value >= durationMs }
        return released.isNotEmpty()
    }

    fun clear() = released.clear()
}
