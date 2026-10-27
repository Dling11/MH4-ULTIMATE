package org.mh4u.port

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mh4u.port.ui.*
import java.io.File
import java.io.IOException

/** Rowell's Android launcher. Importing a game is the only required setup. */
class MainActivity : ComponentActivity() {
    private var lastMessage by mutableStateOf("")
    private val gameResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        lastMessage = it.data?.getStringExtra(EmulationActivity.EXTRA_MESSAGE).orEmpty()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Mh4uTheme { Launcher() } }
        installInputDispatch(onKey = { MH4UInput.learning() && MH4UInput.onKey(it) }, onMotion = { MH4UInput.learning() && MH4UInput.onMotion(it) })
    }

    @Composable private fun Launcher() {
        var screen by rememberSaveable { mutableStateOf("home") }
        var settingsSection by rememberSaveable { mutableStateOf("") }
        var revision by remember { mutableIntStateOf(0) }
        var busy by remember { mutableStateOf("") }
        var progress by remember { mutableFloatStateOf(-1f) }
        val scope = rememberCoroutineScope()
        val game = remember(revision) { Prefs.gameFile() }
        BackHandler(screen != "home" && busy.isEmpty()) { screen = "home" }

        suspend fun copyDocument(uri: Uri, directory: File, extensions: Set<String>, fixedName: String? = null): File = withContext(Dispatchers.IO) {
            val shown = contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(c.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)) else null
            } ?: throw IOException("The selected file has no name.")
            val name = File(shown).name
            require(name.substringAfterLast('.', "").lowercase() in extensions) { "Choose a ${extensions.joinToString(" / ")} file." }
            directory.mkdirs()
            val temporary = File.createTempFile("import-", ".partial", directory)
            try {
                val total = contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: -1L
                var done = 0L; var reported = -1
                contentResolver.openInputStream(uri)?.use { input ->
                    temporary.outputStream().use { out ->
                        val buffer = ByteArray(1024 * 1024)
                        while (true) {
                            val n = input.read(buffer); if (n < 0) break
                            out.write(buffer, 0, n); done += n
                            val percent = if (total > 0) (done * 100 / total).toInt() else -1
                            if (percent != reported) { reported = percent; withContext(Dispatchers.Main) { progress = if (total > 0) done.toFloat() / total else -1f } }
                        }
                    }
                } ?: throw IOException("This file could not be opened.")
                require(done > 0 && (total < 0 || done == total)) { "The file was not completely copied. Please try again." }
                var destination = File(directory, fixedName ?: name)
                if (fixedName == null && destination.exists()) destination = File(directory, "${destination.nameWithoutExtension}-${System.currentTimeMillis()}.${destination.extension}")
                java.nio.file.Files.move(temporary.toPath(), destination.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                destination
            } finally { temporary.delete() }
        }

        val pickGame = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) scope.launch {
                busy = "Adding your game…"; progress = -1f; lastMessage = ""
                try {
                    val added = copyDocument(uri, Storage.games, setOf("3ds", "cci", "cxi"))
                    Prefs.gamePath = "!${added.absolutePath}"; revision++; screen = "home"
                } catch (e: Exception) { lastMessage = e.message ?: "The game could not be added." }
                finally { busy = "" }
            }
        }
        val pickLibrary = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) scope.launch {
                busy = "Adding optional code library…"; progress = -1f
                try { copyDocument(uri, Storage.recomp, setOf("so"), "0004000000126300.so"); lastMessage = "Code library added."; revision++ }
                catch (e: Exception) { lastMessage = e.message ?: "The library could not be added." }
                finally { busy = "" }
            }
        }
        val pickUpdate = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) scope.launch {
                busy = "Installing optional game update…"; progress = -1f
                var imported: File? = null
                try {
                    val file = copyDocument(uri, cacheDir, setOf("cia")); imported = file
                    val status = withContext(Dispatchers.IO) { Native.installCia("!${file.absolutePath}") }
                    lastMessage = if (status == 0) "Game update installed." else "The update could not be installed (code $status)."
                } catch (e: Exception) { lastMessage = e.message ?: "The update could not be installed." }
                finally { imported?.delete(); busy = "" }
            }
        }

        Scaffold(containerColor = Mh4uColors.WindowBg, bottomBar = {
            NavigationBar(containerColor = Mh4uColors.TitleBg) {
                listOf(Triple("home", "Home", Icons.Rounded.Home), Triple("settings", "Settings", Icons.Rounded.Settings), Triple("about", "About", Icons.Rounded.Info)).forEach { (id, label, icon) ->
                    NavigationBarItem(selected = screen == id, enabled = busy.isEmpty() && !FileWork.busy, onClick = { screen = id; settingsSection = "" }, icon = { Icon(icon, label) }, label = { Text(label) })
                }
            }
        }) { insets ->
            Column(Modifier.fillMaxSize().padding(insets)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Image(painterResource(R.drawable.mh4_logo), "Monster Hunter 4 Ultimate logo", Modifier.width(64.dp).height(42.dp))
                    Spacer(Modifier.width(12.dp))
                    Column { Text("Monster Hunter 4", fontWeight = FontWeight.Bold, fontSize = 20.sp); Text("ROWELL", color = Mh4uColors.Amber, fontSize = 11.sp, letterSpacing = 3.sp) }
                }
                if (busy.isNotEmpty()) {
                    Column(Modifier.padding(20.dp)) {
                        Text(busy); Spacer(Modifier.height(12.dp))
                        if (progress >= 0) { LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth()); Hint("${(progress * 100).toInt()}% · Please keep this app open.") }
                        else LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                } else when (screen) {
                    "settings" -> SettingsHub(initialSection = settingsSection, onPickGame = { pickGame.launch(arrayOf("*/*")) }, onPickLibrary = { pickLibrary.launch(arrayOf("*/*")) }, onPickUpdate = { pickUpdate.launch(arrayOf("*/*")) })
                    "about" -> AboutPage()
                    else -> HomePanel(game, lastMessage, onPlay = {
                        if (FileWork.busy) lastMessage = "Please wait for the file operation to finish."
                        else if (FileWork.recoveryError.isNotEmpty() || File(Storage.root, ".save-restore").exists()) {
                            if (FileWork.recoveryError.isEmpty()) FileWork.recoveryError = "A previous save restore needs recovery. Use Retry recovery."
                            lastMessage = "Open Settings → Saves to finish save recovery before playing."; settingsSection = "saves"; screen = "settings"
                        }
                        else if (game == null) pickGame.launch(arrayOf("*/*"))
                        else { lastMessage = ""; gameResult.launch(Intent(this@MainActivity, EmulationActivity::class.java).putExtra(EmulationActivity.EXTRA_GAME, Prefs.gamePath)) }
                    }, onChoose = { pickGame.launch(arrayOf("*/*")) }, onTouch = { settingsSection = "touch"; screen = "settings" }, onDisplay = { settingsSection = "display"; screen = "settings" })
                }
            }
        }
    }
}

