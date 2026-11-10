package org.mh4u.port.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun GameSettingsPanel(enabled: Boolean, onResume: () -> Unit, onSecondScreen: () -> Unit,
    onExit: () -> Unit, content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .20f)).clickable(enabled = enabled, onClick = onResume))
        Surface(modifier = Modifier.align(Alignment.CenterEnd).width((maxWidth * .68f).coerceAtMost(560.dp)).fillMaxHeight(),
            shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
            color = Mh4uColors.WindowBg, contentColor = Mh4uColors.Text) {
            Column {
                Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp).heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Game menu", fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("Game running", color = Mh4uColors.TextDisabled, fontSize = 10.sp)
                    }
                    IconButton(enabled = enabled, onClick = onSecondScreen) { Icon(Icons.Rounded.PictureInPictureAlt, "Enlarge 2nd screen") }
                    TextButton(enabled = enabled, onClick = onExit) { Text("Exit game", fontSize = 12.sp) }
                    FilledTonalButton(enabled = enabled, onClick = onResume, contentPadding = PaddingValues(horizontal = 12.dp)) { Text("Resume", fontSize = 12.sp) }
                }
                HorizontalDivider(color = Mh4uColors.Border)
                Box(Modifier.weight(1f)) { content() }
            }
        }
    }
}
