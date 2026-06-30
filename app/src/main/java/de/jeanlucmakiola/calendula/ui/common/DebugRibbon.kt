package de.jeanlucmakiola.calendula.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex

/**
 * A Flutter-style "DEBUG" corner ribbon, drawn across the top-right corner of
 * the app. Deliberately a stark, un-themed marker (not a product component) so a
 * debug build is unmistakable at a glance — gate it on `BuildConfig.DEBUG` at the
 * call site so it never reaches a release build. Non-interactive: it's a plain
 * label with no pointer handler, so taps fall through to whatever is beneath it.
 *
 * Drop it in as the last child of a full-screen [androidx.compose.foundation.layout.Box]
 * so it overlays the UI.
 */
@Composable
fun BoxScope.DebugRibbon() {
    Text(
        text = "DEBUG",
        color = Color.White,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .align(Alignment.TopEnd)
            .zIndex(1f)
            // Push the band out so its midline crosses the very corner, then
            // rotate it to the classic 45° ribbon.
            .offset(x = 36.dp, y = 24.dp)
            .rotate(45f)
            .background(Color(0xFFB23B00))
            .width(140.dp)
            .padding(vertical = 2.dp),
    )
}
