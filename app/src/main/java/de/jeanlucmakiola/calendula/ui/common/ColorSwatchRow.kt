package de.jeanlucmakiola.calendula.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * A wrapping row of round colour swatches; the one matching [selected] is
 * ringed and checked. Shared by the calendar editor and the event-colour
 * picker so both pick a colour the same way. Swatches render through
 * [pastelize] — the softened colour the app actually paints, not the raw hue.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorSwatchRow(
    colors: List<Int>,
    selected: Int?,
    onSelect: (Int) -> Unit,
    dark: Boolean,
    modifier: Modifier = Modifier,
) {
    FlowRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        colors.forEach { argb ->
            val isSelected = argb == selected
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .padding(vertical = 4.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(pastelize(argb, dark))
                    .then(
                        if (isSelected) {
                            Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                        } else {
                            Modifier
                        },
                    )
                    .clickable { onSelect(argb) },
            ) {
                if (isSelected) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.Black.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

/** Google-Calendar-style palette; ARGB ints for a raw `CALENDAR_COLOR` / `EVENT_COLOR`. */
val CALENDAR_COLOR_PALETTE: List<Int> = listOf(
    0xFFD50000, // red
    0xFFE67C00, // orange
    0xFFF6BF26, // amber
    0xFF33B679, // green
    0xFF0B8043, // dark green
    0xFF039BE5, // blue
    0xFF3F51B5, // indigo
    0xFF8E24AA, // purple
    0xFF616161, // graphite
).map { it.toInt() }
