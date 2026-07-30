package de.jeanlucmakiola.calendula.ui.settings

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.jeanlucmakiola.calendula.R
import de.jeanlucmakiola.calendula.data.appname.LauncherName
import de.jeanlucmakiola.calendula.data.prefs.ThemeMode
import de.jeanlucmakiola.calendula.domain.FontRole
import de.jeanlucmakiola.calendula.ui.common.PickerDescription
import de.jeanlucmakiola.calendula.ui.theme.BundledFont
import de.jeanlucmakiola.calendula.ui.theme.FONT_CUSTOM_TOKEN
import de.jeanlucmakiola.calendula.ui.theme.FONT_SYSTEM_TOKEN
import de.jeanlucmakiola.calendula.ui.theme.resolveFontFamily
import de.jeanlucmakiola.floret.components.CollapsingScaffold
import de.jeanlucmakiola.floret.components.FullScreenPicker
import de.jeanlucmakiola.floret.components.GroupedListInset
import de.jeanlucmakiola.floret.components.GroupedRow
import de.jeanlucmakiola.floret.components.OptionPicker
import de.jeanlucmakiola.floret.components.Position
import de.jeanlucmakiola.floret.components.SelectedCheck
import de.jeanlucmakiola.floret.components.positionOf

/**
 * Appearance: how the app itself looks — theme and colour, the two typeface
 * roles, and the launcher name. Anything that only changes how a *calendar view*
 * reads (week start, time format, grid options) lives in [ViewsScreen] instead,
 * and the widget-only settings in [WidgetsScreen] (#69).
 */
