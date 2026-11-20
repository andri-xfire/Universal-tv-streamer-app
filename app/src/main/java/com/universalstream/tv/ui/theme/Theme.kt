package com.universalstream.tv.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.universalstream.tv.ui.components.LocalReduceMotion

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8AB4FF),
    onPrimary = Color(0xFF07254A),
    primaryContainer = Color(0xFF1E4C86),
    onPrimaryContainer = Color(0xFFDCE9FF),
    secondary = Color(0xFFFFB38C),
    onSecondary = Color(0xFF3A1D08),
    secondaryContainer = Color(0xFF6B3A24),
    onSecondaryContainer = Color(0xFFFFE2D2),
    tertiary = Color(0xFFC7A9FF),
    onTertiary = Color(0xFF2A1A52),
    tertiaryContainer = Color(0xFF433072),
    onTertiaryContainer = Color(0xFFEADDFF),
    background = Color(0xFF0A0F1E),
    onBackground = Color(0xFFEDF1F9),
    surface = Color(0xFF141C2E),
    onSurface = Color(0xFFEDF1F9),
    surfaceVariant = Color(0xFF1F2A42),
    onSurfaceVariant = Color(0xFFC4CEE2),
    outline = Color(0xFF3B4A68),
    error = Color(0xFFFF8A8A),
    onError = Color(0xFF3A0000),
    errorContainer = Color(0xFF5C1A1A),
    onErrorContainer = Color(0xFFFFDADA),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF2B5BD7),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDBE6FF),
    onPrimaryContainer = Color(0xFF001A40),
    secondary = Color(0xFFB5531F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDBC9),
    onSecondaryContainer = Color(0xFF341200),
    tertiary = Color(0xFF6B4EA8),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFEADDFF),
    onTertiaryContainer = Color(0xFF24104F),
    background = Color(0xFFF1F4FB),
    onBackground = Color(0xFF111622),
    surface = Color.White,
    onSurface = Color(0xFF111622),
    surfaceVariant = Color(0xFFE2E8F4),
    onSurfaceVariant = Color(0xFF3E4759),
    outline = Color(0xFF8793A8),
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

/** Soft, depth-giving gradients so screens don't read as one flat block of colour. */
object TvGradients {
    /** Full-screen background: a gentle primary-tinted top fading into the deep base colour. */
    @Composable
    fun appBackground(): Brush {
        val scheme = MaterialTheme.colorScheme
        return Brush.verticalGradient(
            0.0f to lerp(scheme.background, scheme.primary, 0.12f),
            0.45f to scheme.background,
            1.0f to lerp(scheme.background, scheme.tertiary, 0.08f),
        )
    }

    /** The raised "hero" panel that holds the search controls. */
    @Composable
    fun heroPanel(): Brush {
        val scheme = MaterialTheme.colorScheme
        return Brush.linearGradient(
            listOf(
                lerp(scheme.surface, scheme.primary, 0.10f),
                lerp(scheme.surface, scheme.tertiary, 0.06f),
            ),
        )
    }
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
