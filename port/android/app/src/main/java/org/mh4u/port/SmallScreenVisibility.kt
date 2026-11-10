package org.mh4u.port

/** Keep the chosen two-screen arrangement available when temporarily hiding the touch screen. */
data class ScreenChoice(val layout: String, val swapped: Boolean = false)
data class ScreenVisibilityChange(val display: ScreenChoice, val previous: ScreenChoice)

object SmallScreenVisibility {
    private val default = ScreenChoice("overlay")
    private val supported = setOf("overlay", "centered-top", "centered-bottom", "stacked", "large", "side", "single", "hybrid", "windows")

    fun hidden(display: ScreenChoice): Boolean = display.layout == "single" && !display.swapped

    fun toggle(current: ScreenChoice, previous: ScreenChoice = default): ScreenVisibilityChange {
        if (!hidden(current)) return ScreenVisibilityChange(ScreenChoice("single"), current)
        val restored = previous.takeIf { it.layout in supported && !hidden(it) } ?: default
        return ScreenVisibilityChange(restored, restored)
    }
}
