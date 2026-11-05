package dev.koshik.lrcembedder.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

val Violet = Color(0xFF8B5CFF)
val Pink = Color(0xFFFF5FA2)
val Teal = Color(0xFF3DE8D0)
val Amber = Color(0xFFFFC857)
val Ink = Color(0xFF0F0B1E)

enum class ThemeMode(val label: String) {
    SYSTEM("System"),
    DARK("Dark"),
    LIGHT("Light")
}

/** Three accent colours; backgrounds, orbs and the Material scheme are all derived from them. */
enum class Palette(val label: String, val a: Color, val b: Color, val c: Color) {
    AURORA("Aurora", Color(0xFF8B5CFF), Color(0xFFFF5FA2), Color(0xFF3DE8D0)),
    SUNSET("Sunset", Color(0xFFFF8A5B), Color(0xFFFF5F8F), Color(0xFFB36BFF)),
    OCEAN("Ocean", Color(0xFF4C8DFF), Color(0xFF2FD3E6), Color(0xFF6AE3B0)),
    MINT("Mint", Color(0xFF34D399), Color(0xFF2DD4BF), Color(0xFF86EFAC)),
    ROSE("Rose", Color(0xFFFF7EB6), Color(0xFFFFA1A1), Color(0xFFD59BFF)),
    DUSK("Dusk", Color(0xFF7C83FF), Color(0xFFB48CFF), Color(0xFF8FD3FF))
}

private fun buildScheme(dark: Boolean, p: Palette): ColorScheme =
    if (dark) {
        darkColorScheme(
            primary = lerp(p.a, Color.White, 0.45f),
            onPrimary = lerp(p.a, Color.Black, 0.75f),
            primaryContainer = lerp(p.a, Color.Black, 0.45f),
            onPrimaryContainer = lerp(p.a, Color.White, 0.8f),
            secondary = lerp(p.b, Color.White, 0.4f),
            tertiary = lerp(p.c, Color.White, 0.35f),
            background = Ink,
            onBackground = Color(0xFFEDE8FF),
            surface = lerp(Color(0xFF17122B), p.a, 0.06f),
            onSurface = Color(0xFFEDE8FF),
            surfaceVariant = lerp(Color(0xFF17122B), p.a, 0.18f),
            onSurfaceVariant = Color(0xFFCBC3E8),
            outline = Color(0xFF8D84B3),
            error = Color(0xFFFF8A9A)
        )
    } else {
        lightColorScheme(
            primary = lerp(p.a, Color.Black, 0.25f),
            onPrimary = Color.White,
            primaryContainer = lerp(p.a, Color.White, 0.75f),
            onPrimaryContainer = lerp(p.a, Color.Black, 0.6f),
            secondary = lerp(p.b, Color.Black, 0.2f),
            tertiary = lerp(p.c, Color.Black, 0.4f),
            background = Color(0xFFFAF8FF),
            onBackground = Color(0xFF1D1830),
            surface = lerp(Color.White, p.a, 0.05f),
            onSurface = Color(0xFF1D1830),
            surfaceVariant = lerp(Color.White, p.a, 0.14f),
            onSurfaceVariant = Color(0xFF5B5478),
            outline = Color(0xFF857E9F),
            error = Color(0xFFB3261E)
        )
    }

@Composable
fun LrcTheme(dark: Boolean, palette: Palette, content: @Composable () -> Unit) {
    val scheme = remember(dark, palette) { buildScheme(dark, palette) }
    MaterialTheme(colorScheme = scheme) {
        // text outside a Surface would otherwise default to black
        CompositionLocalProvider(LocalContentColor provides scheme.onSurface) {
            content()
        }
    }
}
