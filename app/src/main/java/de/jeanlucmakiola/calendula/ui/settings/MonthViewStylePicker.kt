package de.jeanlucmakiola.calendula.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.jeanlucmakiola.calendula.R
import de.jeanlucmakiola.calendula.ui.month.MonthViewStyle
import de.jeanlucmakiola.calendula.ui.month.descriptionRes
import de.jeanlucmakiola.calendula.ui.month.labelRes
import de.jeanlucmakiola.floret.components.FullScreenPicker

/**
 * The Month view style chooser (#38, #53). Each option carries a schematic of the
 * layout it produces rather than a bare label — the three differ in *shape*, which
 * a word like "Continuous" doesn't convey on its own.
 *
 * Tapping applies immediately and the picker stays open, matching the App name
 * picker: the change is worth seeing land before leaving.
 */
@Composable
internal fun MonthViewStylePicker(
    selected: MonthViewStyle,
    onSelect: (MonthViewStyle) -> Unit,
    onDismiss: () -> Unit,
) {
    FullScreenPicker(
        title = stringResource(R.string.settings_month_view_style),
        onDismiss = onDismiss,
        predictiveBack = true,
    ) {
        Text(
            text = stringResource(R.string.settings_month_view_style_summary),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
        )
        Spacer(Modifier.height(24.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MonthViewStyle.entries.forEach { style ->
                MonthStyleOptionCard(
                    style = style,
                    selected = style == selected,
                    onClick = { onSelect(style) },
                )
            }
        }
    }
}

/**
 * One selectable style: its schematic, name and a line on what it does. Selection
 * reads three ways over — border weight, container tint and a check — so it never
 * rests on colour alone.
 */
@Composable
private fun MonthStyleOptionCard(
    style: MonthViewStyle,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(24.dp)
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(containerColor)
            .border(width = if (selected) 2.dp else 1.dp, color = borderColor, shape = shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(all = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        MonthStyleSchematic(style)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(style.labelRes),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(style.descriptionRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Selection indicator: a filled check when active, an empty ring otherwise.
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                .then(
                    if (selected) {
                        Modifier
                    } else {
                        Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

private val SCHEMATIC_WIDTH = 64.dp
private val SCHEMATIC_HEIGHT = 60.dp
private val SCHEMATIC_SHAPE = RoundedCornerShape(10.dp)

/**
 * A miniature of the layout, drawn from plain boxes on theme tokens — small
 * enough to read as an icon, literal enough that the three are told apart at a
 * glance: pages sit side by side, the continuous stream runs off both edges, the
 * split style stacks a dotted grid over a list.
 */
@Composable
private fun MonthStyleSchematic(style: MonthViewStyle, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(width = SCHEMATIC_WIDTH, height = SCHEMATIC_HEIGHT)
            .clip(SCHEMATIC_SHAPE)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .clipToBounds(),
        contentAlignment = Alignment.Center,
    ) {
        when (style) {
            MonthViewStyle.Paged -> PagedSchematic()
            MonthViewStyle.Continuous -> ContinuousSchematic()
            MonthViewStyle.Split -> SplitSchematic()
        }
    }
}

/** Two pages side by side, the second peeking in — a swipe away. */
@Composable
private fun PagedSchematic() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        SchematicGrid(rows = 4, modifier = Modifier.weight(1f))
        // The next page, cropped by the container — the swipe affordance.
        SchematicGrid(rows = 4, modifier = Modifier.width(20.dp), dim = true)
    }
}

/** One column of weeks running off both edges: no page breaks, no repeated days. */
@Composable
private fun ContinuousSchematic() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Six rows in a 60dp box overflow deliberately: the stream is clipped top
        // and bottom rather than fitting neatly, which is the whole idea.
        repeat(6) { index ->
            SchematicWeekRow(dim = index == 0 || index == 5)
        }
    }
}

/** A squished dotted grid over the selected day's list. */
@Composable
private fun SplitSchematic() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        SchematicGrid(rows = 3, dotted = true)
        // The day pane: two stand-in event rows.
        repeat(2) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
            )
        }
    }
}

/** [rows] week rows of seven cells; [dotted] shrinks the cells to event dots. */
@Composable
private fun SchematicGrid(
    rows: Int,
    modifier: Modifier = Modifier,
    dim: Boolean = false,
    dotted: Boolean = false,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(if (dotted) 4.dp else 3.dp),
    ) {
        repeat(rows) {
            if (dotted) SchematicDotRow(dim) else SchematicWeekRow(dim)
        }
    }
}

/** A week as seven filled cells. */
@Composable
private fun SchematicWeekRow(dim: Boolean = false, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
        .copy(alpha = if (dim) 0.18f else 0.38f)
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        repeat(7) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(5.dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(color),
            )
        }
    }
}

/** A week as seven event dots — the split style's compact grid. */
@Composable
private fun SchematicDotRow(dim: Boolean = false, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
        .copy(alpha = if (dim) 0.18f else 0.38f)
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(7) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}
