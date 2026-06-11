package de.jeanlucmakiola.calendula.ui.settings

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.core.net.toUri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.jeanlucmakiola.calendula.R
import de.jeanlucmakiola.calendula.data.prefs.ThemeMode
import de.jeanlucmakiola.calendula.data.prefs.WeekStartPref
import de.jeanlucmakiola.calendula.domain.EventFormField

/**
 * Settings (M4) — appearance (theme, dynamic colour, week start), language,
 * and an about section. A full-screen destination; [onBack] pops it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Intercept the system back button/gesture — without this it falls through
    // to the activity and closes the app instead of returning to the calendar.
    BackHandler { onBack() }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            SectionHeader(stringResource(R.string.settings_section_appearance))

            SettingDropdownRow(
                title = stringResource(R.string.settings_theme),
                selected = state.themeMode,
                options = ThemeMode.entries,
                optionLabel = { themeLabel(it) },
                onSelect = viewModel::setThemeMode,
            )
            DynamicColorRow(
                checked = state.dynamicColor,
                enabled = state.dynamicColorAvailable,
                onCheckedChange = viewModel::setDynamicColor,
            )
            SettingDropdownRow(
                title = stringResource(R.string.settings_week_start),
                selected = state.weekStart,
                options = WeekStartPref.entries,
                optionLabel = { weekStartLabel(it) },
                onSelect = viewModel::setWeekStart,
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionHeader(stringResource(R.string.settings_section_event_form))
            Text(
                text = stringResource(R.string.settings_form_fields_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            EventFormField.entries.forEach { field ->
                FormFieldRow(
                    title = stringResource(formFieldLabel(field)),
                    checked = field in state.defaultFormFields,
                    onCheckedChange = { viewModel.setFormFieldDefault(field, it) },
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionHeader(stringResource(R.string.settings_section_language))
            LanguageRow()

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionHeader(stringResource(R.string.settings_section_about))
            AboutSection()
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun LanguageRow() {
    // Setting a locale recreates the activity; mirror the choice locally so the
    // dropdown updates instantly even before the recreation lands.
    var current by remember { mutableStateOf(AppLanguage.current()) }
    SettingDropdownRow(
        title = stringResource(R.string.settings_language),
        selected = current,
        options = LanguagePref.entries,
        optionLabel = { languageLabel(it) },
        onSelect = {
            current = it
            AppLanguage.apply(it)
        },
    )
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun <T> SettingDropdownRow(
    title: String,
    selected: T,
    options: List<T>,
    optionLabel: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = optionLabel(selected),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Icon(
                Icons.Default.ArrowDropDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}

@Composable
private fun DynamicColorRow(
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.settings_dynamic_color),
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!enabled) {
                Text(
                    text = stringResource(R.string.settings_dynamic_color_unavailable),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
}

@Composable
private fun AboutSection() {
    val context = LocalContext.current
    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "—"
    }
    val sourceUrl = stringResource(R.string.about_source_url)

    AboutRow(
        title = stringResource(R.string.settings_version),
        value = versionName,
    )
    AboutRow(
        title = stringResource(R.string.settings_license),
        value = stringResource(R.string.settings_license_value),
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(start = 8.dp)) {
            Text(
                text = stringResource(R.string.settings_source),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = sourceUrl.removePrefix("https://"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = {
            val intent = Intent(Intent.ACTION_VIEW, sourceUrl.toUri())
            runCatching { context.startActivity(intent) }
        }) {
            Text(stringResource(R.string.settings_source_open))
        }
    }
}

@Composable
private fun AboutRow(title: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
    }
}

@Composable
private fun FormFieldRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun formFieldLabel(field: EventFormField): Int = when (field) {
    EventFormField.Location -> R.string.event_detail_location
    EventFormField.Description -> R.string.event_detail_description
    EventFormField.Reminders -> R.string.event_detail_reminders
    EventFormField.Availability -> R.string.event_edit_availability
    EventFormField.Visibility -> R.string.event_edit_visibility
}

@Composable
private fun themeLabel(mode: ThemeMode): String = stringResource(
    when (mode) {
        ThemeMode.SYSTEM -> R.string.settings_theme_system
        ThemeMode.LIGHT -> R.string.settings_theme_light
        ThemeMode.DARK -> R.string.settings_theme_dark
    },
)

@Composable
private fun weekStartLabel(pref: WeekStartPref): String = stringResource(
    when (pref) {
        WeekStartPref.AUTO -> R.string.settings_week_start_auto
        WeekStartPref.MONDAY -> R.string.settings_week_start_monday
        WeekStartPref.SUNDAY -> R.string.settings_week_start_sunday
    },
)

@Composable
private fun languageLabel(pref: LanguagePref): String = stringResource(
    when (pref) {
        LanguagePref.AUTO -> R.string.settings_language_auto
        LanguagePref.GERMAN -> R.string.settings_language_german
        LanguagePref.ENGLISH -> R.string.settings_language_english
    },
)
