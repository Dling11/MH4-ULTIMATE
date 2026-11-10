package org.mh4u.port

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.*
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.mh4u.port.saves.SaveGameSession
import org.mh4u.port.overlay.*
import org.mh4u.port.ui.*

/** Android game window, menus and touch controls around the native engine. */
class EmulationActivity : ComponentActivity(), SurfaceHolder.Callback {
    private lateinit var overlay: OverlayView
    private var gameThread: Thread? = null
    private var menuOpen by mutableStateOf(false)
    private var editing by mutableStateOf(false)
    private var selected by mutableStateOf<Control?>(null)
    private var editSession: LayoutEditSession? = null
    private var layoutPortrait = false
    private var secondScreenExpanded by mutableStateOf(false)
    private var undoAvailable by mutableStateOf(false)
    private var holdingForSaveImport = false
    private val playbackState = GamePlaybackState()
    private val saveSession = object : SaveGameSession {
        override suspend fun closeForImport() {
            withContext(Dispatchers.Main.immediate) {
                holdingForSaveImport = true
                Native.stop()
            }
            check(withTimeoutOrNull(30_000) { while (!Native.saveDataIdle()) delay(25); true } == true) {
                "The game did not finish closing. Saved progress has not been replaced. Try again from Home."
            }
        }
        override fun restartAfterImport() {
            SaveRestartActivity.restart(this@EmulationActivity)
        }
        override fun importFailed(message: String) {
            if (holdingForSaveImport) {
                setResult(RESULT_OK, android.content.Intent().putExtra(EXTRA_MESSAGE, message))
                finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            else WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        applyWindowMode()
        Keyboard.host = this
        val game = intent.getStringExtra(EXTRA_GAME) ?: return finish()
        layoutPortrait = isPortrait()
        val root = FrameLayout(this)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            // Let the picture fill the camera area. OverlayView and Compose still
            // receive cutout insets to keep Menu and its settings reachable.
            val safe = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(safe.left, safe.top, safe.right, safe.bottom)
            // Surface and touch controls share this usable area. Pass the remaining
            // insets to Compose so its menu does not apply this padding twice.
            insets.inset(safe.left, safe.top, safe.right, safe.bottom)
        }
        val surface = SurfaceView(this).also { it.holder.addCallback(this) }
        overlay = OverlayView(this, OverlayLayout.load(layoutPortrait)).apply {
            onMenu = { openMenu() }
            onSpeed = {
                val requested = when (speedPercent) { 100 -> 125; 125 -> 150; else -> 100 }
                speedPercent = Native.setSpeed(requested)
                if (speedPercent == 100 && requested != 100)
                    Toast.makeText(this@EmulationActivity, "Normal speed is required for multiplayer.", Toast.LENGTH_SHORT).show()
            }
            onSelect = { c -> this@EmulationActivity.selected = c }
            onSecondScreen = { focusSecondScreen(!secondScreenExpanded) }
            onScreenVisibility = { toggleSmallScreen() }
            onEditCheckpoint = { editSession?.checkpoint() }
            onEditChanged = { undoAvailable = editSession?.canUndo == true }
        }
        applyOverlayPrefs()
        root.addView(surface, FrameLayout.LayoutParams(-1, -1))
        root.addView(overlay, FrameLayout.LayoutParams(-1, -1))
        root.addView(ComposeView(this).apply { setContent { Mh4uTheme { Hud() } } }, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
        ViewCompat.requestApplyInsets(root)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when { editing -> finishEditing(true); menuOpen -> closeMenu(); secondScreenExpanded -> focusSecondScreen(false); else -> openMenu() }
            }
        })
        installInputDispatch(onKey = { event ->
            if (event.keyCode == KeyEvent.KEYCODE_BUTTON_MODE && event.action == KeyEvent.ACTION_DOWN && !menuOpen) { openMenu(); true }
            else ((!menuOpen && !editing) || MH4UInput.learning()) && MH4UInput.onKey(event)
        }, onMotion = { event -> ((!menuOpen && !editing) || MH4UInput.learning()) && MH4UInput.onMotion(event) })
        Prefs.beginGameSession()
        gameThread = Thread({
            val message = Native.run(game)
            runOnUiThread {
                if (!isFinishing && !holdingForSaveImport) { setResult(RESULT_OK, android.content.Intent().putExtra(EXTRA_MESSAGE, message)); finish() }
            }
        }, "mh4u-game").also { it.start() }
    }

    private fun isPortrait() = resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    private fun inRoom() = runCatching { org.json.JSONObject(Native.roomStatus()).optString("phase") == "room" }.getOrDefault(false)

    private fun applyWindowMode() {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun applyOverlayPrefs() {
        if (editing) return
        overlay.smallScreenHidden = Prefs.smallScreenHidden()
        overlay.layout = OverlayLayout.load(layoutPortrait)
        val pad = InputDevice.getDeviceIds().any { id -> InputDevice.getDevice(id)?.let { d -> d.sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD && !d.isVirtual } == true }
        overlay.controlsVisible = Prefs.getBool("Android", "overlay", true) && !(Prefs.getBool("Android", "overlay_autohide", false) && pad)
        overlay.invalidate()
    }

    private fun openMenu() {
        focusSecondScreen(false)
        if (editing && !finishEditing(true, false)) return
        menuOpen = true; overlay.shown = false; MH4UInput.releaseAll()
        syncPlaybackState()
    }
    private fun closeMenu() {
        if (FileWork.busy) return
        menuOpen = false; overlay.shown = true; applyOverlayPrefs(); applyWindowMode(); Prefs.apply()
        ViewCompat.requestApplyInsets(overlay.parent as android.view.View)
        syncPlaybackState()
    }
    private fun startEditing() {
        focusSecondScreen(false)
        MH4UInput.releaseAll()
        editSession = LayoutEditSession(overlay.layout)
        undoAvailable = false
        overlay.layout = editSession!!.draft
        overlay.selected = null; overlay.shown = true; overlay.controlsVisible = true
        overlay.editing = true; menuOpen = false; editing = true
        syncPlaybackState()
    }

    private fun syncPlaybackState() {
        Native.setPaused(playbackState.shouldPause(inRoom()))
    }

    private fun toggleSmallScreen() {
        focusSecondScreen(false)
        Prefs.toggleSmallScreen()
        overlay.smallScreenHidden = Prefs.smallScreenHidden()
    }

    private fun focusSecondScreen(expanded: Boolean) {
        if (secondScreenExpanded == expanded) return
        secondScreenExpanded = expanded
        if (::overlay.isInitialized) overlay.secondScreenExpanded = expanded
        if (Native.isRunning()) Native.setSecondScreenExpanded(expanded)
    }

    /** Back, leaving the app and Save all use the same commit path. */
    private fun finishEditing(save: Boolean, resume: Boolean = true): Boolean {
        val session = editSession ?: return true
        if (save) {
            try { session.commit(OverlayLayout.file(layoutPortrait)) }
            catch (_: Exception) { Toast.makeText(this, "Layout could not be saved. Please try Save again.", Toast.LENGTH_LONG).show(); return false }
        } else overlay.layout = session.cancel()
        overlay.editing = false; overlay.selected = null; editing = false; editSession = null
        overlay.invalidate()
        if (resume) closeMenu() else { applyOverlayPrefs(); syncPlaybackState() }
        return true
    }

    @Composable private fun Hud() {
        var fps by remember { mutableStateOf("") }
        var notice by remember { mutableStateOf("") }
        val counter = remember(menuOpen) { Prefs.getBool("Display", "fps_counter", false) }
        LaunchedEffect(Unit) {
            while (!Native.isRunning()) delay(50)
            // This is a renderer layout option; it does not change the game's CPU or timing.
            Native.setGameOption("touch_overlay", Prefs.getGame("touch_overlay", "top-right 25 85"))
            Prefs.apply()
            delay(2500)
            val message = Native.notice()
            if (!message.contains("code library isn't installed")) notice = message
        }
        LaunchedEffect(counter, menuOpen, editing) {
            fps = ""
            if (counter && !menuOpen && !editing) lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                fps = ""
                while (!Native.isRunning()) delay(50)
                // Discard the interval containing time spent paused, then measure a full live second.
                Native.perf()
                while (true) {
                    delay(1000)
                    val p = Native.perf()
                    if (p.size == 2) fps = "%.0f FPS · %.0f%%".format(p[0], p[1])
                }
            }
        }
        LaunchedEffect(Unit) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (true) {
                    if (Native.isRunning()) {
                        val speed = Native.speedState()
                        if (speed.size == 3) { overlay.speedPercent = speed[0]; overlay.speedBlocked = speed[2] != 0 }
                    }
                    delay(500)
                }
            }
        }
        Box(Modifier.fillMaxSize()) {
            if (counter && fps.isNotEmpty() && !menuOpen && !editing) Text(fps, color = Mh4uColors.Amber, fontSize = 12.sp, modifier = Modifier.align(Alignment.TopStart).safeDrawingPadding().padding(8.dp).background(Color(0xB3101D25), RoundedCornerShape(6.dp)).padding(8.dp))
            if (notice.isNotEmpty() && !menuOpen && !editing) {
                Surface(Modifier.align(Alignment.TopCenter).safeDrawingPadding().padding(12.dp), color = Mh4uColors.PopupBg) {
                    Column(Modifier.widthIn(max = 420.dp).padding(12.dp)) { Text(notice, fontSize = 12.sp); TextButton(onClick = { notice = "" }) { Text("Dismiss") } }
                }
            }
            if (editing) LayoutEditor(overlay.layout, selected, { overlay.selected = it },
                { undoAvailable = editSession?.canUndo == true; overlay.invalidate() },
                { finishEditing(true) }, { finishEditing(false) }, layoutPortrait,
                checkpoint = { editSession?.checkpoint() }, canUndo = undoAvailable,
                onUndo = { overlay.releaseEverything(); editSession?.undo(); overlay.selected = null; undoAvailable = editSession?.canUndo == true; overlay.invalidate() })
            if (menuOpen) GameMenu()
        }
    }

    @Composable private fun GameMenu() {
        var quit by remember { mutableStateOf(false) }
        GameSettingsPanel(enabled = !FileWork.busy, onResume = { closeMenu() },
            onSecondScreen = { closeMenu(); focusSecondScreen(true) }, onExit = { quit = true }) {
            SettingsHub(onEditOverlay = { startEditing() }, onOverlayChanged = { applyOverlayPrefs() }, saveSession = saveSession, showRootTitle = false)
        }
        if (quit) AlertDialog(onDismissRequest = { quit = false }, title = { Text("Exit this game?") }, text = { Text("Make sure you saved your progress inside Monster Hunter.") }, confirmButton = { TextButton(onClick = { quit = false; Native.stop() }) { Text("Exit game") } }, dismissButton = { TextButton(onClick = { quit = false }) { Text("Keep playing") } })
    }

    override fun surfaceCreated(holder: SurfaceHolder) { Native.surfaceChanged(holder.surface) }
    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) { Native.surfaceChanged(holder.surface) }
    override fun surfaceDestroyed(holder: SurfaceHolder) { Native.surfaceDestroyed() }
    override fun onWindowFocusChanged(hasFocus: Boolean) { super.onWindowFocusChanged(hasFocus); if (hasFocus) applyWindowMode() }
    override fun onConfigurationChanged(newConfig: Configuration) {
        if (::overlay.isInitialized && editing) finishEditing(true, false)
        super.onConfigurationChanged(newConfig)
        layoutPortrait = isPortrait()
        if (::overlay.isInitialized) applyOverlayPrefs()
        Prefs.apply(force = true)
    }
    override fun onPause() {
        playbackState.onPause()
        if (::overlay.isInitialized) {
            focusSecondScreen(false)
            Native.setSpeed(100)
            if (editing) finishEditing(true, false)
            MH4UInput.releaseAll(); overlay.releaseEverything()
        }
        syncPlaybackState()
        super.onPause()
    }
    override fun onResume() { super.onResume(); playbackState.onResume(); syncPlaybackState() }
    override fun onDestroy() {
        if (editing) finishEditing(true, false)
        if (Keyboard.host === this) Keyboard.host = null
        if (isFinishing) { Native.stop(); gameThread?.join(5000) }
        super.onDestroy()
    }
    companion object { const val EXTRA_GAME = "game"; const val EXTRA_MESSAGE = "message" }
}
