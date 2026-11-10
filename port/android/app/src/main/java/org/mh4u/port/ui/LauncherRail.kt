package org.mh4u.port.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LauncherRail(screen: String, enabled: Boolean, onSelect: (String) -> Unit) {
    Surface(color = Mh4uColors.TitleBg, modifier = Modifier.width(72.dp).fillMaxHeight()) {
        Column(Modifier.padding(horizontal = 4.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(Triple("home", "Home", Icons.Rounded.Home), Triple("settings", "Settings", Icons.Rounded.Settings), Triple("about", "About", Icons.Rounded.Info)).forEach { (id, label, icon) ->
                if (id == "about") Spacer(Modifier.weight(1f))
                val active = screen == id
                Surface(onClick = { onSelect(id) }, enabled = enabled,
                    shape = RoundedCornerShape(12.dp),
                    color = if (active) Mh4uColors.TabSelected else Mh4uColors.TitleBg,
                    contentColor = if (active) Mh4uColors.AmberBright else Mh4uColors.TextDisabled,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).semantics { selected = active }) {
                    Column(Modifier.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Icon(icon, null, Modifier.size(22.dp))
                        Text(label, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
