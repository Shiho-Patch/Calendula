package de.jeanlucmakiola.calendula.ui.licences

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.jeanlucmakiola.calendula.R
import de.jeanlucmakiola.floret.components.CollapsingScaffold
import de.jeanlucmakiola.floret.components.GroupedListInset
import de.jeanlucmakiola.floret.components.GroupedRow
import de.jeanlucmakiola.floret.components.positionOf

/**
 * Third-party attribution.
 *
 * Reachable from Settings rather than buried, because Apache-2.0 §4(a) is about
 * what the *recipient of the binary* is told. Each row opens the project's source.
 */
@Composable
internal fun LicencesScreen(onBack: () -> Unit, visible: Boolean = true) {
    val uriHandler = LocalUriHandler.current

    CollapsingScaffold(
        title = stringResource(R.string.licences_title),
        onBack = onBack,
        predictiveBack = true,
        visible = visible,
    ) {
        Text(
            stringResource(R.string.licences_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = GroupedListInset),
        )
        Spacer(Modifier.height(16.dp))

        OpenSourceLicences.ALL.forEachIndexed { index, attribution ->
            GroupedRow(
                title = attribution.name,
                summary = "${attribution.copyright} · ${attribution.licence.spdxId}",
                position = positionOf(index, OpenSourceLicences.ALL.size),
                onClick = { uriHandler.openUri(attribution.sourceUrl) },
            )
        }

        Spacer(Modifier.height(24.dp))
        Text(
            stringResource(R.string.licences_footer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = GroupedListInset),
        )
        Spacer(Modifier.height(24.dp))
    }
}
