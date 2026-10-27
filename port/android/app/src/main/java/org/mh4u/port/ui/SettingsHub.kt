package org.mh4u.port.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.mh4u.port.*
import org.mh4u.port.overlay.OverlayLayout
import java.io.File

@Composable fun ActionTile(title: String, description: String, icon: ImageVector, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(16.dp), color = Mh4uColors.PopupBg, contentColor = Mh4uColors.Text, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Mh4uColors.Amber, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) { Text(title, color = Mh4uColors.Text, fontWeight = FontWeight.SemiBold); Spacer(Modifier.height(3.dp)); Hint(description) }
            Icon(Icons.Rounded.ChevronRight, null, tint = Mh4uColors.TextDisabled)
        }
    }
}

@Composable fun SettingsHub(
    initialSection: String = "", onEditOverlay: (() -> Unit)? = null, onOverlayChanged: () -> Unit = {},
    onPickGame: (() -> Unit)? = null, onPickLibrary: (() -> Unit)? = null, onPickUpdate: (() -> Unit)? = null,
) {
    var section by rememberSaveable(initialSection) { mutableStateOf(initialSection) }
    val titles = mapOf("display" to "Display & performance", "touch" to "Touch controls", "layouts" to "Saved control layouts", "saves" to "Saves", "reports" to "Help & reports", "controller" to "Controller & keyboard", "audio" to "Audio", "online" to "Multiplayer", "files" to "Game & storage", "advanced" to "Advanced", "graphics" to "Advanced graphics", "game" to "Recompiled game options")
    fun parentSection() = when (section) { "graphics", "game" -> "advanced"; "layouts" -> "touch"; else -> "" }
    BackHandler(section.isNotEmpty()) { if (!FileWork.busy) section = parentSection() }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (section.isNotEmpty()) IconButton(enabled = !FileWork.busy, onClick = { section = parentSection() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back to settings") }
            Text(titles[section] ?: "Settings", color = Mh4uColors.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(8.dp))
        }
        Box(Modifier.weight(1f)) {
            when (section) {
                "display" -> SimpleDisplayPage(inGame = onEditOverlay != null)
                "touch" -> TouchSettingsPage(onEditOverlay, onOverlayChanged) { section = "layouts" }
                "layouts" -> LayoutProfilesPage(onOverlayChanged)
                "saves" -> SavesPage(inGame = onEditOverlay != null)
                "reports" -> ReportsPage()
                "controller" -> ControlsPage(includeTouch = false)
                "audio" -> AudioPage()
                "online" -> MultiplayerPage()
                "graphics" -> DisplayPage()
                "game" -> if (hasCodeLibrary()) GamePage() else SettingsColumn { Hint("These options need a matching recompiled code library. Normal emulation is available without one.") }
                "files" -> SettingsColumn {
                    SectionTitle("Your game")
                    Panel {
                        Text(Prefs.gameFile()?.name ?: "No game added")
                        Spacer(Modifier.height(12.dp))
                        if (onPickGame != null) AmberButton("Choose game") { onPickGame() } else Hint("Choose another game from Home after closing this session.")
                    }
                    SectionTitle("App storage")
                    Panel { Text("Monster Hunter 4 - Rowell", fontWeight = FontWeight.SemiBold); Hint("This app keeps its own progress, controls and settings."); Spacer(Modifier.height(10.dp)); Hint(Storage.root.absolutePath) }
                    Panel { Hint("Save your progress inside Monster Hunter before closing the game. Moving buttons does not save game progress.") }
                }
                "advanced" -> SettingsColumn {
                    Panel { Text("Optional recompiled mode", fontWeight = FontWeight.SemiBold); Hint("You can play normally without these files. Recompiled mode needs a matching USA v1.3.0 update and code library.")
                        if (onPickLibrary != null) { Spacer(Modifier.height(12.dp)); QuietButton("Import code library (.so)") { onPickLibrary() } }
                        if (onPickUpdate != null) QuietButton("Install game update (.cia)") { onPickUpdate() }
                        if (hasCodeLibrary()) Hint("A code library is present. Runtime compatibility is checked when the game starts.")
                    }
                    ActionTile("Advanced graphics", "Shader behaviour, filters and additional layouts", Icons.Rounded.Tune) { section = "graphics" }
                    if (hasCodeLibrary()) ActionTile("Recompiled game options", "Frame rate, widescreen and game-specific features", Icons.Rounded.Code) { section = "game" }
                }
                else -> SettingsColumn {
                    ActionTile("Display & performance", "Fullscreen, layout and picture quality", Icons.Rounded.Fullscreen) { section = "display" }
                    ActionTile("Touch controls", "Layout, colours, opacity and vibration", Icons.Rounded.TouchApp) { section = "touch" }
                    ActionTile("Saves", "Back up, export or restore saved progress", Icons.Rounded.Save) { section = "saves" }
                    ActionTile("Controller & keyboard", "Bluetooth / USB devices and button bindings", Icons.Rounded.SportsEsports) { section = "controller" }
                    ActionTile("Audio", "Game volume", Icons.AutoMirrored.Rounded.VolumeUp) { section = "audio" }
                    ActionTile("Multiplayer", "Join or host a network room", Icons.Rounded.Wifi) { section = "online" }
                    ActionTile("Game & storage", "Selected game and separate app folder", Icons.Rounded.FolderOpen) { section = "files" }
                    ActionTile("Advanced", "Optional setup and extra settings", Icons.Rounded.Build) { section = "advanced" }
                    ActionTile("Help & reports", "Share an error report", Icons.Rounded.BugReport) { section = "reports" }
                }
            }
        }
    }
}

