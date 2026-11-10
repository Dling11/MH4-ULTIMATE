package org.mh4u.port.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** Scrollable content and a fixed footer keep confirmations reachable in landscape. */
@Composable fun SaveModal(
    title: String, onCancel: () -> Unit, cancelLabel: String = "Cancel",
    confirmLabel: String? = null, onConfirm: () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val maximumHeight = (LocalConfiguration.current.screenHeightDp - 24).coerceAtLeast(180).dp
    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp).widthIn(max = 720.dp).fillMaxWidth().heightIn(max = maximumHeight),
            shape = RoundedCornerShape(20.dp), color = Mh4uColors.PopupBg, contentColor = Mh4uColors.Text,
            border = BorderStroke(1.dp, Mh4uColors.Border), tonalElevation = 6.dp,
        ) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
                Text(title, fontSize = 21.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onCancel, modifier = Modifier.heightIn(min = 48.dp)) { Text(cancelLabel) }
                    if (confirmLabel != null) {
                        Spacer(Modifier.width(12.dp))
                        Button(onClick = onConfirm, modifier = Modifier.heightIn(min = 48.dp)) { Text(confirmLabel) }
                    }
                }
            }
        }
    }
}

@Composable fun SaveOptionCard(
    title: String, description: String, icon: ImageVector, modifier: Modifier = Modifier,
    enabled: Boolean = true, onClick: () -> Unit,
) {
    Surface(onClick = onClick, enabled = enabled, modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp), color = Mh4uColors.TitleBg, contentColor = Mh4uColors.Text,
        border = BorderStroke(1.dp, Mh4uColors.Border)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = if (enabled) Mh4uColors.Amber else Mh4uColors.TextDisabled, modifier = Modifier.size(25.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp)
                Spacer(Modifier.height(4.dp)); Hint(description)
            }
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Rounded.ChevronRight, null, tint = Mh4uColors.TextDisabled, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable fun SaveFormatDialog(exporting: Boolean, onCancel: () -> Unit, onFolder: () -> Unit, onZip: () -> Unit) {
    SaveModal(if (exporting) "Export saved progress" else "Import saved progress", onCancel) {
        Hint(if (exporting) "Choose how to keep a copy of your last in-game save. Your progress stays in this app."
            else "Choose where your saved progress comes from. You can review it before confirming.")
        SaveOptionCard(if (exporting) "Use in another MH4U app" else "From another MH4U app",
            if (exporting) "Create a save folder for compatible MH4U apps, including Native and Azahar."
            else "Choose a save folder exported by another compatible MH4U app, such as Native or Azahar.",
            Icons.Rounded.FolderOpen, onClick = onFolder)
        SaveOptionCard(if (exporting) "Keep a backup file" else "From a backup file",
            if (exporting) "Create one ZIP file that you can import into this app later."
            else "Choose a backup ZIP previously exported by this app.",
            Icons.Rounded.Archive, onClick = onZip)
        Hint(if (exporting) "Copies saved files only. Save inside MH4U first; unsaved gameplay is not included."
            else "MH4U USA saves only. Import replaces this app's saved progress and restarts the game.")
    }
}
