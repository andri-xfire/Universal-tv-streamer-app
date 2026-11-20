package com.universalstream.tv.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.universalstream.tv.ui.components.LocalReduceMotion

private val DarkColors = darkColorScheme(
    primary = Color(0xFF6CC7FF),
    onPrimary = Color(0xFF00243A),
    primaryContainer = Color(0xFF0E4A6E),
    onPrimaryContainer = Color(0xFFD5ECFF),
    secondary = Color(0xFFFFB86B),
    onSecondary = Color(0xFF3A2000),
    secondaryContainer = Color(0xFF5A3A10),
    onSecondaryContainer = Color(0xFFFFE2C2),
    background = Color(0xFF0A0E15),
    onBackground = Color(0xFFF1F4F9),
    surface = Color(0xFF121822),
    onSurface = Color(0xFFF1F4F9),
    surfaceVariant = Color(0xFF1E2633),
    onSurfaceVariant = Color(0xFFBFC8D6),
    outline = Color(0xFF4A5568),
    error = Color(0xFFFF7A7A),
    onError = Color(0xFF3A0000),
    errorContainer = Color(0xFF5C1A1A),
    onErrorContainer = Color(0xFFFFDADA),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF00629B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDE5FF),
    onPrimaryContainer = Color(0xFF001D33),
    secondary = Color(0xFF8A5100),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDDB8),
    onSecondaryContainer = Color(0xFF2C1600),
    background = Color(0xFFF4F6FA),
    onBackground = Color(0xFF111418),
    surface = Color.White,
    onSurface = Color(0xFF111418),
    surfaceVariant = Color(0xFFE1E6EE),
    onSurfaceVariant = Color(0xFF3F4856),
    outline = Color(0xFF7A8494),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
)

/** Type scale sized for a ~960x540 dp TV canvas viewed from 2–3 metres. */
private val TvTypography = Typography(
    displaySmall = TextStyle(fontSize = 40.sp, lineHeight = 48.sp, fontWeight = FontWeight.SemiBold),
    headlineMedium = TextStyle(fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.SemiBold),
    headlineSmall = TextStyle(fontSize = 27.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 24.sp, lineHeight = 31.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 21.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium),
    titleSmall = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 20.sp, lineHeight = 28.sp),
    bodyMedium = TextStyle(fontSize = 18.sp, lineHeight = 25.sp),
    bodySmall = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
    labelLarge = TextStyle(fontSize = 19.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
)

/** TV overscan-safe margins (5% of a 960x540 dp canvas). */
object TvSafeArea {
    val horizontal = 48.dp
    val vertical = 27.dp
}

@Composable
fun UniversalStreamTheme(
    darkTheme: Boolean,
    reduceMotion: Boolean,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = TvTypography,
    ) {
        CompositionLocalProvider(LocalReduceMotion provides reduceMotion, content = content)
    }
}
