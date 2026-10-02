package de.jeanlucmakiola.calendula.ui.common

import android.content.ClipData
import android.os.Build
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import de.jeanlucmakiola.calendula.R
import kotlinx.coroutines.launch

/** Puts one labelled field on the clipboard. */
fun interface FieldCopier {
    operator fun invoke(label: String, text: String)
}

/**
 * A [FieldCopier] that confirms the copy only where the system doesn't (#195).
 *
 * Android 13 raises its own clipboard chip for every copy, so a snackbar on top
 * of it reads as the app having done the job twice. A failure is always worth a
 * word, though: the clipboard can refuse a very long description outright.
 */
@Composable
fun rememberFieldCopier(snackbarHostState: SnackbarHostState): FieldCopier {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val confirmation = stringResource(R.string.field_copied)
    val failure = stringResource(R.string.field_copy_failed)
    return remember(clipboard, scope, snackbarHostState, confirmation, failure) {
        FieldCopier { label, text ->
            scope.launch {
                val message = runCatching {
                    clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(label, text)))
                }.fold(
                    onSuccess = {
                        confirmation.takeIf {
                            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                        }
                    },
                    onFailure = { failure },
                )
                if (message != null) {
                    // Copying twice in a row shouldn't queue two four-second
                    // confirmations — the newest one wins.
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(message)
                }
            }
        }
    }
}

/**
 * Long-press to copy [text] whole, filed on the clipboard under [label].
 *
 * [onTap] carries a field's existing tap action through, announced as
 * [onTapLabel]; a field without one stays unclickable rather than growing a
 * ripple that leads nowhere, and gets its long press announced through semantics instead. That branch merges the
 * node it sits on, so a screen reader lands on the field itself and finds the
 * action there — a bare container is never focused.
 */
@Composable
fun Modifier.copyOnLongPress(
    label: String,
    text: String,
    copy: FieldCopier,
    onTap: (() -> Unit)? = null,
    onTapLabel: String? = null,
): Modifier {
    val actionLabel = stringResource(R.string.field_copy_action)
    return if (onTap != null) {
        combinedClickable(
            onClick = onTap,
            onClickLabel = onTapLabel,
            onLongClickLabel = actionLabel,
            onLongClick = { copy(label, text) },
        )
    } else {
        val haptics = LocalHapticFeedback.current
        pointerInput(label, text, copy) {
            detectTapGestures(
                onLongPress = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    copy(label, text)
                },
            )
        }.semantics(mergeDescendants = true) {
            onLongClick(actionLabel) {
                copy(label, text)
                true
            }
        }
    }
}