private fun hasCodeLibrary() = File(Storage.recomp, "0004000000126300.so").isFile

@Composable fun SettingsColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
}

@Composable private fun SimpleDisplayPage(inGame: Boolean) = SettingsColumn {
    var screens by remember { mutableStateOf(Prefs.getApp("Display", "screens", "overlay")) }
    var resolution by remember { mutableStateOf(Prefs.getApp("Display", "resolution", "2")) }
    Panel {
        Hint("Landscape fullscreen. Swipe from an edge to show Android bars.")
        ChoiceRow("Screen layout", null, listOf("Game + small touch screen" to "overlay", "Centered · small screen top" to "centered-top", "Centered · small screen bottom" to "centered-bottom", "Top & bottom screens" to "stacked", "Large + small" to "large", "Side by side" to "side", "Main screen only" to "single"), screens) { screens = it; Prefs.setApp("Display", "screens", it); Prefs.apply() }
        Hint(if (inGame) "Changes save automatically. Tap Resume above to apply. No game restart needed." else "Changes save automatically and apply when you start the game.")
        if (screens.startsWith("centered-")) Hint("The game stays centered with equal space on both sides. The small screen sits in the right margin.")
    }
    if (inGame) {
        SectionTitle("Solo game speed")
        Panel {
            var percent by remember { mutableIntStateOf(100) }
            var blocked by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                while (true) {
                    val speed = Native.speedState()
                    if (speed.size == 3) { percent = speed[0]; blocked = speed[2] != 0 }
                    kotlinx.coroutines.delay(500)
                }
            }
            if (blocked) Text("1× · Multiplayer")
            else ChoiceRow("Speed", null, listOf("1× · Normal" to "100", "1.25×" to "125", "1.5×" to "150"), percent.toString()) {
                percent = Native.setSpeed(it.toInt())
            }
            Hint("Starts at 1×. Faster speeds need spare performance; return to 1× if play becomes uneven.")
            Hint("The movable speed button cycles these choices during play. Multiplayer always uses 1×.")
        }
    }
    SectionTitle("Picture quality")
    Panel {
        ChoiceRow("Resolution", "1× is lighter. Higher values make the picture sharper.", listOf("1× · Performance" to "1", "2× · Balanced" to "2", "3× · Quality" to "3", "4× · High quality" to "4"), resolution) { resolution = it; Prefs.setApp("Display", "resolution", it); Prefs.apply() }
        Hint("Balanced keeps the same resolution as the original Android app.")
        var counter by remember { mutableStateOf(Prefs.getBool("Display", "fps_counter", false)) }
        ToggleRow("Show FPS and game speed", null, counter) { counter = it; Prefs.setBool("Display", "fps_counter", it) }
    }
    if (screens == "overlay") {
        SectionTitle("Small touch screen")
        Panel {
            var corner by remember { mutableStateOf(Prefs.getGame("touch_overlay", "top-right 25 85").substringBefore(' ')) }
            var size by remember { mutableFloatStateOf(Prefs.getGame("touch_overlay", "top-right 25 85").split(' ').getOrNull(1)?.toFloatOrNull() ?: 25f) }
            fun applyTouch() { Prefs.setGame("touch_overlay", "$corner ${size.toInt()} 85"); Prefs.apply() }
            ChoiceRow("Position", null, listOf("Top right" to "top-right", "Top left" to "top-left", "Bottom right" to "bottom-right", "Bottom left" to "bottom-left"), corner) { corner = it; applyTouch() }
            SliderRow("Size", null, size, 15f..50f, " %", steps = 6) { size = it; applyTouch() }
        }
    }
}

