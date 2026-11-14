package io.github.staninna.warosso.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import io.github.staninna.warosso.data.ThemeMode

/**
 * Counts the screens that currently draw a camera image behind the status bar. While there is
 * one, the status bar icons are white whatever the theme. A count, because two such screens
 * overlap for a moment during a transition.
 */
class CameraBackdrops {
    var count by mutableIntStateOf(0)
}

val LocalCameraBackdrops = staticCompositionLocalOf<CameraBackdrops> { error("No WarOssoTheme above") }

/** Material 3 Expressive on the wallpaper's dynamic colours, with the bundled rounded typeface. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WarOssoTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val colors = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

    val cameraBackdrops = remember { CameraBackdrops() }
    val darkIcons = !dark && cameraBackdrops.count == 0
    val view = LocalView.current
    SideEffect {
        WindowCompat.getInsetsController((view.context as Activity).window, view).run {
            isAppearanceLightStatusBars = darkIcons
            isAppearanceLightNavigationBars = !dark
        }
    }

    CompositionLocalProvider(LocalCameraBackdrops provides cameraBackdrops) {
        MaterialExpressiveTheme(
            colorScheme = colors,
            motionScheme = MotionScheme.expressive(),
            typography = WarOssoTypography,
            content = content,
        )
    }
}
