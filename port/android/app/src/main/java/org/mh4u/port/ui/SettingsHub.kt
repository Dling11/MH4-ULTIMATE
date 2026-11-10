package org.mh4u.port.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.mh4u.port.*
import org.mh4u.port.overlay.OverlayLayout
import org.mh4u.port.saves.SaveGameSession
import java.io.File

@Composable fun ActionTile(title: String, description: String, icon: ImageVector, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(16.dp), color = Mh4uColors.PopupBg, contentColor = Mh4uColors.Text, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Mh4uColors.Amber, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) { Text(title, color = Mh4uColors.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold); Spacer(Modifier.height(3.dp)); Hint(description) }
            Icon(Icons.Rounded.ChevronRight, null, tint = Mh4uColors.TextDisabled)
        }
    }
}

@Composable fun SettingsHub(
    initialSection: String = "", onEditOverlay: (() -> Unit)? = null, onOverlayChanged: () -> Unit = {},
    onPickGame: (() -> Unit)? = null, onPickLibrary: (() -> Unit)? = null, onPickUpdate: (() -> Unit)? = null,
    saveSession: SaveGameSession? = null,
    showRootTitle: Boolean = true,
) {
    var section by rememberSaveable(initialSection) { mutableStateOf(initialSection) }
    val titles = mapOf("display" to "Display & performance", "touch" to "Touch controls", "layouts" to "Saved control layouts", "saves" to "Saves", "reports" to "Help & reports", "controller" to "Controller & keyboard", "audio" to "Audio", "online" to "Multiplayer", "files" to "Game & storage", "advanced" to "Advanced", "graphics" to "Advanced graphics", "game" to "Game settings")
    fun parentSection() = when (section) { "graphics" -> "advanced"; "layouts" -> "touch"; else -> "" }
    BackHandler(section.isNotEmpty()) { if (!FileWork.busy) section = parentSection() }
    Column(Modifier.fillMaxSize()) {
        if (section.isNotEmpty() || showRootTitle) Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (section.isNotEmpty()) IconButton(enabled = !FileWork.busy, onClick = { section = parentSection() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back to settings") }
            Text(titles[section] ?: "Settings", color = Mh4uColors.Text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.weight(1f)) {
            when (section) {
                "display" -> SimpleDisplayPage(inGame = onEditOverlay != null)
                "touch" -> TouchSettingsPage(onEditOverlay, onOverlayChanged) { section = "layouts" }
                "layouts" -> LayoutProfilesPage(onOverlayChanged)
                "saves" -> SavesPage(inGame = onEditOverlay != null, session = saveSession)
                "reports" -> ReportsPage()
                "controller" -> ControlsPage(includeTouch = false)
                "audio" -> AudioPage()
                "online" -> MultiplayerPage()
                "graphics" -> DisplayPage()
                "game" -> GamePage(recompiledAvailable = recompiledOptionsAvailable(), inGame = onEditOverlay != null)
                "files" -> SettingsColumn {
                    SectionTitle("Your game")
                    Panel {
                        Text(Prefs.gameFile()?.name ?: "No game added")
                        Hint("Add a .3ds, .cci or .cxi game image, or a single ZIP / 7Z archive. Split and RAR archives need manual extraction.")
                        Spacer(Modifier.height(12.dp))
                        if (onPickGame != null) AmberButton("Choose game") { onPickGame() } else Hint("Choose another game from Home after closing this session.")
                    }
                    SectionTitle("App storage")
                    Panel { Text("Monster Hunter 4", fontWeight = FontWeight.SemiBold); Hint("This app keeps its own progress, controls and settings."); Spacer(Modifier.height(10.dp)); Hint(Storage.root.absolutePath) }
                    Panel { Hint("Save your progress inside Monster Hunter before closing the game. Moving buttons does not save game progress.") }
                }
                "advanced" -> SettingsColumn {
                    Panel { Text("Optional recompiled mode", fontWeight = FontWeight.SemiBold); Hint("You can play normally without these files. Recompiled mode needs a matching USA v1.3.0 update and code library.")
                        if (onPickLibrary != null) { Spacer(Modifier.height(12.dp)); QuietButton("Import code library (.so)") { onPickLibrary() } }
                        if (onPickUpdate != null) QuietButton("Install game update (.cia)") { onPickUpdate() }
                        if (hasCodeLibrary()) Hint("A code library is present. Runtime compatibility is checked when the game starts.")
                    }
                    ActionTile("Advanced graphics", "Shader behaviour, filters and additional layouts", Icons.Rounded.Tune) { section = "graphics" }
                    ActionTile("Game settings", "Frame rate, CPU clock and game-specific features", Icons.Rounded.Code) { section = "game" }
                }
                else -> SettingsIndex { section = it }
            }
        }
    }
}

private fun hasCodeLibrary() = File(Storage.recomp, "0004000000126300.so").isFile
private fun recompiledOptionsAvailable() = hasCodeLibrary() && (!Native.isRunning() || Native.notice().isBlank())

@Composable private fun SettingsIndex(onSelect: (String) -> Unit) {
    val entries = listOf(
        Triple("display", "Display & performance", Icons.Rounded.Fullscreen),
        Triple("game", "Game settings", Icons.Rounded.Speed),
        Triple("touch", "Touch controls", Icons.Rounded.TouchApp),
        Triple("saves", "Saves", Icons.Rounded.Save),
        Triple("controller", "Controller & keyboard", Icons.Rounded.SportsEsports),
        Triple("audio", "Audio", Icons.AutoMirrored.Rounded.VolumeUp),
        Triple("online", "Multiplayer", Icons.Rounded.Wifi),
        Triple("files", "Game & storage", Icons.Rounded.FolderOpen),
        Triple("advanced", "Advanced", Icons.Rounded.Build),
        Triple("reports", "Help & reports", Icons.Rounded.BugReport),
    )
    LazyVerticalGrid(columns = GridCells.Adaptive(200.dp), contentPadding = PaddingValues(10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
        items(entries, key = { it.first }) { (id, label, icon) ->
            Surface(onClick = { onSelect(id) }, enabled = !FileWork.busy, shape = RoundedCornerShape(12.dp),
                color = Mh4uColors.PopupBg, contentColor = Mh4uColors.Text, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(icon, null, tint = Mh4uColors.Amber, modifier = Modifier.size(20.dp))
                    Text(label, fontSize = 14.sp, lineHeight = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Icon(Icons.Rounded.ChevronRight, null, tint = Mh4uColors.TextDisabled, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable fun SettingsColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
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
    FrameRateSettings(recompiledAvailable = recompiledOptionsAvailable())
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
        Hint("Hide / Show toggles the small touch screen. 2nd enlarges it; Back returns to your layout.")
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
    val version = remember { context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty() }
    var license by remember { mutableStateOf(false) }
    var archiveLicenses by remember { mutableStateOf(false) }
    SettingsColumn {
        Text("Monster Hunter 4", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Hint("Version $version · Android")
        Panel { Text("A simpler home for your hunts.", fontWeight = FontWeight.SemiBold); Hint("Custom Android interface and controls by Rowell.") }
        SectionTitle("Built with the community")
        Panel { Text("Android base: klark231 / MH4U-Android"); Spacer(Modifier.height(8.dp)); Text("Original port: CompaLarios18 / MH4UltimateDC"); Spacer(Modifier.height(8.dp)); Text("Emulator core: Azahar / AzaharPlus"); Spacer(Modifier.height(8.dp)); Hint("Monster Hunter 4 Ultimate and its game content belong to Capcom. Game files are not included.") }
        QuietButton("Open-source licence") { license = true }
        QuietButton("Archive library licences") { archiveLicenses = true }
    }
    if (license) AlertDialog(onDismissRequest = { license = false }, title = { Text("GNU GPL version 3") }, text = { Text(remember { context.assets.open("LICENSE.txt").bufferedReader().use { it.readText() } }, modifier = Modifier.heightIn(max = 350.dp).verticalScroll(rememberScrollState()), fontSize = 12.sp) }, confirmButton = { TextButton(onClick = { license = false }) { Text("Close") } })
    if (archiveLicenses) AlertDialog(onDismissRequest = { archiveLicenses = false }, title = { Text("Archive library licences") }, text = { Text(remember { context.assets.open("archive-licenses.txt").bufferedReader().use { it.readText() } }, modifier = Modifier.heightIn(max = 350.dp).verticalScroll(rememberScrollState()), fontSize = 12.sp) }, confirmButton = { TextButton(onClick = { archiveLicenses = false }) { Text("Close") } })
}