@Composable private fun TouchSettingsPage(onEditOverlay: (() -> Unit)?, changed: () -> Unit, profiles: () -> Unit) = SettingsColumn {
    Panel {
        Text("Make the layout yours", fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Hint("Move and resize buttons, or add a combo for several buttons at once.")
        Spacer(Modifier.height(12.dp))
        if (onEditOverlay != null) AmberButton("Edit button layout", Modifier.fillMaxWidth()) { onEditOverlay() }
        else Hint("Open the game, then Menu → Touch controls → Edit button layout.")
    }
    ActionTile("Saved control layouts", "Save named button arrangements and switch between them", Icons.Rounded.FolderOpen, profiles)
    Panel {
        var show by remember { mutableStateOf(Prefs.getBool("Android", "overlay", true)) }
        ToggleRow("Show touch buttons", null, show) { show = it; Prefs.setBool("Android", "overlay", it); changed() }
        var hide by remember { mutableStateOf(Prefs.getBool("Android", "overlay_autohide", false)) }
        ToggleRow("Hide with a controller", "Touch buttons return when the controller disconnects.", hide) { hide = it; Prefs.setBool("Android", "overlay_autohide", it); changed() }
    }
    val initial = remember { OverlayLayout.load(false) }
    var opacity by remember { mutableFloatStateOf(initial.opacity * 100) }
    var vibration by remember { mutableStateOf(initial.haptics) }
    var palette by remember { mutableStateOf(initial.palette) }
    fun saveAppearance() {
        // Read current positions, rather than writing a stale settings-page snapshot.
        val fresh = OverlayLayout.load(false)
        fresh.opacity = opacity / 100; fresh.haptics = vibration; fresh.palette = palette; fresh.save(false)
        changed()
    }
    Panel {
        ChoiceRow("Button colour", null, listOf("Jade" to "mint", "Blue" to "blue", "Gold" to "gold"), palette) { palette = it; saveAppearance() }
        SliderRow("Button opacity", null, opacity, 15f..100f, " %") { opacity = it; saveAppearance() }
        ToggleRow("Vibrate when pressed", null, vibration) { vibration = it; saveAppearance() }
    }
}

@Composable fun AboutPage() {
    val context = LocalContext.current
    var license by remember { mutableStateOf(false) }
    SettingsColumn {
        Text("Monster Hunter 4 - Rowell", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Hint("Version 1.0.6 · Android")
        Panel { Text("A simpler home for your hunts.", fontWeight = FontWeight.SemiBold); Hint("Custom Android interface and controls by Rowell.") }
        SectionTitle("Built with the community")
        Panel { Text("Android base: klark231 / MH4U-Android"); Spacer(Modifier.height(8.dp)); Text("Original port: CompaLarios18 / MH4UltimateDC"); Spacer(Modifier.height(8.dp)); Text("Emulator core: Azahar / AzaharPlus"); Spacer(Modifier.height(8.dp)); Hint("Monster Hunter 4 Ultimate and its game content belong to Capcom. Game files are not included.") }
        QuietButton("Open-source licence") { license = true }
    }
    if (license) AlertDialog(onDismissRequest = { license = false }, title = { Text("GNU GPL version 3") }, text = { Text(remember { context.assets.open("LICENSE.txt").bufferedReader().use { it.readText() } }, modifier = Modifier.heightIn(max = 350.dp).verticalScroll(rememberScrollState()), fontSize = 12.sp) }, confirmButton = { TextButton(onClick = { license = false }) { Text("Close") } })
}
