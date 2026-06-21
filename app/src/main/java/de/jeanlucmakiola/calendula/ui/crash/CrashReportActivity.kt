package de.jeanlucmakiola.calendula.ui.crash

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import de.jeanlucmakiola.calendula.data.crash.CrashReporter
import de.jeanlucmakiola.calendula.ui.theme.CalendulaTheme

/**
 * A deliberately minimal, standalone surface for a captured crash report.
 * `MainActivity` routes here when it detects a startup crash-loop (see
 * [CrashReporter.isCrashLoop]): the main UI can't be trusted to start, so this
 * screen stays clear of the app's Hilt graph, DataStore-backed theme and
 * Compose content — it only reads the report file and shows the report dialog.
 * Plain [CalendulaTheme] defaults (follow-system, dynamic colour) avoid touching
 * anything that might be the cause of the crash.
 */
class CrashReportActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val report = CrashReporter.pendingReport(this)
        if (report == null) {
            finish()
            return
        }
        enableEdgeToEdge()
        setContent {
            CalendulaTheme {
                // Opaque backdrop so the dialog doesn't float over a bare task.
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {}
                CrashReportDialog(
                    report = report,
                    onSend = {
                        submitCrashReport(this, report)
                        CrashReporter.clearReport(this)
                        finish()
                    },
                    onDismiss = {
                        CrashReporter.clearReport(this)
                        finish()
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Reaching this screen breaks the loop; reset the timing trail so a
        // later ordinary crash isn't mistaken for a loop.
        CrashReporter.markHealthy(this)
    }
}
