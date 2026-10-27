package org.mh4u.port

import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Window
import androidx.activity.ComponentActivity

/** Public Window input callbacks avoid depending on AndroidX's restricted dispatcher. */
fun ComponentActivity.installInputDispatch(onKey: (KeyEvent) -> Boolean, onMotion: (MotionEvent) -> Boolean) {
    val previous = requireNotNull(window.callback)
    window.callback = object : Window.Callback by previous {
        override fun dispatchKeyEvent(event: KeyEvent): Boolean = onKey(event) || previous.dispatchKeyEvent(event)
        override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean = onMotion(event) || previous.dispatchGenericMotionEvent(event)
    }
}
