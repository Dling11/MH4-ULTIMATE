package org.mh4u.port.overlay

import java.io.File

/** The editor owns a draft; Cancel never changes a saved layout. */
class LayoutEditSession(layout: OverlayLayout) {
    private val original = layout.copyLayout()
    val draft = layout.copyLayout()
    private val history = ArrayDeque<OverlayLayout>()
    val canUndo get() = history.any { !it.sameAs(draft) }
    fun checkpoint() {
        if (history.lastOrNull()?.sameAs(draft) != true) history.addLast(draft.copyLayout())
        while (history.size > 20) history.removeFirst()
    }
    fun undo(): Boolean {
        while (history.isNotEmpty()) {
            val previous = history.removeLast()
            if (!previous.sameAs(draft)) { draft.replaceWith(previous); return true }
        }
        return false
    }
    fun cancel() = original.copyLayout()
    fun commit(destination: File) { draft.saveTo(destination) }
}
