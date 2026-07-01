package de.jeanlucmakiola.calendula.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * The app's borderless text input: no underline, no outline, just the tonal
 * card behind it. This is the standard input across the app — we deliberately
 * don't use Material's outlined/filled text fields, so anything that takes text
 * (the event form, the calendar manager, dialogs) uses this inside a tonal
 * [androidx.compose.material3.Surface].
 */
@Composable
fun InlineTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 4.dp),
    textStyle: TextStyle = MaterialTheme.typography.titleMedium,
    singleLine: Boolean = true,
    minLines: Int = 1,
    enabled: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    imeAction: ImeAction = ImeAction.Default,
    /** Invoked when the IME action key (e.g. Done) is pressed. */
    onImeAction: (() -> Unit)? = null,
) {
    val resolvedStyle = textStyle.copy(
        color = when {
            // A disabled field reads as dimmed, like a locked value.
            !enabled -> MaterialTheme.colorScheme.onSurfaceVariant
            textStyle.color.isSpecified -> textStyle.color
            else -> MaterialTheme.colorScheme.onSurface
        },
    )
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        textStyle = resolvedStyle,
        singleLine = singleLine,
        minLines = minLines,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            capitalization = capitalization,
            imeAction = imeAction,
        ),
        keyboardActions = onImeAction?.let { action ->
            KeyboardActions(onAny = { action() })
        } ?: KeyboardActions.Default,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        decorationBox = { innerTextField ->
            Box {
                if (value.isEmpty()) {
                    // Clearly fainter than typed text, so a hint never reads as
                    // prefilled content.
                    Text(
                        text = placeholder,
                        style = resolvedStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                }
                innerTextField()
            }
        },
        modifier = modifier,
    )
}
