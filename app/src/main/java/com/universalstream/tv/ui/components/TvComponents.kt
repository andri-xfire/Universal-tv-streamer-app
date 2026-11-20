package com.universalstream.tv.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay

/** True on low-memory devices: focus scaling is applied without animation. */
val LocalReduceMotion = staticCompositionLocalOf { false }

/**
 * The basic focusable TV element. Focus is shown three ways at once (colour, 3 dp outline, slight
 * scale) so it is obvious from the sofa. OK/ENTER clicks; long-press OK or MENU triggers [onLongClick].
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TvSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(14.dp),
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    focusedContainerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    focusedContentColor: Color = MaterialTheme.colorScheme.onPrimary,
    focusScale: Float = 1.05f,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val reduceMotion = LocalReduceMotion.current
    val targetScale = if (focused) focusScale else 1f
    val scale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = tween(durationMillis = if (reduceMotion) 0 else 110),
        label = "tvFocusScale",
    )
    val background = when {
        !enabled -> containerColor.copy(alpha = 0.45f)
        focused -> focusedContainerColor
        else -> containerColor
    }
    val foreground = when {
        !enabled -> contentColor.copy(alpha = 0.45f)
        focused -> focusedContentColor
        else -> contentColor
    }
    val outline = MaterialTheme.colorScheme.onBackground
    val glowColor = MaterialTheme.colorScheme.primary
    val longClickKeys = if (onLongClick != null) {
        Modifier.onPreviewKeyEvent { event ->
            if (event.key == Key.Menu) {
                if (event.type == KeyEventType.KeyUp) onLongClick()
                true
            } else {
                false
            }
        }
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(
                if (focused) {
                    Modifier.shadow(14.dp, shape, ambientColor = glowColor, spotColor = glowColor)
                } else {
                    Modifier
                },
            )
            .clip(shape)
            .background(background, shape)
            .then(if (focused) Modifier.border(3.dp, outline, shape) else Modifier)
            .then(longClickKeys)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onLongClick = onLongClick,
                onClick = onClick,
            ),
        contentAlignment = contentAlignment,
    ) {
        CompositionLocalProvider(LocalContentColor provides foreground) {
            content(focused)
        }
    }
}

@Composable
fun TvButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    primary: Boolean = false,
    enabled: Boolean = true,
) {
    TvSurface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 56.dp),
        enabled = enabled,
        shape = RoundedCornerShape(28.dp),
        containerColor = if (primary) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (primary) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(26.dp))
            Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}

/** Compact icon button for the browser toolbar; its label appears while focused. */
@Composable
fun TvIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    active: Boolean = false,
) {
    TvSurface(
        onClick = onClick,
        modifier = modifier.height(56.dp).widthIn(min = 56.dp),
        enabled = enabled,
        shape = RoundedCornerShape(28.dp),
        containerColor = if (active) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (active) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
    ) { focused ->
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(28.dp))
            if (focused) {
                Spacer(Modifier.width(8.dp))
                Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
            }
        }
    }
}

/** A large, D-pad friendly row used in lists and option dialogs. */
@Composable
fun TvListRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leadingIcon: ImageVector? = null,
    trailing: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
) {
    TvSurface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = 72.dp),
        enabled = enabled,
        focusScale = 1.02f,
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (leadingIcon != null) Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(28.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalContentColor.current.copy(alpha = 0.8f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            trailing?.invoke()
        }
    }
}

/** Modal dialog sized for TV; BACK dismisses it. */
@Composable
fun TvDialog(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    buttons: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 6.dp,
            modifier = modifier.widthIn(min = 480.dp, max = 760.dp),
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(32.dp))
                    Text(title, style = MaterialTheme.typography.headlineSmall)
                }
                content()
                if (buttons != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                        content = buttons,
                    )
                }
            }
        }
    }
}

/** Requests focus once the target is attached (dialogs compose in their own window a frame later). */
suspend fun FocusRequester.requestFocusSafely() {
    repeat(10) {
        delay(if (it == 0) 16L else 50L)
        val ok = runCatching { requestFocus() }.isSuccess
        if (ok) return
    }
}
