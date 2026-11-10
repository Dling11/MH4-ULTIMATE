package org.mh4u.port.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
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
import org.mh4u.port.saves.NativeSaveImport
import org.mh4u.port.saves.DocumentSaveEntry
import org.mh4u.port.saves.DocumentSaveExport
import org.mh4u.port.saves.SaveGameSession
import org.mh4u.port.saves.SaveImportPlan
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

fun saveTime(milliseconds: Long): String = DateTimeFormatter.ofPattern("MMM d, yyyy · h:mm:ss a XXX", Locale.getDefault())
    .withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(milliseconds))

@Composable fun ConfirmAction(title: String, description: String, onNo: () -> Unit, onYes: () -> Unit) {
    SaveModal(title, onNo, cancelLabel = "No", confirmLabel = "Yes", onConfirm = onYes) { Text(description) }
}

@Composable fun SavesPage(inGame: Boolean, session: SaveGameSession? = null) {
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
    var importDetails by rememberSaveable { mutableStateOf("") }
    var resultTitle by rememberSaveable { mutableStateOf("") }
    var resultMessage by rememberSaveable { mutableStateOf("") }
    val enabled = !FileWork.busy && FileWork.recoveryError.isEmpty()
    BackHandler(FileWork.busy) { /* Keep the file operation's screen alive. */ }

    fun operation(run: suspend () -> String, done: () -> Unit = {}, completeTitle: String? = null) {
        scope.launch {
            try {
                message = run()
                if (completeTitle != null) { resultTitle = completeTitle; resultMessage = message }
                done()
            }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                message = e.message ?: "The operation could not be completed."
                resultTitle = "Could not complete this action"; resultMessage = message
                if (File(Storage.root, ".save-restore").exists()) FileWork.recoveryError = "Save recovery needs another attempt. Keep the app installed and use Retry recovery."
            }
            finally { refresh++ }
        }
    }
    fun work(block: suspend () -> String, done: () -> Unit = {}, completeTitle: String? = null) = operation({ FileWork.run(action = block) }, done, completeTitle)
    fun readSavedFiles(block: () -> String, done: () -> Unit = {}, completeTitle: String? = null) = operation({ FileWork.readSavedFiles(block) }, done, completeTitle)
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
        }, completeTitle = "Backup file exported") else { pending?.delete(); message = "Export canceled." }
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
            importDetails = "Backup file · ${saveTime(checked?.createdAt ?: 0L)}"
        })
    }
    val nativeImportPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        var prepared: NativeSaveImport.Prepared? = null
        if (uri != null) work({
            prepared = NativeSaveImport().prepare(DocumentSaveEntry.selected(context.contentResolver, uri), context.cacheDir)
            "Save folder checked. Review and confirm before replacing anything."
        }, done = {
            prepared?.let { incoming ->
                restoring = incoming.backup
                importPath = incoming.backup.file.absolutePath
                importDetails = "Folder: ${incoming.source}\nCharacter slots: ${incoming.slots.joinToString()}\n" +
                    if (incoming.extraData) "Guild cards, event quests and Palico data included."
                    else "No extra data included; current guild cards, event quests and Palico data will be removed."
            }
        }) else message = "Import canceled."
    }
    val nativeExportPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val pending = exportPath.takeIf { it.isNotEmpty() }?.let(::File)
        exportPath = ""
        if (uri != null && pending != null) work({
            try {
                val folder = DocumentSaveExport.export(context.contentResolver, uri, pending, repository)
                "Export verified: $folder. Keep the complete folder and use Import save in your other MH4U app."
            } finally { pending.delete() }
        }, completeTitle = "Save folder exported") else { pending?.delete(); message = "Export canceled." }
    }

    SettingsColumn {
        Panel {
            Text("Your saved progress")
            Hint("Save inside MH4U first. Back up that progress, keep a copy outside the app, or bring in an existing MH4U save.")
            Hint("These tools leave your touch controls and graphics settings unchanged.")
        }
        if (FileWork.recoveryError.isNotEmpty()) Panel {
            Text("Save recovery needs attention")
            Hint(FileWork.recoveryError)
            QuietButton("Retry recovery", enabled = !inGame && !FileWork.busy) { work({ repository.recover(); "Previous saved progress recovered." }, done = { FileWork.recoveryError = "" }) }
        }
        if (message.isNotEmpty()) Notice(message)
        if (FileWork.busy) { LinearProgressIndicator(Modifier.fillMaxWidth()); Hint("Working with saved files. Please keep this app open.") }
        Panel {
            if (inGame) Hint("Backup and Export copy your last in-game save. Import asks first, then restarts the game with the selected progress.")
            SaveOptionCard("Create backup", "Keep up to 3 copies in this app. Unchanged saves reuse the latest copy.", Icons.Rounded.Save,
                enabled = enabled) { action = "backup" }
            Spacer(Modifier.height(10.dp))
            SaveOptionCard("Export save", "Keep a copy outside this app or use it in another MH4U app.", Icons.Rounded.Upload,
                enabled = enabled) { action = "export" }
            Spacer(Modifier.height(10.dp))
            SaveOptionCard("Import save", "Choose existing MH4U saved progress or a backup file.", Icons.Rounded.Download,
                enabled = enabled) { action = "import-format" }
        }
        SectionTitle("Choose a backup to restore")
        if (entries.isEmpty()) Hint("No backups yet. Create one after saving inside MH4U.")
        entries.forEach { backup -> Panel {
            Text(saveTime(backup.createdAt))
            Hint("${backup.reason.take(80)} · ${backup.files} files · ${(backup.bytes / 1024).coerceAtLeast(1)} KB")
            QuietButton("Restore this backup", enabled = enabled) { importDetails = "Backup from this app · ${saveTime(backup.createdAt)}"; restoring = backup }
        } }
    }
    if (action == "backup") ConfirmAction("Back up saved progress?", "Copies your latest in-game save. If three backups already exist, the oldest is replaced after the new copy is verified.", { action = "" }) {
        action = ""; readSavedFiles({ val backup = repository.create(); "Backup ready · ${saveTime(backup.createdAt)}" }, completeTitle = "Backup ready")
    }
    if (action == "export" || action == "import-format") {
        val exporting = action == "export"
        SaveFormatDialog(exporting, onCancel = { action = "" },
            onFolder = { action = if (exporting) "export-folder" else "import-folder" },
            onZip = { action = if (exporting) "export-zip" else "import-zip" })
    }
    if (action == "import-folder") SaveModal("Import from another MH4U app", { action = "" }, confirmLabel = "Choose folder", onConfirm = {
        action = ""; nativeImportPicker.launch(null)
    }) {
        Text("1. Save inside your other MH4U app, then use its Export save option.")
        Text("2. Choose the dated exported folder containing sdmc here.")
        Hint("Supported exports include Native and Azahar. Use an MH4U USA save, not a game image or game-data folder.")
        Hint("The next screen lets you review the characters. Nothing is replaced until you choose Yes.")
    }
    if (action == "import-zip") SaveModal("Import a backup file", { action = "" }, confirmLabel = "Choose ZIP file", onConfirm = {
        action = ""; importPicker.launch(arrayOf("*/*"))
    }) {
        Text("Choose a backup ZIP previously created with Export save → Keep a backup file in this app.")
        Hint("For an export from another MH4U app, go back and choose From another MH4U app instead.")
        Hint("You can review the backup before confirming. The game restarts after a successful import.")
    }
    if (action == "export-folder") ConfirmAction("Export for another MH4U app?",
        "Creates a copy of your last in-game save.\n\nChoose or create MH4U_Saves directly in Internal storage. A new dated folder will be made inside it. Keep the complete folder for your other app's Import save option.\n\nYour progress stays in this app; unsaved gameplay is not included.", { action = "" }) {
        action = ""
        var destination: File? = null
        readSavedFiles({
            val file = File.createTempFile("mh4u-export-", ".zip", context.cacheDir)
            try { repository.export(file); destination = file }
            catch (error: Exception) { file.delete(); throw error }
            "Choose MH4U_Saves directly in Internal storage."
        }, done = { exportPath = checkNotNull(destination).absolutePath; nativeExportPicker.launch(null) })
    }
    if (action == "export-zip") ConfirmAction("Export a backup file?", "Creates one ZIP file from your last in-game save. Choose where to keep it outside the app.\n\nUse Import save → From a backup file to restore it here later. This does not save your current live game session.", { action = "" }) {
        val destination = File(context.cacheDir, "mh4u-save-export.zip")
        action = ""; readSavedFiles({
            repository.export(destination)
            "Choose where to save your ZIP."
        }, done = { exportPath = destination.absolutePath; exportPicker.launch("MH4U-save-${java.time.LocalDate.now()}.zip") })
    }
    restoring?.let { backup ->
        fun cancel() { restoring = null; if (importPath.isNotEmpty()) File(importPath).delete(); importPath = "" }
        val importing = importPath.isNotEmpty()
        ConfirmAction(if (importing) "Import this saved progress?" else "Restore this backup?",
            "$importDetails\n\nReplaces this app's saved characters and progress. Your current saved progress is backed up first.\n\nThe game will close and restart after importing. Choose Continue and load your character. Unsaved gameplay will be lost.\n\nYour controls and graphics settings stay unchanged.", ::cancel) {
            restoring = null
            val imported = importPath; importPath = ""
            scope.launch {
                var importedSuccessfully = false
                try {
                    message = FileWork.run {
                        SaveImportPlan(repository).restore(backup.file,
                            closeAndWait = { session?.closeForImport() }, coreClosed = { Native.saveDataIdle() })
                        "Saved progress imported. Load your character with Continue."
                    }
                    importedSuccessfully = true
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) {
                    message = e.message ?: "The saved progress could not be imported."
                    resultTitle = "Import did not finish"; resultMessage = message
                    if (File(Storage.root, ".save-restore").exists()) FileWork.recoveryError = "Save recovery needs another attempt. Keep the app installed and use Retry recovery."
                    session?.importFailed(message)
                } finally { if (imported.isNotEmpty()) File(imported).delete(); refresh++ }
                if (importedSuccessfully) session?.restartAfterImport()
            }
        }
    }
    if (resultTitle.isNotEmpty()) SaveModal(resultTitle, onCancel = { resultTitle = "" }, cancelLabel = "OK") {
        Text(resultMessage)
    }
}
