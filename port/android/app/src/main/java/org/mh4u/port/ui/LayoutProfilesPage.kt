package org.mh4u.port.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mh4u.port.*
import org.mh4u.port.overlay.*
import java.io.File

@Composable fun LayoutProfilesPage(changed: () -> Unit) {
    val repository = remember { LayoutProfiles(File(Storage.root, "control-layouts")) }
    val scope = rememberCoroutineScope()
    var profiles by remember { mutableStateOf(emptyList<LayoutProfiles.Profile>()) }
    var revision by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf<LayoutProfiles.Profile?>(null) }
    var deleting by remember { mutableStateOf<LayoutProfiles.Profile?>(null) }
    var resetting by remember { mutableStateOf(false) }
    BackHandler(FileWork.busy) {}
    LaunchedEffect(revision) { profiles = withContext(Dispatchers.IO) { repository.list() } }
    fun work(block: () -> String, applied: Boolean = false) {
        scope.launch {
            try { message = FileWork.run(action = block); if (applied) changed() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { message = e.message ?: "The layout could not be saved." }
            finally { revision++ }
        }
    }
    SettingsColumn {
        Panel {
            Text("Saved control layouts")
            Hint("Keep named copies of your buttons, combos, colours and vibration settings.")
            Hint("Save your current arrangement here before loading another one.")
        }
        if (message.isNotEmpty()) Notice(message)
        if (FileWork.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        Panel {
            OutlinedTextField(name, { name = it.take(32) }, label = { Text("Layout name") }, singleLine = true, enabled = !FileWork.busy, modifier = Modifier.fillMaxWidth())
            AmberButton("Save current layout", enabled = !FileWork.busy && name.isNotBlank()) {
                val chosen = name
                work({ repository.save(chosen, OverlayLayout.load(false)); "Layout saved as ${chosen.trim()}." })
            }
        }
        profiles.forEach { profile -> Panel {
            Text(profile.name)
            Hint(saveTime(profile.createdAt))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuietButton("Load", enabled = !FileWork.busy) { loading = profile }
                QuietButton("Delete", enabled = !FileWork.busy) { deleting = profile }
            }
        } }
        QuietButton("Use default layout", enabled = !FileWork.busy) { resetting = true }
    }
    loading?.let { profile -> ConfirmAction("Load ${profile.name}?", "Replaces the active button arrangement with this saved layout. Your game progress stays unchanged.", { loading = null }) {
        loading = null; work({ repository.load(profile.id).save(false); "${profile.name} loaded." }, true)
    } }
    deleting?.let { profile -> ConfirmAction("Delete ${profile.name}?", "Removes this saved layout copy. The buttons currently in use stay as they are.", { deleting = null }) {
        deleting = null; work({ repository.delete(profile.id); "Saved layout deleted." })
    } }
    if (resetting) ConfirmAction("Use the default layout?", "Replaces the active buttons and combos with the defaults. Your named layouts stay available.", { resetting = false }) {
        resetting = false; work({ OverlayLayout.defaults(false).save(false); "Default controls loaded." }, true)
    }
}
