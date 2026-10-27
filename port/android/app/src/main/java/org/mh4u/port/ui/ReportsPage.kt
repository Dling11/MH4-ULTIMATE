package org.mh4u.port.ui

import android.content.ClipData
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.mh4u.port.FileWork
import org.mh4u.port.diagnostics.Diagnostics

@Composable fun ReportsPage() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirm by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    BackHandler(FileWork.busy) {}
    SettingsColumn {
        Panel {
            Text("Help troubleshoot a problem")
            Hint("Create a ZIP with the app version, device model, graphics settings and recent app logs.")
            Hint("Game files and saved progress are excluded. Common private details are filtered. Review the report before sharing.")
            AmberButton("Share error report", enabled = !FileWork.busy) { confirm = true }
        }
        if (FileWork.busy) LinearProgressIndicator()
        if (message.isNotEmpty()) Notice(message)
        Panel { Hint("When reporting a problem, include what you tapped and what happened. A screenshot helps with display issues.") }
    }
    if (confirm) ConfirmAction("Create an error report?", "Collects recent app logs and basic device/settings details. Android will ask where you want to share the ZIP.", { confirm = false }) {
        confirm = false
        scope.launch {
            try {
                val report = FileWork.run { Diagnostics.create(context) }
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.reports", report)
                val intent = Intent(Intent.ACTION_SEND).setType("application/zip").putExtra(Intent.EXTRA_STREAM, uri)
                    .apply { clipData = ClipData.newRawUri("MH4U error report", uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                context.startActivity(Intent.createChooser(intent, "Share MH4U error report"))
                message = "Report ready. No game or save files were included."
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { message = e.message ?: "The report could not be created." }
        }
    }
}
