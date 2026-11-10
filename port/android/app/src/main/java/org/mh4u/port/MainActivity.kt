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
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import org.mh4u.port.saves.SaveGameSession
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

    companion object { const val EXTRA_SAVE_IMPORTED = "saved_progress_imported" }

    @Composable private fun Launcher() {
        var screen by rememberSaveable { mutableStateOf("home") }
        var settingsSection by rememberSaveable { mutableStateOf("") }
        var revision by remember { mutableIntStateOf(0) }
        var busy by remember { mutableStateOf("") }
        var progress by remember { mutableFloatStateOf(-1f) }
        val scope = rememberCoroutineScope()
        val game = remember(revision) { Prefs.gameFile() }
        val saveSession = object : SaveGameSession {
            override suspend fun closeForImport() {
                Native.stop()
                check(withTimeoutOrNull(30_000) { while (!Native.saveDataIdle()) delay(25); true } == true) {
                    "The game did not finish closing. Saved progress has not been replaced. Try again from Home."
                }
            }
            override fun restartAfterImport() {
                SaveRestartActivity.restart(this@MainActivity)
            }
            override fun importFailed(message: String) { lastMessage = message }
        }
        BackHandler(screen != "home" && busy.isEmpty()) { screen = "home" }
        LaunchedEffect(Unit) {
            if (intent.getBooleanExtra(EXTRA_SAVE_IMPORTED, false)) {
                intent.removeExtra(EXTRA_SAVE_IMPORTED)
                if (FileWork.recoveryError.isEmpty() && !File(Storage.root, ".save-restore").exists() && Prefs.gameFile() != null) {
                    gameResult.launch(Intent(this@MainActivity, EmulationActivity::class.java)
                        .putExtra(EmulationActivity.EXTRA_GAME, Prefs.gamePath))
                } else lastMessage = if (FileWork.recoveryError.isNotEmpty()) FileWork.recoveryError
                    else "Saved progress imported. Add your game, then choose Play game → Continue."
            }
        }

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

        Scaffold(containerColor = Mh4uColors.WindowBg) { insets ->
            Row(Modifier.fillMaxSize().padding(insets)) {
                LauncherRail(screen, enabled = busy.isEmpty() && !FileWork.busy) { screen = it; settingsSection = "" }
                Box(Modifier.weight(1f).fillMaxHeight()) {
                if (busy.isNotEmpty()) {
                    Column(Modifier.padding(20.dp)) {
                        Text(busy); Spacer(Modifier.height(12.dp))
                        if (progress >= 0) { LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth()); Hint("${(progress * 100).toInt()}% · Please keep this app open.") }
                        else LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                } else when (screen) {
                    "settings" -> SettingsHub(initialSection = settingsSection, onPickGame = { pickGame.launch(arrayOf("*/*")) }, onPickLibrary = { pickLibrary.launch(arrayOf("*/*")) }, onPickUpdate = { pickUpdate.launch(arrayOf("*/*")) }, saveSession = saveSession)
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
}

@Composable private fun HomePanel(game: File?, message: String, onPlay: () -> Unit, onChoose: () -> Unit, onTouch: () -> Unit, onDisplay: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 520.dp
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (message.isNotEmpty()) Notice(message)
            Surface(Modifier.fillMaxWidth(), color = Mh4uColors.TitleBgActive, shape = RoundedCornerShape(16.dp)) {
                if (wide) Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(Modifier.weight(1f)) { HomeGameSummary(game, onChoose) }
                    HomePlayButton(game, onPlay)
                } else Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HomeGameSummary(game, onChoose)
                    HomePlayButton(game, onPlay, Modifier.fillMaxWidth())
                }
            }
            if (wide) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.weight(1f)) { ActionTile("Touch controls", "Arrange buttons and add combos", Icons.Rounded.SportsEsports, onTouch) }
                    Box(Modifier.weight(1f)) { ActionTile("Display & performance", "Screen layout and picture quality", Icons.Rounded.Tune, onDisplay) }
                }
            } else {
                ActionTile("Touch controls", "Arrange buttons and add combos", Icons.Rounded.SportsEsports, onTouch)
                ActionTile("Display & performance", "Screen layout and picture quality", Icons.Rounded.Tune, onDisplay)
            }
        }
    }
}

@Composable private fun HomeGameSummary(game: File?, onChoose: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Image(painterResource(R.drawable.mh4_logo), "Monster Hunter 4 Ultimate logo", Modifier.size(56.dp))
        Column(Modifier.weight(1f)) {
            Text("Monster Hunter 4 Ultimate", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(game?.name ?: "Choose your decrypted game image to get started.", color = Mh4uColors.TextDisabled, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (game != null) TextButton(enabled = !FileWork.busy, onClick = onChoose, contentPadding = PaddingValues(horizontal = 0.dp)) {
                Icon(Icons.Rounded.FolderOpen, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Change game", fontSize = 12.sp)
            }
        }
    }
}

@Composable private fun HomePlayButton(game: File?, onPlay: () -> Unit, modifier: Modifier = Modifier) {
    Button(onClick = onPlay, enabled = !FileWork.busy, modifier = modifier.heightIn(min = 48.dp).widthIn(min = 120.dp), shape = RoundedCornerShape(12.dp)) {
        Icon(if (game != null) Icons.Rounded.PlayArrow else Icons.Rounded.Add, null)
        Spacer(Modifier.width(8.dp))
        Text(if (game != null) "Play game" else "Add game", fontSize = 15.sp)
    }
}
