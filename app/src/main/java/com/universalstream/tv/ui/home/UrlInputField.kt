package com.universalstream.tv.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.universalstream.tv.ui.components.TvSurface
import com.universalstream.tv.ui.components.requestFocusSafely

/**
 * A URL box that behaves well with a remote:
 * - D-pad focus only highlights it (the on-screen keyboard does not pop up while passing by).
 * - OK opens the Android TV keyboard; the keyboard's Go/Done key opens the site.
 * - Typing on a Bluetooth/USB keyboard while it is highlighted starts editing immediately.
 * - D-pad UP/DOWN leave the field even while editing, so focus never gets trapped.
 */
@Composable
fun UrlInputField(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester = remember { FocusRequester() },
    placeholder: String = "Ketik alamat situs atau tautan pendek, mis. bit.ly/abc",
) {
    var editing by remember { mutableStateOf(false) }

    if (editing) {
        val editorFocus = remember { FocusRequester() }
        val keyboard = LocalSoftwareKeyboardController.current
        val focusManager = LocalFocusManager.current
        var hadFocus by remember { mutableStateOf(false) }
        var fieldValue by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
        if (fieldValue.text != value) fieldValue = TextFieldValue(value, TextRange(value.length))

        LaunchedEffect(Unit) {
            editorFocus.requestFocusSafely()
            keyboard?.show()
        }

        OutlinedTextField(
            value = fieldValue,
            onValueChange = {
                fieldValue = it
                onValueChange(it.text)
            },
            modifier = modifier
                .height(64.dp)
                .focusRequester(editorFocus)
                .onFocusChanged { state ->
                    if (state.isFocused) hadFocus = true
                    else if (hadFocus) editing = false
                }
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.DirectionDown -> {
                            keyboard?.hide()
                            focusManager.moveFocus(FocusDirection.Down)
                            true
                        }
                        Key.DirectionUp -> {
                            keyboard?.hide()
                            focusManager.moveFocus(FocusDirection.Up)
                            true
                        }
                        else -> false
                    }
                },
            singleLine = true,
            textStyle = MaterialTheme.typography.titleMedium,
            placeholder = { Text(placeholder, style = MaterialTheme.typography.titleMedium) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Go,
                autoCorrectEnabled = false,
            ),
            keyboardActions = KeyboardActions(
                onGo = {
                    keyboard?.hide()
                    onSubmit()
                },
                onDone = {
                    keyboard?.hide()
                    onSubmit()
                },
            ),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            ),
        )
    } else {
        TvSurface(
            onClick = { editing = true },
            modifier = modifier
                .height(64.dp)
                .focusRequester(focusRequester)
                .onKeyEvent { event ->
                    // A hardware keyboard key while highlighted: start editing with that character.
                    if (event.type != KeyEventType.KeyDown || event.isCtrlPressed || event.isAltPressed) {
                        return@onKeyEvent false
                    }
                    val codePoint = event.utf16CodePoint
                    if (codePoint > 32 && codePoint != 127) {
                        onValueChange(value + String(Character.toChars(codePoint)))
                        editing = true
                        true
                    } else {
                        false
                    }
                },
            shape = RoundedCornerShape(16.dp),
            focusScale = 1.02f,
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(28.dp))
                Text(
                    text = value.ifEmpty { placeholder },
                    style = MaterialTheme.typography.titleMedium,
                    color = if (value.isEmpty()) LocalContentColor.current.copy(alpha = 0.65f) else LocalContentColor.current,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
