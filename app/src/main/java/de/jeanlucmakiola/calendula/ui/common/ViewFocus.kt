package de.jeanlucmakiola.calendula.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.datetime.LocalDate

/**
 * The date every calendar view is looking at (#184), so switching views keeps
 * the same days on screen and what is shown in both can travel between them.
 *
 * A view reads it once, as it comes on screen, and writes it back only when the
 * user moves: a view settling where it opened must not nudge it, or switching
 * back and forth would drift.
 */
@Stable
class ViewFocus(initial: LocalDate) {
    var date by mutableStateOf(initial)
}

val ViewFocusSaver: Saver<ViewFocus, String> = Saver(
    save = { it.date.toString() },
    restore = { ViewFocus(LocalDate.parse(it)) },
)

val LocalViewFocus = staticCompositionLocalOf<ViewFocus?> { null }

/**
 * Runs [enter] with the focused date once, as the view comes on screen, before
 * it reads its own position: its first frame is then already on that date.
 */
@Composable
fun <T : Any> EnterOnFocus(key: T, enter: (LocalDate) -> Unit) {
    val focus = LocalViewFocus.current
    remember(key) { focus?.date?.also(enter) }
}