@Composable
internal fun AppearanceScreen(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    var showTheme by remember { mutableStateOf(false) }
    var showBrandFont by remember { mutableStateOf(false) }
    var showPlainFont by remember { mutableStateOf(false) }
    var showAppName by remember { mutableStateOf(false) }

    val fonts by viewModel.fontState.collectAsStateWithLifecycle()
    val launcherName by viewModel.launcherName.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val importFailedMessage = stringResource(R.string.settings_font_import_failed)
    LaunchedEffect(Unit) {
        viewModel.fontImportFailed.collect {
            Toast.makeText(context, importFailedMessage, Toast.LENGTH_LONG).show()
        }
    }

    CollapsingScaffold(
        title = stringResource(R.string.settings_section_appearance),
        onBack = onBack,
        predictiveBack = true,
    ) {
        // Theme & colour
        GroupedRow(
            title = stringResource(R.string.settings_theme),
            summary = themeLabel(state.themeMode),
            position = Position.Top,
            onClick = { showTheme = true },
        )
        GroupedRow(
            title = stringResource(R.string.settings_dynamic_color),
            summary = if (state.dynamicColorAvailable) {
                stringResource(R.string.settings_dynamic_color_summary)
            } else {
                stringResource(R.string.settings_dynamic_color_unavailable)
            },
            position = Position.Middle,
            trailing = {
                Switch(
                    checked = state.dynamicColor,
                    onCheckedChange = viewModel::setDynamicColor,
                    enabled = state.dynamicColorAvailable,
                )
            },
            onClick = if (state.dynamicColorAvailable) {
                { viewModel.setDynamicColor(!state.dynamicColor) }
            } else {
                null
            },
        )
        GroupedRow(
            title = stringResource(R.string.settings_soften_colors),
            summary = stringResource(R.string.settings_soften_colors_summary),
            position = Position.Bottom,
            trailing = {
                Switch(
                    checked = state.softenColors,
                    onCheckedChange = viewModel::setSoftenColors,
                )
            },
            onClick = { viewModel.setSoftenColors(!state.softenColors) },
        )

        Spacer(Modifier.height(16.dp))

        // Fonts — the two Material typeface roles (#19).
        GroupedRow(
            title = stringResource(R.string.settings_font_headings),
            summary = fontLabel(fonts.brand),
            position = Position.Top,
            onClick = { showBrandFont = true },
        )
        GroupedRow(
            title = stringResource(R.string.settings_font_body),
            summary = fontLabel(fonts.plain),
            position = Position.Bottom,
            onClick = { showPlainFont = true },
        )

        Spacer(Modifier.height(16.dp))

        // App name — the launcher label (#44); its own group, being a
        // launcher concern rather than app styling.
        GroupedRow(
            title = stringResource(R.string.settings_app_name),
            summary = launcherNameLabel(launcherName),
            position = Position.Alone,
            onClick = { showAppName = true },
        )
    }

    if (showAppName) {
        FullScreenPicker(
            title = stringResource(R.string.settings_app_name),
            onDismiss = { showAppName = false },
            predictiveBack = true,
        ) {
            // Both names shown as launcher-mark previews; tapping applies at
            // once and the picker stays open so the change is visible.
            Text(
                text = stringResource(R.string.settings_app_name_summary),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
            )
            Spacer(Modifier.height(24.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                LauncherName.entries.forEach { option ->
                    AppNameOptionCard(
                        name = option,
                        selected = launcherName == option,
                        onClick = { viewModel.setLauncherName(option) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
    if (showTheme) {
        // No thumbnails: picking a theme repaints the app itself. Only which
        // way "Follow the system" currently falls needs spelling out.
        val systemDark = isSystemInDarkTheme()
        OptionPicker(
            title = stringResource(R.string.settings_theme),
            header = { PickerDescription(stringResource(R.string.settings_theme_hint)) },
            predictiveBack = true,
            options = ThemeMode.entries,
            selected = state.themeMode,
            label = { themeLabel(it) },
            summary = { mode ->
                if (mode == ThemeMode.SYSTEM) {
                    stringResource(
                        R.string.settings_theme_system_summary,
                        themeLabel(if (systemDark) ThemeMode.DARK else ThemeMode.LIGHT),
                    )
                } else {
                    null
                }
            },
            onSelect = viewModel::setThemeMode,
            onDismiss = { showTheme = false },
        )
    }
    if (showBrandFont) {
        FontPicker(
            title = stringResource(R.string.settings_font_headings),
            role = FontRole.BRAND,
            selected = fonts.brand,
            stamp = fonts.brandStamp,
            onSelect = { viewModel.setFont(FontRole.BRAND, it) },
            onImport = { viewModel.importCustomFont(FontRole.BRAND, it) },
            onDismiss = { showBrandFont = false },
        )
    }
    if (showPlainFont) {
        FontPicker(
            title = stringResource(R.string.settings_font_body),
            role = FontRole.PLAIN,
            selected = fonts.plain,
            stamp = fonts.plainStamp,
            onSelect = { viewModel.setFont(FontRole.PLAIN, it) },
            onImport = { viewModel.importCustomFont(FontRole.PLAIN, it) },
            onDismiss = { showPlainFont = false },
        )
    }
}

@Composable
private fun themeLabel(mode: ThemeMode): String = stringResource(
    when (mode) {
        ThemeMode.SYSTEM -> R.string.settings_theme_system
        ThemeMode.LIGHT -> R.string.settings_theme_light
        ThemeMode.DARK -> R.string.settings_theme_dark
    },
)

/** The summary label for a stored font token (issue #19). */
@Composable
private fun fontLabel(token: String): String = when (token) {
    FONT_SYSTEM_TOKEN -> stringResource(R.string.settings_font_system)
    FONT_CUSTOM_TOKEN -> stringResource(R.string.settings_font_custom_selected)
    else -> BundledFont.fromToken(token)?.let { stringResource(it.labelRes) }
        ?: stringResource(R.string.settings_font_system)
}

/** The display name for a launcher-label choice (issue #44). */
@Composable
private fun launcherNameLabel(name: LauncherName): String = stringResource(
    when (name) {
        LauncherName.CALENDULA -> R.string.app_name
        LauncherName.CALENDAR -> R.string.app_name_calendar_alias
    },
)

/**
 * MIME types offered to the document picker so it lists only font files. Covers
 * the modern `font/` types plus the legacy `application/` font aliases some
 * providers still report. Anything that slips through is still validated by
 * [de.jeanlucmakiola.calendula.data.fonts.CustomFontStore] before use.
 */
private val FONT_PICKER_MIME_TYPES = arrayOf(
    "font/ttf",
    "font/otf",
    "font/sfnt",
    "font/collection",
    "application/x-font-ttf",
    "application/x-font-otf",
    "application/font-sfnt",
    "application/vnd.ms-opentype",
)

/**
 * Full-screen font chooser for one [FontRole]: the system default, each bundled
 * font, and "Choose file…" for a .ttf/.otf. A specimen of the current choice
 * sits above the rows, so the picker stays open on selection and re-renders it.
 */
@Composable
private fun FontPicker(
    title: String,
    role: FontRole,
    selected: String,
    stamp: Int,
    onSelect: (String) -> Unit,
    onImport: (Uri) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) onImport(uri)
    }

    // System default + the bundled fonts + the "Choose file…" row.
    val rowCount = BundledFont.entries.size + 2
    val isCustom = selected == FONT_CUSTOM_TOKEN
    // Resolving the custom face stats the disk, so memoise it; [stamp] is bumped
    // on re-import to refresh a replaced file.
    val customPreview = remember(role, isCustom, stamp) {
        if (isCustom) resolveFontFamily(FONT_CUSTOM_TOKEN, role, context) else null
    }
    val selectedFamily = remember(role, selected, stamp) {
        resolveFontFamily(selected, role, context)
    }

    FullScreenPicker(title = title, onDismiss = onDismiss, predictiveBack = true) {
        FontSpecimen(role = role, family = selectedFamily)
        FontOptionRow(
            label = stringResource(R.string.settings_font_system),
            preview = FontFamily.Default,
            selected = selected == FONT_SYSTEM_TOKEN,
            position = positionOf(0, rowCount),
            onClick = { onSelect(FONT_SYSTEM_TOKEN) },
        )
        BundledFont.entries.forEachIndexed { index, font ->
            FontOptionRow(
                label = stringResource(font.labelRes),
                preview = font.family,
                selected = selected == font.token,
                position = positionOf(index + 1, rowCount),
                onClick = { onSelect(font.token) },
            )
        }
        FontOptionRow(
            label = if (isCustom) {
                stringResource(R.string.settings_font_custom_selected)
            } else {
                stringResource(R.string.settings_font_choose_file)
            },
            preview = customPreview,
            leadingIcon = if (isCustom) null else Icons.Default.UploadFile,
            selected = isCustom,
            position = positionOf(rowCount - 1, rowCount),
            onClick = { launcher.launch(FONT_PICKER_MIME_TYPES) },
        )
    }
}

/**
 * A specimen of [family] in the type role [role] governs, using the app's own
 * styles with only the family swapped. A null [family] is the system typeface.
 */
@Composable
private fun FontSpecimen(role: FontRole, family: FontFamily?) {
    val isBrand = role == FontRole.BRAND
    val style = if (isBrand) {
        MaterialTheme.typography.headlineMedium
    } else {
        MaterialTheme.typography.bodyLarge
    }
    Text(
        text = stringResource(
            if (isBrand) R.string.settings_font_specimen_heading else R.string.settings_font_specimen_body,
        ),
        style = style.copy(fontFamily = family ?: style.fontFamily),
        color = MaterialTheme.colorScheme.onSurface,
        // Fixed height, so switching between a wide and a narrow face doesn't
        // shuffle the option list under it.
        minLines = 2,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = GroupedListInset, vertical = 8.dp),
    )
}

/**
 * One row in the [FontPicker]: the font's name, an "Ag" sample in its own
 * [preview] face (or an [leadingIcon] cue), and a check when selected.
 */
@Composable
private fun FontOptionRow(
    label: String,
    preview: FontFamily?,
    selected: Boolean,
    position: Position,
    onClick: () -> Unit,
    leadingIcon: ImageVector? = null,
) {
    GroupedRow(
        title = label,
        position = position,
        selected = selected,
        leading = {
            if (preview != null) {
                Text(
                    text = "Ag",
                    fontFamily = preview,
                    style = MaterialTheme.typography.titleLarge,
                )
            } else if (leadingIcon != null) {
                Icon(imageVector = leadingIcon, contentDescription = null)
            }
        },
        trailing = if (selected) {
            { SelectedCheck() }
        } else {
            null
        },
        onClick = onClick,
    )
}

/**
 * One selectable launcher-name preview in the App name picker (#44): the app's
 * launcher mark over the name, framed as a card.
 */
@Composable
private fun AppNameOptionCard(
    name: LauncherName,
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
    Column(
        modifier = modifier
            .clip(shape)
            .background(containerColor)
            .border(width = if (selected) 2.dp else 1.dp, color = borderColor, shape = shape)
            .clickable(onClick = onClick)
            .padding(vertical = 20.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // The adaptive mark rebuilt as a squircle, as in the onboarding
        // BrandHero, so it renders identically everywhere.
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(colorResource(R.color.ic_launcher_background)),
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Text(
            text = launcherNameLabel(name),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
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
