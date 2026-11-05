package dev.koshik.lrcembedder.ui

import android.graphics.Color as AndroidColor
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import dev.koshik.lrcembedder.ui.theme.LrcTheme
import dev.koshik.lrcembedder.ui.theme.ThemeMode

@Composable
fun LrcApp(vm: MainViewModel) {
    val dark = when (vm.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }

    // keep status/navigation bar icons readable in both themes
    val activity = LocalContext.current as? ComponentActivity
    DisposableEffect(dark) {
        val style = if (dark) {
            SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        } else {
            SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
        }
        activity?.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        onDispose { }
    }

    LrcTheme(dark = dark, palette = vm.palette) {
        AnimatedGradientBackground(palette = vm.palette, dark = dark, animated = vm.animatedBg) {
            if (vm.hasAccess) HomeScreen(vm) else PermissionScreen(vm)
        }
    }
}
