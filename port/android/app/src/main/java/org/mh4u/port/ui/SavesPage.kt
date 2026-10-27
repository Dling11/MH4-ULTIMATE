package org.mh4u.port.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mh4u.port.*
import org.mh4u.port.saves.SaveArchive
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

fun saveTime(milliseconds: Long): String = DateTimeFormatter.ofPattern("MMM d, yyyy · h:mm:ss a XXX", Locale.getDefault())
    .withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(milliseconds))

@Composable fun ConfirmAction(title: String, description: String, onNo: () -> Unit, onYes: () -> Unit) {
    AlertDialog(onDismissRequest = onNo, title = { Text(title) }, text = { Text(description) },
        confirmButton = { TextButton(onClick = onYes) { Text("Yes") } }, dismissButton = { TextButton(onClick = onNo) { Text("No") } })
}

@Composable fun SavesPage(inGame: Boolean) {
    val context = LocalContext.current
    val repository = remember { SaveArchive(Storage.root) }
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf(emptyList<SaveArchive.Backup>()) }
    var refresh by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf("") }
    var action by remember { mutableStateOf("") }
    var restoring by remember { mutableStateOf<SaveArchive.Backup?>(null) }
    var importPath by rememberSaveable { mutableStateOf("") }
    var exportPath by rememberSaveable { mutableStateOf("") }
    val enabled = !inGame && !FileWork.busy && FileWork.recoveryError.isEmpty()
    BackHandler(FileWork.busy) { /* Keep the file operation's screen alive. */ }

    fun work(block: () -> String, done: () -> Unit = {}) {
        scope.launch {
            try { message = FileWork.run(idleGame = true, action = block); done() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                message = e.message ?: "The operation could not be completed."
                if (File(Storage.root, ".save-restore").exists()) FileWork.recoveryError = "Save recovery needs another attempt. Keep the app installed and use Retry recovery."
            }
            finally { refresh++ }
        }
    }
    LaunchedEffect(refresh) { entries = withContext(Dispatchers.IO) { repository.list() } }

    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        val pending = exportPath.takeIf { it.isNotEmpty() }?.let(::File)
        exportPath = ""
        if (uri != null && pending != null) work({
            try {
                context.contentResolver.openOutputStream(uri, "w")?.use { output -> pending.inputStream().use { it.copyTo(output) } }
                    ?: error("The selected destination could not be opened.")
                "Saved progress exported. Keep this ZIP somewhere outside the app."
            } finally { pending.delete() }
        }) else { pending?.delete(); message = "Export canceled." }
    }
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        var checked: SaveArchive.Backup? = null
        if (uri != null) work({
            val imported = File(context.cacheDir, "mh4u-save-import.zip")
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    imported.outputStream().use { output ->
                        val buffer = ByteArray(8192); var total = 0L
                        while (true) {
                            val count = input.read(buffer); if (count < 0) break
                            total += count; check(total <= 300L * 1024 * 1024) { "This save ZIP is too large." }
                            output.write(buffer, 0, count)
                        }
                    }
                } ?: error("The selected file could not be opened.")
                checked = repository.validate(imported)
                "Backup checked. Confirm below to restore it."
            } catch (e: Exception) { imported.delete(); throw e }
        }, done = {
            restoring = checked
            importPath = checked?.file?.absolutePath.orEmpty()
        })
    }

    SettingsColumn {
        Panel {
            Text("Protect your saved progress")
            Hint("Save inside MH4U first. These tools copy that saved progress and its extra data.")
            Hint("Your touch controls and graphics settings stay as they are.")
            Hint("Supports this app's MH4U USA saves.")
            if (inGame) Hint("Exit the game after saving, then open Home → Settings → Saves.")
        }
        if (FileWork.recoveryError.isNotEmpty()) Panel {
            Text("Save recovery needs attention")
            Hint(FileWork.recoveryError)
            QuietButton("Retry recovery", enabled = !inGame && !FileWork.busy) { work({ repository.recover(); "Previous saved progress recovered." }, done = { FileWork.recoveryError = "" }) }
        }
        if (message.isNotEmpty()) Notice(message)
        if (FileWork.busy) { LinearProgressIndicator(Modifier.fillMaxWidth()); Hint("Working with saved files. Please keep this app open.") }
        Panel {
            AmberButton("Create backup", enabled = enabled) { action = "backup" }
            Hint("Keeps the newest 3 backups. Unchanged saves reuse the latest copy.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuietButton("Export save", Modifier.weight(1f), enabled) { action = "export" }
                QuietButton("Import save ZIP", Modifier.weight(1f), enabled) { importPicker.launch(arrayOf("*/*")) }
            }
            Hint("Export chooses where to save a portable ZIP. Import lets you select a Rowell save ZIP.")
        }
        SectionTitle("Choose a backup to restore")
        if (entries.isEmpty()) Hint("No backups yet. Create one after saving inside MH4U.")
        entries.forEach { backup -> Panel {
            Text(saveTime(backup.createdAt))
            Hint("${backup.reason} · ${backup.files} files · ${(backup.bytes / 1024).coerceAtLeast(1)} KB")
            QuietButton("Restore this backup", enabled = enabled) { restoring = backup }
        } }
    }
    if (action == "backup") ConfirmAction("Back up saved progress?", "Copies your latest in-game save. If three backups already exist, the oldest is replaced after the new copy is verified.", { action = "" }) {
        action = ""; work({ repository.recover(); val backup = repository.create(); "Backup ready · ${saveTime(backup.createdAt)}" })
    }
    if (action == "export") ConfirmAction("Export saved progress?", "Choose where to save a ZIP of your latest in-game save. This does not save your current live game session.", { action = "" }) {
        val destination = File(context.cacheDir, "mh4u-save-export.zip")
        action = ""; work({
            repository.recover()
            repository.export(destination)
            "Choose where to save your ZIP."
        }, done = { exportPath = destination.absolutePath; exportPicker.launch("MH4U-Rowell-save-${java.time.LocalDate.now()}.zip") })
    }
    restoring?.let { backup ->
        fun cancel() { restoring = null; if (importPath.isNotEmpty()) File(importPath).delete(); importPath = "" }
        ConfirmAction("Restore this backup?", "${saveTime(backup.createdAt)}\n\nReplaces saved progress with this copy. Your current save is backed up first. Tap Play game afterward and load your character.", ::cancel) {
            restoring = null
            val imported = importPath; importPath = ""
            work({ try { repository.restore(backup.file); "Save restored. Tap Play game and load your character." } finally { if (imported.isNotEmpty()) File(imported).delete() } })
        }
    }
}
