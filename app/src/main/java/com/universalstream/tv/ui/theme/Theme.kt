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

// A bold, saturated palette: electric blue + hot pink + vivid purple on a deep violet base.
private val DarkColors = darkColorScheme(
    primary = Color(0xFF4F8CFF),
    onPrimary = Color(0xFF041A42),
    primaryContainer = Color(0xFF2E57D8),
    onPrimaryContainer = Color(0xFFEAF1FF),
    secondary = Color(0xFFFF5C9D),
    onSecondary = Color(0xFF42071F),
    secondaryContainer = Color(0xFFC03573),
    onSecondaryContainer = Color(0xFFFFE1EC),
    tertiary = Color(0xFFB174FF),
    onTertiary = Color(0xFF270A52),
    tertiaryContainer = Color(0xFF6A38C4),
    onTertiaryContainer = Color(0xFFF1E6FF),
    background = Color(0xFF120A2A),
    onBackground = Color(0xFFF3EFFC),
    surface = Color(0xFF1C1547),
    onSurface = Color(0xFFF3EFFC),
    surfaceVariant = Color(0xFF2A1F5C),
    onSurfaceVariant = Color(0xFFCDC4EC),
    outline = Color(0xFF53468C),
    error = Color(0xFFFF7A95),
    onError = Color(0xFF42000D),
    errorContainer = Color(0xFF8E1538),
    onErrorContainer = Color(0xFFFFDAE1),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF2F5BEA),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE4FF),
    onPrimaryContainer = Color(0xFF001555),
    secondary = Color(0xFFE23E7B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFD9E4),
    onSecondaryContainer = Color(0xFF3E0421),
    tertiary = Color(0xFF7A3FD6),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFEBDDFF),
    onTertiaryContainer = Color(0xFF270A5A),
    background = Color(0xFFF4F2FC),
    onBackground = Color(0xFF151227),
    surface = Color.White,
    onSurface = Color(0xFF151227),
    surfaceVariant = Color(0xFFE8E3F7),
    onSurfaceVariant = Color(0xFF453F62),
    outline = Color(0xFF897FB2),
    error = Color(0xFFC0143E),
    onError = Color.White,
    errorContainer = Color(0xFFFFD9DF),
    onErrorContainer = Color(0xFF40000F),
)

/** Type scale for a ~960x540 dp TV canvas, trimmed down so text feels lighter and less bulky. */
private val TvTypography = Typography(
    displaySmall = TextStyle(fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.Medium),
    titleSmall = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
)

/** TV overscan-safe margins (5% of a 960x540 dp canvas). */
object TvSafeArea {
    val horizontal = 48.dp
    val vertical = 27.dp
}

/** Bold, saturated gradients so the screen reads as a colourful, eye-catching surface. */
object TvGradients {
    /** Full-screen background: a vivid purple → blue → magenta diagonal wash. */
    @Composable
    fun appBackground(): Brush {
        val scheme = MaterialTheme.colorScheme
        return Brush.linearGradient(
            0.0f to lerp(scheme.background, scheme.tertiary, 0.40f),
            0.50f to scheme.background,
            1.0f to lerp(scheme.background, scheme.primary, 0.38f),
        )
    }

    /** The raised "hero" panel that holds the search controls — a rich blue → violet blend. */
    @Composable
    fun heroPanel(): Brush {
        val scheme = MaterialTheme.colorScheme
        return Brush.linearGradient(
            listOf(
                lerp(scheme.surface, scheme.primary, 0.30f),
                lerp(scheme.surface, scheme.tertiary, 0.26f),
            ),
        )
    }

    /** A vivid accent gradient used on primary buttons, the logo badge and the selected chip. */
    @Composable
    fun accent(): Brush {
        val scheme = MaterialTheme.colorScheme
        return Brush.linearGradient(
            listOf(scheme.primary, scheme.tertiary, scheme.secondary),
        )
    }

    /** A gentle per-card tint so the cards look colourful without hurting text contrast. */
    @Composable
    fun card(): Brush {
        val scheme = MaterialTheme.colorScheme
        return Brush.linearGradient(
            listOf(
                lerp(scheme.surface, scheme.primary, 0.20f),
                lerp(scheme.surface, scheme.secondary, 0.16f),
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
