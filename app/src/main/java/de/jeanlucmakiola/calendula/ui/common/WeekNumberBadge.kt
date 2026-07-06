package de.jeanlucmakiola.calendula.ui.common

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.jeanlucmakiola.calendula.R

/**
 * Calendar-week badge — a filled tonal chip with a bold number, deliberately set
 * apart from the surrounding day numbers. Shared by the Week and Month views so
 * the week-of-year indicator reads identically wherever it appears.
 */
@Composable
fun WeekNumberBadge(weekNumber: Int, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.week_number_label)
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier.semantics { contentDescription = "$label $weekNumber" },
    ) {
        Text(
            text = weekNumber.toString(),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}
