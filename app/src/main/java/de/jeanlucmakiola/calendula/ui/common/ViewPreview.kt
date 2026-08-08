package de.jeanlucmakiola.calendula.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * The viewport a view preview pretends to be: a phone's width by the height a
 * calendar view gets under the top bar. Scaling every preview from one fixed
 * height keeps them comparable — each shows the same slice of screen.
 */
internal val PREVIEW_VIEWPORT_HEIGHT = 440.dp

/**
 * Renders [content] at a full phone viewport and shrinks the finished layout
 * into a box [height] tall, for the settings and onboarding choosers.
 *
 * Previews render the *real* view composables rather than a drawing of them, so
 * they cannot drift from the thing they depict: change a cell's shape or an
 * event block's colour and every preview follows. The trick is the [layout]
 * below — it ignores the incoming constraints, so the view lays itself out at a
 * plausible phone viewport, and a [graphicsLayer] scale shrinks the result.
 *
 * `Modifier.requiredSize` would be the obvious way to force the larger
 * measurement, but it *centres* content that overflows the incoming constraints
 * — which pushes the view to a negative offset and leaves only its bottom-right
 * corner inside the clip.
 */
@Composable
internal fun ScaledViewPreview(
    height: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val scale = height.value / PREVIEW_VIEWPORT_HEIGHT.value

    Box(
        modifier = modifier
            .clipToBounds()
            .background(MaterialTheme.colorScheme.surface)
            // A preview is a picture, not a control: swallow touches before the
            // view's own clickables see them, and give screen readers one label
            // instead of six weeks of day cells.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial).changes
                            .forEach { it.consume() }
                    }
                }
            }
            .clearAndSetSemantics { }
            // Measure at a full phone viewport but report the scaled size, so the
            // node occupies exactly what it draws.
            .layout { measurable, _ ->
                val fullWidth = screenWidth.roundToPx()
                val fullHeight = PREVIEW_VIEWPORT_HEIGHT.roundToPx()
                val placeable = measurable.measure(Constraints.fixed(fullWidth, fullHeight))
                layout((fullWidth * scale).roundToInt(), (fullHeight * scale).roundToInt()) {
                    placeable.place(0, 0)
                }
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0f, 0f)
            },
    ) {
        Column(Modifier.fillMaxSize()) { content() }
    }
}
