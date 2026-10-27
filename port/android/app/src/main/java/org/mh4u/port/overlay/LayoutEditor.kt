package org.mh4u.port.overlay

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.mh4u.port.Prefs
import org.mh4u.port.ui.*
import kotlin.math.roundToInt

/** Only the toolbar's handle consumes dragging, leaving the rest of the game editable. */
@OptIn(ExperimentalLayoutApi::class)
@Composable fun LayoutEditor(
    layout: OverlayLayout, selected: Control?, onSelected: (Control?) -> Unit,
    changed: () -> Unit, onSave: () -> Unit, onCancel: () -> Unit, portrait: Boolean,
    checkpoint: () -> Unit, canUndo: Boolean, onUndo: () -> Unit,
) {
    var collapsed by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    var reset by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var mask by remember { mutableIntStateOf(0) }
    var latch by remember { mutableStateOf(false) }
    var panel by remember { mutableStateOf(IntSize.Zero) }
    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().padding(8.dp)) {
        val maxX = (constraints.maxWidth - panel.width).coerceAtLeast(0).toFloat()
        val maxY = (constraints.maxHeight - panel.height).coerceAtLeast(0).toFloat()
        var x by remember { mutableFloatStateOf(Prefs.getInt("Android", "editor_x", 0) / 100f) }
        var y by remember { mutableFloatStateOf(Prefs.getInt("Android", "editor_y", 0) / 100f) }
        Surface(
            modifier = Modifier.offset { IntOffset((x * maxX).roundToInt(), (y * maxY).roundToInt()) }
                .widthIn(max = 330.dp).width(maxWidth.coerceAtMost(330.dp)).heightIn(max = maxHeight)
                .onSizeChanged { panel = it },
            color = Mh4uColors.PopupBg.copy(alpha = .96f), shape = MaterialTheme.shapes.large,
            contentColor = Mh4uColors.Text,
            border = androidx.compose.foundation.BorderStroke(1.dp, Mh4uColors.Border),
        ) {
            Column {
                Row(Modifier.fillMaxWidth().pointerInput(maxX, maxY) {
                    detectDragGestures(onDragEnd = {
                        Prefs.setApp("Android", "editor_x", (x * 100).roundToInt().toString())
                        Prefs.setApp("Android", "editor_y", (y * 100).roundToInt().toString())
                    }) { change, amount ->
                        change.consume()
                        if (maxX > 0) x = (x + amount.x / maxX).coerceIn(0f, 1f)
                        if (maxY > 0) y = (y + amount.y / maxY).coerceIn(0f, 1f)
                    }
                }.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.DragIndicator, "Drag toolbar", tint = Mh4uColors.Text)
                    Text("Layout editor", Modifier.weight(1f).padding(start = 8.dp), color = Mh4uColors.Text, fontSize = 14.sp)
                    IconButton(onClick = { collapsed = !collapsed }) { Icon(if (collapsed) Icons.Rounded.ExpandMore else Icons.Rounded.ExpandLess, if (collapsed) "Expand toolbar" else "Collapse toolbar") }
                    if (collapsed) IconButton(onClick = onSave) { Icon(Icons.Rounded.Check, "Save layout", tint = Mh4uColors.Text) }
                }
                if (!collapsed) Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 14.dp).padding(bottom = 10.dp)) {
                    Hint(selected?.let { "Selected: ${it.label.ifEmpty { it.kind.name.lowercase().replace('_', ' ') }}" } ?: "Drag a button. Pinch to resize it.")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        QuietButton("−", enabled = selected != null) { selected?.let { checkpoint(); it.size = (it.size * .9f).coerceAtLeast(.04f); changed() } }
                        QuietButton("+", enabled = selected != null) { selected?.let { checkpoint(); it.size = (it.size * 1.1f).coerceAtMost(.5f); changed() } }
                        QuietButton(if (selected?.visible == false) "Show" else "Hide", enabled = selected != null && selected?.kind != Control.Kind.MENU) { selected?.let { checkpoint(); it.visible = !it.visible; changed() } }
                        if (selected?.kind == Control.Kind.COMBO) QuietButton("Delete combo") { checkpoint(); layout.controls.remove(selected); onSelected(null); changed() }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        QuietButton("Add combo") { adding = true }
                        QuietButton("Reset") { reset = true }
                        QuietButton("Undo", enabled = canUndo, onClick = onUndo)
                    }
                    RowDivider()
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        QuietButton("Cancel", Modifier.weight(1f)) { onCancel() }
                        AmberButton("Save & play", Modifier.weight(1f)) { onSave() }
                    }
                    Hint("Back saves your layout. Cancel discards this edit.")
                }
            }
        }
    }
    if (reset) AlertDialog(onDismissRequest = { reset = false }, title = { Text("Reset button layout?") }, text = { Text("Restore the default positions and remove custom combo buttons in this layout.") }, confirmButton = { TextButton(onClick = { checkpoint(); layout.controls.clear(); layout.controls.addAll(OverlayLayout.defaults(portrait).controls); onSelected(null); changed(); reset = false }) { Text("Reset") } }, dismissButton = { TextButton(onClick = { reset = false }) { Text("Keep layout") } })
    if (adding) AlertDialog(onDismissRequest = { adding = false }, title = { Text("Create combo button") }, text = {
        Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
            TextRow("Button name", null, name) { name = it.take(12) }
            Hint("Choose the buttons this one touch will press.")
            FlowChips(mask) { mask = it }
            ToggleRow("Keep pressed", if (latch) "On: tap to hold the buttons; tap again to release." else "Off: hold with your finger; lift to release.", latch) { latch = it }
        }
    }, confirmButton = { TextButton(enabled = mask != 0 && name.isNotBlank(), onClick = { checkpoint(); onSelected(layout.addCombo(name.trim(), mask, latch)); changed(); adding = false; name = ""; mask = 0; latch = false }) { Text("Create") } }, dismissButton = { TextButton(onClick = { adding = false }) { Text("Cancel") } })
}
