package org.mh4u.port.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Dark surfaces, white text and jade accents for the Android interface. */
object Mh4uColors {
    val Amber = Color(0xFF65D9C0)
    val AmberBright = Color(0xFFA0F1DE)
    val Text = Color.White
    val TextDisabled = Color(0xFF9EB0B6)
    val WindowBg = Color(0xFF0C151C)
    val PopupBg = Color(0xFF14232D)
    val FrameBg = Color(0xFF213640)
    val FrameBgHovered = Color(0xFF234D50)
    val FrameBgActive = Color(0xFF2A6260)
    val TitleBg = Color(0xFF101D25)
    val TitleBgActive = Color(0xFF1A363C)
    val Button = Color(0xFF22383F)
    val ButtonHovered = Color(0xFF24564F)
    val ButtonActive = Color(0xFF307569)
    val Header = Color(0xFF234A48)
    val Tab = Color(0xFF172A33)
    val TabHovered = Color(0xFF286055)
    val TabSelected = Color(0xFF214C47)
    val Notice = Color(0xFFFFBF73)
    val Border = Amber.copy(alpha = 0.30f)
    val Error = Color(0xFFE5645A)
}

private val scheme = darkColorScheme(
    primary = Mh4uColors.Amber,
    onPrimary = Color(0xFF1A1204),
    primaryContainer = Mh4uColors.TabSelected,
    onPrimaryContainer = Mh4uColors.Text,
    secondary = Mh4uColors.ButtonHovered,
    onSecondary = Mh4uColors.Text,
    secondaryContainer = Mh4uColors.Button,
    onSecondaryContainer = Mh4uColors.Text,
    tertiary = Mh4uColors.AmberBright,
    background = Mh4uColors.WindowBg,
    onBackground = Mh4uColors.Text,
    surface = Mh4uColors.WindowBg,
    onSurface = Mh4uColors.Text,
    surfaceVariant = Mh4uColors.FrameBg,
    onSurfaceVariant = Mh4uColors.TextDisabled,
    surfaceContainer = Mh4uColors.PopupBg,
    surfaceContainerHigh = Mh4uColors.TitleBg,
    surfaceContainerHighest = Mh4uColors.TitleBgActive,
    outline = Mh4uColors.Border,
    outlineVariant = Mh4uColors.Border,
    error = Mh4uColors.Error,
)

@Composable
fun Mh4uTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
