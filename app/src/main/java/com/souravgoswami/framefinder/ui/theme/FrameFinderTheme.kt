package com.souravgoswami.framefinder.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.souravgoswami.framefinder.data.ThemeMode

private val Accent = ComposeColor(0xFF6A94FF)

@Composable
fun FrameFinderTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK_GRAY, ThemeMode.OLED -> true
        ThemeMode.LIGHT -> false
    }
    val colors = when {
        mode == ThemeMode.OLED -> darkColorScheme(
            primary = Accent,
            onPrimary = ComposeColor.White,
            background = ComposeColor.Black,
            onBackground = ComposeColor(0xFFF5F7FF),
            surface = ComposeColor(0xFF101010),
            onSurface = ComposeColor(0xFFF5F7FF),
            surfaceVariant = ComposeColor(0xFF181818),
            onSurfaceVariant = ComposeColor(0xFFB8BBC6),
            outline = ComposeColor(0xFF343434)
        )
        dark -> darkColorScheme(
            primary = Accent,
            onPrimary = ComposeColor.White,
            background = ComposeColor(0xFF111827),
            onBackground = ComposeColor(0xFFF1F5FF),
            surface = ComposeColor(0xFF1E293B),
            onSurface = ComposeColor(0xFFF1F5FF),
            surfaceVariant = ComposeColor(0xFF263449),
            onSurfaceVariant = ComposeColor(0xFFB9C2D3),
            outline = ComposeColor(0xFF34435B)
        )
        else -> lightColorScheme(
            primary = Accent,
            onPrimary = ComposeColor.White,
            background = ComposeColor(0xFFFAFAFA),
            onBackground = ComposeColor(0xFF171717),
            surface = ComposeColor.White,
            onSurface = ComposeColor(0xFF171717),
            surfaceVariant = ComposeColor(0xFFF2F3F5),
            onSurfaceVariant = ComposeColor(0xFF666A73),
            outline = ComposeColor(0xFFD0D2D7)
        )
    }
    val view = LocalView.current
    SideEffect {
        val activity = view.context.findActivity() ?: return@SideEffect
        activity.window.statusBarColor = Color.TRANSPARENT
        WindowCompat.getInsetsController(activity.window, view).isAppearanceLightStatusBars = !dark
    }
    MaterialTheme(
        colorScheme = colors,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(6.dp),
            small = RoundedCornerShape(8.dp),
            medium = RoundedCornerShape(8.dp),
            large = RoundedCornerShape(12.dp),
            extraLarge = RoundedCornerShape(16.dp)
        ),
        content = content
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
