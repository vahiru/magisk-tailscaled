package io.github.vahiru.tailscaled.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Fallback palette (Android 8-11, no dynamic color); Material 3 baseline tones of #3D5A80.
private val Light = lightColorScheme(
    primary = Color(0xFF3A5F8F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD3E4FF),
    onPrimaryContainer = Color(0xFF001C38),
    secondary = Color(0xFF545F71),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD8E3F8),
    onSecondaryContainer = Color(0xFF111C2B),
    tertiary = Color(0xFF3B6939),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFBCF0B4),
    onTertiaryContainer = Color(0xFF002204),
    background = Color(0xFFF8F9FF),
    onBackground = Color(0xFF191C20),
    surface = Color(0xFFF8F9FF),
    onSurface = Color(0xFF191C20),
    surfaceVariant = Color(0xFFDFE2EB),
    onSurfaceVariant = Color(0xFF43474E),
    outline = Color(0xFF73777F),
    outlineVariant = Color(0xFFC3C6CF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF2F3FA),
    surfaceContainer = Color(0xFFECEEF4),
    surfaceContainerHigh = Color(0xFFE6E8EE),
    surfaceContainerHighest = Color(0xFFE1E2E8),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFA4C9FE),
    onPrimary = Color(0xFF00315C),
    primaryContainer = Color(0xFF1F4876),
    onPrimaryContainer = Color(0xFFD3E4FF),
    secondary = Color(0xFFBCC7DC),
    onSecondary = Color(0xFF263141),
    secondaryContainer = Color(0xFF3C4758),
    onSecondaryContainer = Color(0xFFD8E3F8),
    tertiary = Color(0xFFA1D39A),
    onTertiary = Color(0xFF0A390F),
    tertiaryContainer = Color(0xFF235024),
    onTertiaryContainer = Color(0xFFBCF0B4),
    background = Color(0xFF111318),
    onBackground = Color(0xFFE1E2E8),
    surface = Color(0xFF111318),
    onSurface = Color(0xFFE1E2E8),
    surfaceVariant = Color(0xFF43474E),
    onSurfaceVariant = Color(0xFFC3C6CF),
    outline = Color(0xFF8D9199),
    outlineVariant = Color(0xFF43474E),
    surfaceContainerLowest = Color(0xFF0C0E13),
    surfaceContainerLow = Color(0xFF191C20),
    surfaceContainer = Color(0xFF1D2024),
    surfaceContainerHigh = Color(0xFF282A2F),
    surfaceContainerHighest = Color(0xFF32353A),
)

@Composable
fun TailscaledTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        darkTheme -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = colors, content = content)
}