@Composable private fun HomePanel(game: File?, message: String, onPlay: () -> Unit, onChoose: () -> Unit, onTouch: () -> Unit, onDisplay: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp).padding(bottom = 8.dp)) {
        if (message.isNotEmpty()) { Notice(message, Modifier.heightIn(max = 70.dp).verticalScroll(rememberScrollState())); Spacer(Modifier.height(8.dp)) }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Surface(Modifier.weight(1.1f).fillMaxHeight(), color = Mh4uColors.TitleBgActive, shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Image(painterResource(R.drawable.mh4_logo), null, Modifier.width(96.dp).height(64.dp))
                        Spacer(Modifier.width(16.dp))
                        Text("Monster Hunter\n4 Ultimate", fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    }
                    if (game != null) Text(game.name, color = Mh4uColors.TextDisabled, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = onPlay, enabled = !FileWork.busy, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)) {
                        Icon(if (game != null) Icons.Rounded.PlayArrow else Icons.Rounded.Add, null); Spacer(Modifier.width(8.dp)); Text(if (game != null) "Play game" else "Add game", fontSize = 17.sp)
                    }
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionTile("Touch controls", "Arrange buttons and add combos", Icons.Rounded.SportsEsports, onTouch)
                ActionTile("Display & performance", "Fullscreen and picture quality", Icons.Rounded.Tune, onDisplay)
                if (game != null) TextButton(onClick = onChoose) { Icon(Icons.Rounded.FolderOpen, null); Spacer(Modifier.width(8.dp)); Text("Choose another game") }
            }
        }
    }
}
