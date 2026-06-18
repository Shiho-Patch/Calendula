package de.jeanlucmakiola.calendula.ui.common

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.jeanlucmakiola.calendula.R

/**
 * Position of a row within a grouped list, after the Android-15 settings
 * pattern: a run of rows shares one rounded container, with full corners at the
 * group's outer edges and small corners between, separated by small gaps.
 */
enum class Position { Top, Middle, Bottom, Alone }

/** Maps an index within a group of [count] rows to its [Position]. */
fun positionOf(index: Int, count: Int): Position = when {
    count <= 1 -> Position.Alone
    index == 0 -> Position.Top
    index == count - 1 -> Position.Bottom
    else -> Position.Middle
}

/**
 * The app's standard full-screen list scaffold: a collapsing [LargeTopAppBar]
 * whose title shrinks into the bar (next to the back button) as the content
 * scrolls. Content is a scrollable column that feeds the toolbar via nested
 * scroll. Used by Settings and the calendar manager so they share one shell.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollapsingScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHost: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    BackHandler(onBack = onBack)
    val scrollBehavior =
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back),
                        )
                    }
                },
                actions = actions,
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        snackbarHost = snackbarHost,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                // Mark the scaffold's system-bar insets as consumed so the
                // imePadding below adds only the keyboard height beyond them
                // (max, not sum) — otherwise the nav-bar inset double-counts and
                // leaves an empty strip above the keyboard.
                .consumeWindowInsets(innerPadding)
                .fillMaxSize()
                // Paint the surface across the full area before imePadding carves
                // into it, so any sliver above the keyboard reads as surface — not
                // the dialog window's black — during the IME animation.
                .background(MaterialTheme.colorScheme.surface)
                // Shrink the scroll viewport by the keyboard inset so a focused
                // field (e.g. the custom-reminder amount) can scroll into view.
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(top = 8.dp, bottom = 24.dp),
            content = content,
        )
    }
}

/**
 * One row in a grouped list: an M3 [ListItem] over a tonal [Surface] whose
 * corner radii come from its [position] (so a run of rows reads as a single
 * rounded card). Corners round further on press. A null [onClick] makes the
 * row non-interactive (e.g. read-only entries).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupedRow(
    title: String,
    position: Position,
    modifier: Modifier = Modifier,
    summary: String? = null,
    selected: Boolean = false,
    minHeight: Dp = 72.dp,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val full by animateDpAsState(if (pressed) 36.dp else 22.dp, label = "fullCorner")
    val small by animateDpAsState(if (pressed) 36.dp else 6.dp, label = "smallCorner")
    val shape = when (position) {
        Position.Alone -> RoundedCornerShape(full)
        Position.Top -> RoundedCornerShape(
            topStart = full, topEnd = full, bottomStart = small, bottomEnd = small,
        )
        Position.Middle -> RoundedCornerShape(small)
        Position.Bottom -> RoundedCornerShape(
            topStart = small, topEnd = small, bottomStart = full, bottomEnd = full,
        )
    }
    val gap = when (position) {
        Position.Top, Position.Middle -> Modifier.padding(bottom = 2.dp)
        Position.Bottom, Position.Alone -> Modifier
    }
    val itemColors = if (selected) {
        ListItemDefaults.colors(
            containerColor = Color.Transparent,
            headlineColor = MaterialTheme.colorScheme.onSecondaryContainer,
            leadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
            supportingColor = MaterialTheme.colorScheme.onSecondaryContainer,
            trailingIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    } else {
        ListItemDefaults.colors(containerColor = Color.Transparent)
    }
    val item: @Composable () -> Unit = {
        ListItem(
            headlineContent = { Text(title) },
            supportingContent = summary?.let { text -> { Text(text) } },
            leadingContent = leading,
            trailingContent = trailing,
            colors = itemColors,
            modifier = Modifier.heightIn(min = minHeight),
        )
    }
    val base = modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp)
        .then(gap)
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    if (onClick != null) {
        Surface(
            onClick = onClick,
            color = containerColor,
            shape = shape,
            interactionSource = interaction,
            modifier = base,
        ) { item() }
    } else {
        Surface(color = containerColor, shape = shape, modifier = base) { item() }
    }
}
