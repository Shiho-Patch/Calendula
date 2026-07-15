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
import androidx.compose.ui.unit.dp

/**
 * A wrapping row of round colour swatches; the one matching [selected] is
 * ringed and checked. Shared by the calendar editor and the event-colour
 * picker so both pick a colour the same way. Swatches render through
 * [eventFill] — the colour the app actually paints (softened, or raw when the
 * softener is off, issue #36), not necessarily the stored hue.
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
    val soften = LocalSoftenColors.current
    FlowRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        colors.forEach { argb ->
            val isSelected = argb == selected
            val fill = eventFill(argb, dark, soften)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .padding(vertical = 4.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(fill)
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
                        tint = eventInk(fill, alpha = 0.7f),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}
