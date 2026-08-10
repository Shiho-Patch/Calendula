package de.jeanlucmakiola.calendula.ui.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.jeanlucmakiola.calendula.R

/** What, if anything, the brand mark is wearing in its corner. */
internal enum class HeroBadge { Denied, Done }

/**
 * The app's adaptive launcher mark, reconstructed as a large branded squircle —
 * the hero of the onboarding screens (floret-kit's OnboardingScaffold supplies
 * the shell; the mark stays app-local so each sibling keeps its own identity).
 * A [badge] overlays the corner where the screen has a verdict to give: blocked,
 * or finished.
 */
@Composable
internal fun BrandHero(badge: HeroBadge? = null) {
    Box(contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(128.dp)
                .clip(RoundedCornerShape(34.dp))
                .background(colorResource(R.color.ic_launcher_background)),
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = stringResource(R.string.app_name),
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (badge != null) {
            val container = when (badge) {
                HeroBadge.Denied -> MaterialTheme.colorScheme.errorContainer
                HeroBadge.Done -> MaterialTheme.colorScheme.primaryContainer
            }
            val onContainer = when (badge) {
                HeroBadge.Denied -> MaterialTheme.colorScheme.onErrorContainer
                HeroBadge.Done -> MaterialTheme.colorScheme.onPrimaryContainer
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 10.dp, y = 10.dp)
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(container),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = when (badge) {
                        HeroBadge.Denied -> Icons.Filled.Lock
                        HeroBadge.Done -> Icons.Filled.Check
                    },
                    contentDescription = null,
                    tint = onContainer,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

/**
 * A single [icon] in the brand squircle — the same silhouette as [BrandHero],
 * for the wizard steps that stand for a feature rather than for the app itself.
 */
@Composable
internal fun IconHero(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(128.dp)
            .clip(RoundedCornerShape(34.dp))
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(56.dp),
        )
    }
}
