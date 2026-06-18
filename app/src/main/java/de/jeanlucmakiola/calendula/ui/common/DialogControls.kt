package de.jeanlucmakiola.calendula.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * Tonal 3-digit number input shared by the custom reminder/recurrence steps and
 * the reminder pickers — the app's [InlineTextField] over a tonal surface, so it
 * matches the card/grouped-row design language (not Material's outlined field).
 */
@Composable
fun DialogAmountField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
) {
    // surfaceContainerHighest — the picker/dialog sits on surfaceContainerHigh,
    // so anything lower vanishes.
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(12.dp),
    ) {
        InlineTextField(
            value = value,
            onValueChange = { text ->
                if (text.length <= 3 && text.all(Char::isDigit)) onValueChange(text)
            },
            placeholder = placeholder,
            textStyle = MaterialTheme.typography.titleMedium,
            keyboardType = KeyboardType.Number,
            modifier = Modifier
                .width(72.dp)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        )
    }
}

/** Tonal dropdown trigger + menu shared by the custom reminder/recurrence steps and pickers. */
@Composable
fun DialogUnitDropdown(
    label: String,
    entries: List<String>,
    onPick: (Int) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            shape = RoundedCornerShape(12.dp),
            onClick = { open = true },
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            ) {
                Text(text = label, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(4.dp))
                Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null)
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            entries.forEachIndexed { index, entry ->
                DropdownMenuItem(
                    text = { Text(entry) },
                    onClick = {
                        onPick(index)
                        open = false
                    },
                )
            }
        }
    }
}
