package app.orbistv.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object OrbisColors {
    // Direction artistique : noir profond, blanc pour le focus, une seule touche de couleur (Brand).
    val Bg = Color(0xFF000000)
    val Surface = Color(0xFF0E0F12)
    val Surface2 = Color(0xFF1C1E24)
    val Selected = Color(0x1FFFFFFF)
    val Accent = Color(0xFFFFFFFF)
    val Accent2 = Color(0xFFB9BCC4)
    val Brand = Color(0xFF8B6CFF)
    val Light = Color(0xFFF4F4F4)
    val Ink = Color(0xFF171A20)
    val Gold = Color(0xFFFFC857)
    val Text = Color(0xFFF2F3F7)
    val Muted = Color(0xFF9AA0B4)
    val Error = Color(0xFFFF6B6B)
}

@Composable
fun OrbisTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = OrbisColors.Accent,
            onPrimary = Color.Black,
            secondary = OrbisColors.Accent2,
            background = OrbisColors.Bg,
            onBackground = OrbisColors.Text,
            surface = OrbisColors.Surface,
            onSurface = OrbisColors.Text,
            surfaceVariant = OrbisColors.Surface2,
            onSurfaceVariant = OrbisColors.Muted,
            error = OrbisColors.Error,
        ),
        content = content,
    )
}
