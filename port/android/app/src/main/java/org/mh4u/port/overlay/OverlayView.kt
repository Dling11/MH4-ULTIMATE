package org.mh4u.port.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.WindowInsets
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.Insets
import androidx.core.view.WindowInsetsCompat
import org.mh4u.port.MH4UInput
import org.mh4u.port.Native
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * The controls drawn over the game, and the touches that reach them. Every finger is one of:
 * a button or combo, the circle pad or the C-stick, or the 3DS touch screen (a finger inside
 * the bottom screen's rectangle that is not on a control is the stylus). In edit mode a finger
 * drags a control and two fingers resize the selected one.
 */
class OverlayView(context: Context, var layout: OverlayLayout) : View(context) {

    var editing = false
        set(v) { field = v; if (v) releaseEverything(); invalidate() }
    var selected: Control? = null
        set(v) { if (field === v) return; field = v; onSelect?.invoke(v); invalidate() }
    var onSelect: ((Control?) -> Unit)? = null
    var onMenu: (() -> Unit)? = null
    var onSpeed: (() -> Unit)? = null
    var onSecondScreen: (() -> Unit)? = null
    var onScreenVisibility: (() -> Unit)? = null
    var onEditCheckpoint: (() -> Unit)? = null
    var onEditChanged: (() -> Unit)? = null
    var secondScreenExpanded = false
        set(value) { field = value; releaseEverything(); invalidate() }
    var smallScreenHidden = false
        set(value) { if (field != value) { field = value; releaseEverything(); invalidate() } }
    var speedPercent = 100
        set(value) { if (field != value) { field = value; invalidate() } }
    var speedBlocked = false
        set(value) { if (field != value) { field = value; invalidate() } }
    /** The controls on screen; off, the view still passes touches to the 3DS touch screen. */
    var controlsVisible = true
        set(v) { field = v; if (!v) releaseEverything(); invalidate() }
    /** Hidden while the game's own menu or a dialog covers it. */
    var shown = true
        set(v) { field = v; if (!v) releaseEverything(); invalidate() }

    private sealed interface Target {
        data class Ctl(val c: Control) : Target
        data object Screen : Target
        data class Drag(val c: Control, val dx: Float, val dy: Float) : Target
    }

    private val targets = HashMap<Int, Target>()
    private val sticks = HashMap<Control, FloatArray>()      // circle pad, c-stick: x, y
    private val held = HashMap<Control, Int>()               // pointer count on a button
    private val latched = HashSet<Control>()
    private val tapFeedback = TapFeedback()
    private var overlayButtons = 0
    private var screens = FloatArray(10)
    private var safeInsets = Insets.NONE

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        safeInsets = WindowInsetsCompat.toWindowInsetsCompat(insets, this)
            .getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
        invalidate()
        return super.onApplyWindowInsets(insets)
    }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val buttonRect = RectF()
    private val accent: Int get() = when (layout.palette) { "blue" -> 0xFF81B9FF.toInt(); "gold" -> 0xFFF3C477.toInt(); else -> 0xFF65D9C0.toInt() }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3f }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; color = AMBER_TEXT }

    private val pinch = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(d: ScaleGestureDetector): Boolean {
            selected?.let { it.size = (it.size * d.scaleFactor).coerceIn(0.04f, 0.5f); onEditChanged?.invoke(); invalidate() }
            return true
        }
    })

    // ── geometry ──

    // Keep Menu reachable when Android bars are shown, without rewriting saved positions.
    private fun menuCoordinate(value: Float, extent: Int, before: Int, after: Int, radius: Float): Float {
        val margin = max(radius, 24f * resources.displayMetrics.density) + 4f * resources.displayMetrics.density
        val low = before + margin
        val high = extent - after - margin
        return if (high >= low) value.coerceIn(low, high) else extent / 2f
    }
    private fun utility(c: Control) = c.kind == Control.Kind.MENU || c.kind == Control.Kind.SPEED || c.kind == Control.Kind.SCREEN || c.kind == Control.Kind.SCREEN_VISIBILITY
    private fun cx(c: Control) = if (utility(c))
        menuCoordinate(c.x * width, width, safeInsets.left, safeInsets.right, radius(c)) else c.x * width
    private fun cy(c: Control) = if (utility(c))
        menuCoordinate(c.y * height, height, safeInsets.top, safeInsets.bottom, radius(c)) else c.y * height
    private fun radius(c: Control) = c.size * height / 2f

    private fun hit(c: Control, x: Float, y: Float): Boolean {
        val r = if (utility(c)) max(radius(c), 24f * resources.displayMetrics.density)
            else radius(c) * if (c.kind == Control.Kind.CIRCLE_PAD || c.kind == Control.Kind.C_STICK) 1.15f else 1.0f
        return hypot(x - cx(c), y - cy(c)) <= r + 8f
    }

    private fun onScreen(x: Float, y: Float): Boolean {
        if (smallScreenHidden && !secondScreenExpanded) return false
        val l = screens   // [4..7] the bottom screen
        return l[6] > l[4] && x >= l[4] && x <= l[6] && y >= l[5] && y <= l[7]
    }

    // ── touches ──

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (!shown) return false
        if (editing) return editTouch(e)
        screens = Native.layout()
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> down(e, e.actionIndex)
            MotionEvent.ACTION_MOVE -> for (i in 0 until e.pointerCount) move(e, i)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> up(e, e.actionIndex)
            MotionEvent.ACTION_CANCEL -> releaseEverything()
        }
        publish()
        invalidate()
        return true
    }

    private fun down(e: MotionEvent, i: Int) {
        val id = e.getPointerId(i)
        val x = e.getX(i); val y = e.getY(i)
        // Menu takes priority over overlapping buttons and remains available when controls hide.
        val c = layout.controls.lastOrNull { it.kind == Control.Kind.MENU && hit(it, x, y) }
            ?: if (secondScreenExpanded) layout.controls.lastOrNull { it.kind == Control.Kind.SCREEN && hit(it, x, y) }
            else if (!controlsVisible) null else layout.controls.lastOrNull { it.visible && hit(it, x, y) }
        if (c != null) {
            targets[id] = Target.Ctl(c)
            when (c.kind) {
                Control.Kind.MENU -> onMenu?.invoke()
                Control.Kind.SPEED -> { held[c] = (held[c] ?: 0) + 1; buzz(); onSpeed?.invoke() }
                Control.Kind.SCREEN -> { buzz(); onSecondScreen?.invoke(); tapFeedback.release(c.id, SystemClock.uptimeMillis()) }
                Control.Kind.SCREEN_VISIBILITY -> { buzz(); onScreenVisibility?.invoke(); tapFeedback.release(c.id, SystemClock.uptimeMillis()) }
                Control.Kind.CIRCLE_PAD, Control.Kind.C_STICK -> { sticks[c] = floatArrayOf(0f, 0f); moveStick(c, x, y) }
                Control.Kind.DPAD -> held[c] = dpadMask(c, x, y)
                Control.Kind.COMBO -> {
                    if (c.latch) { if (!latched.remove(c)) latched.add(c) } else held[c] = (held[c] ?: 0) + 1
                    buzz()
                }
                else -> { held[c] = (held[c] ?: 0) + 1; buzz() }
            }
        } else if (onScreen(x, y)) {
            targets[id] = Target.Screen
            Native.touch(x.toInt(), y.toInt(), true)
        }
    }

    private fun move(e: MotionEvent, i: Int) {
        val x = e.getX(i); val y = e.getY(i)
        when (val t = targets[e.getPointerId(i)]) {
            is Target.Ctl -> when (t.c.kind) {
                Control.Kind.CIRCLE_PAD, Control.Kind.C_STICK -> moveStick(t.c, x, y)
                Control.Kind.DPAD -> held[t.c] = dpadMask(t.c, x, y)
                else -> {}
            }
            Target.Screen -> Native.touchMove(x.toInt(), y.toInt())
            else -> {}
        }
    }

    private fun up(e: MotionEvent, i: Int) {
        when (val t = targets.remove(e.getPointerId(i))) {
            is Target.Ctl -> {
                if (t.c.kind == Control.Kind.BUTTON || t.c.kind == Control.Kind.COMBO || t.c.kind == Control.Kind.SPEED) {
                    tapFeedback.release(t.c.id, SystemClock.uptimeMillis())
                }
                when (t.c.kind) {
                Control.Kind.CIRCLE_PAD, Control.Kind.C_STICK -> sticks.remove(t.c)
                Control.Kind.DPAD -> held.remove(t.c)
                Control.Kind.COMBO -> if (!t.c.latch) dec(t.c)
                else -> dec(t.c)
                }
            }
            Target.Screen -> Native.touch(0, 0, false)
            else -> {}
        }
    }

    private fun dec(c: Control) {
        val n = (held[c] ?: 0) - 1
        if (n <= 0) held.remove(c) else held[c] = n
    }

    private fun moveStick(c: Control, x: Float, y: Float) {
        val r = radius(c)
        var dx = (x - cx(c)) / r
        var dy = (y - cy(c)) / r
        val len = hypot(dx, dy)
        if (len > 1f) { dx /= len; dy /= len }
        sticks[c] = floatArrayOf(dx, -dy)   // up positive
    }

    /** The d-pad's directions under a finger: 8-way with a dead centre. */
    private fun dpadMask(c: Control, x: Float, y: Float): Int {
        val r = radius(c)
        val dx = (x - cx(c)) / r; val dy = (y - cy(c)) / r
        var m = 0
        if (hypot(dx, dy) < 0.25f) return 0
        if (dx > 0.38f) m = m or MH4UInput.RIGHT
        if (dx < -0.38f) m = m or MH4UInput.LEFT
        if (dy > 0.38f) m = m or MH4UInput.DOWN
        if (dy < -0.38f) m = m or MH4UInput.UP
        return m
    }

    private fun buzz() {
        if (layout.haptics) performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }

    /** What the controls hold, handed to the game. */
    private fun publish() {
        var mask = 0
        for ((c, n) in held) {
            mask = mask or when (c.kind) { Control.Kind.DPAD -> n; else -> c.mask }
        }
        for (c in latched) mask = mask or c.mask
        overlayButtons = mask
        val circle = layout.controls.firstOrNull { it.kind == Control.Kind.CIRCLE_PAD }?.let { sticks[it] } ?: ZERO
        val cstick = layout.controls.firstOrNull { it.kind == Control.Kind.C_STICK }?.let { sticks[it] } ?: ZERO
        MH4UInput.setOverlay(mask, circle[0], circle[1], cstick[0], cstick[1])
    }

    fun releaseEverything() {
        if (targets.values.any { it === Target.Screen }) Native.touch(0, 0, false)
        targets.clear(); sticks.clear(); held.clear(); latched.clear()
        tapFeedback.clear(); overlayButtons = 0
        MH4UInput.setOverlay(0, 0f, 0f, 0f, 0f)
        invalidate()
    }

    // ── editing ──

    private fun editTouch(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_DOWN && layout.controls.any { hit(it, e.x, e.y) }) onEditCheckpoint?.invoke()
        pinch.onTouchEvent(e)
        if (pinch.isInProgress) return true
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val c = layout.controls.lastOrNull { hit(it, e.x, e.y) }
                selected = c
                if (c != null) targets[e.getPointerId(0)] = Target.Drag(c, cx(c) - e.x, cy(c) - e.y)
            }
            MotionEvent.ACTION_MOVE -> {
                val t = targets[e.getPointerId(0)]
                if (t is Target.Drag && e.pointerCount == 1) {
                    t.c.x = ((e.x + t.dx) / width).coerceIn(0.02f, 0.98f)
                    t.c.y = ((e.y + t.dy) / height).coerceIn(0.02f, 0.98f)
                    onEditChanged?.invoke()
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> targets.clear()
        }
        return true
    }

    // ── drawing ──

    override fun onDraw(canvas: Canvas) {
        if (!shown) return
        val a = (layout.opacity * 255).toInt().coerceIn(20, 255)
        val now = SystemClock.uptimeMillis()
        for (c in layout.controls) {
            if (secondScreenExpanded && !editing && c.kind != Control.Kind.MENU && c.kind != Control.Kind.SCREEN) continue
            if (secondScreenExpanded && c.kind == Control.Kind.SCREEN) { /* Keep the return control available. */ }
            else if (c.kind != Control.Kind.MENU && ((!c.visible || !controlsVisible) && !editing)) continue
            val r = radius(c)
            val pressed = !editing && ((held[c] ?: 0) > 0 || c in latched || sticks.containsKey(c) ||
                (c.kind == Control.Kind.BUTTON && c.mask != 0 && (overlayButtons and c.mask) == c.mask))
            val feedback = if (editing) 0f else tapFeedback.strength(c.id, pressed, now)
            fill.color = ColorUtils.blendARGB(FRAME, accent, feedback)
            fill.alpha = ((a * .55f) + (max(200, min(255, a + 80)) - a * .55f) * feedback).toInt()
            stroke.color = if (c === selected && editing) WHITE else ColorUtils.blendARGB(accent, WHITE, feedback)
            stroke.alpha = (a + (255 - a) * feedback).toInt()
            stroke.strokeWidth = if (c === selected && editing) 6f else 3f + 2f * feedback
            when (c.kind) {
                Control.Kind.CIRCLE_PAD, Control.Kind.C_STICK -> {
                    // Walking and camera pads share a faint theme tint, including while held.
                    fill.color = ColorUtils.blendARGB(FRAME, accent, feedback)
                    fill.alpha = (a * (.08f + .06f * feedback)).toInt()
                    canvas.drawCircle(cx(c), cy(c), r, fill); canvas.drawCircle(cx(c), cy(c), r, stroke)
                    val s = sticks[c]
                    val kx = cx(c) + (s?.get(0) ?: 0f) * r * 0.6f
                    val ky = cy(c) - (s?.get(1) ?: 0f) * r * 0.6f
                    fill.color = accent
                    fill.alpha = (a * (.22f + .08f * feedback)).toInt()
                    canvas.drawCircle(kx, ky, r * 0.38f, fill)
                    canvas.drawCircle(kx, ky, r * 0.38f, stroke)
                    label(canvas, if (c.kind == Control.Kind.C_STICK) "C" else "", cx(c), cy(c) - r - 6f, r * 0.25f, a)
                }
                Control.Kind.DPAD -> drawDpad(canvas, c, r, a)
                Control.Kind.MENU, Control.Kind.BUTTON, Control.Kind.COMBO, Control.Kind.SPEED, Control.Kind.SCREEN, Control.Kind.SCREEN_VISIBILITY -> {
                    val saved = canvas.save()
                    val scale = 1f - .045f * feedback
                    canvas.scale(scale, scale, cx(c), cy(c))
                    val longButton = c.kind == Control.Kind.BUTTON && c.label.length > 2
                    if (longButton) {
                        buttonRect.set(cx(c) - r * 1.3f, cy(c) - r * 0.6f, cx(c) + r * 1.3f, cy(c) + r * 0.6f)
                        canvas.drawRoundRect(buttonRect, r, r, fill); canvas.drawRoundRect(buttonRect, r, r, stroke)
                    } else {
                        canvas.drawCircle(cx(c), cy(c), r, fill); canvas.drawCircle(cx(c), cy(c), r, stroke)
                    }
                    val caption = when (c.kind) {
                        Control.Kind.SPEED -> when (speedPercent) { 125 -> "1.25×"; 150 -> "1.5×"; else -> "1×" }
                        Control.Kind.SCREEN -> if (secondScreenExpanded) "Back" else "2nd"
                        Control.Kind.SCREEN_VISIBILITY -> if (smallScreenHidden) "Show" else "Hide"
                        else -> c.label
                    }
                    label(canvas, caption, cx(c), cy(c), r * if (caption.length > 2) 0.55f else 0.8f,
                        (a + (255 - a) * feedback).toInt(),
                        ColorUtils.blendARGB(if (c.kind == Control.Kind.SPEED && speedBlocked) 0xFF9BAEB7.toInt() else WHITE, PRESSED_TEXT, feedback), r * if (longButton) 2.2f else 1.7f)
                    if (c in latched) {
                        fill.color = WHITE; fill.alpha = 255
                        canvas.drawCircle(cx(c) + r * .55f, cy(c) - r * .55f, max(3f, r * .09f), fill)
                    }
                    canvas.restoreToCount(saved)
                }
            }
        }
        if (tapFeedback.pending(now)) postInvalidateOnAnimation()
    }

    private fun drawDpad(canvas: Canvas, c: Control, r: Float, a: Int) {
        val arm = r * 0.38f
        val x = cx(c); val y = cy(c)
        val m = held[c] ?: 0
        val arms = listOf(
            RectF(x - arm, y - r, x + arm, y - arm * 0.2f) to MH4UInput.UP,
            RectF(x - arm, y + arm * 0.2f, x + arm, y + r) to MH4UInput.DOWN,
            RectF(x - r, y - arm, x - arm * 0.2f, y + arm) to MH4UInput.LEFT,
            RectF(x + arm * 0.2f, y - arm, x + r, y + arm) to MH4UInput.RIGHT,
        )
        for ((rect, bit) in arms) {
            fill.color = if (m and bit != 0) accent else FRAME
            fill.alpha = if (m and bit != 0) min(255, a + 60) else (a * 0.55f).toInt()
            canvas.drawRoundRect(rect, 10f, 10f, fill); canvas.drawRoundRect(rect, 10f, 10f, stroke)
        }
    }

    private fun label(canvas: Canvas, s: String, x: Float, y: Float, size: Float, a: Int,
                      color: Int = WHITE, maxWidth: Float = Float.POSITIVE_INFINITY) {
        if (s.isEmpty()) return
        text.textSize = max(14f, size); text.alpha = a; text.color = color
        val measured = text.measureText(s)
        if (measured > maxWidth) text.textSize *= maxWidth / measured
        canvas.drawText(s, x, y - (text.ascent() + text.descent()) / 2f, text)
    }

    companion object {
        private const val WHITE = 0xFFFFFFFF.toInt()
        private const val PRESSED_TEXT = 0xFF0C151C.toInt()
        private const val AMBER_TEXT = 0xFFEDEBE3.toInt()
        private const val FRAME = 0xFF29292B.toInt()
        private val ZERO = floatArrayOf(0f, 0f)
    }
}
