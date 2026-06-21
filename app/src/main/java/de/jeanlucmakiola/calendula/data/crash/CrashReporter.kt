package de.jeanlucmakiola.calendula.data.crash

import android.content.Context
import android.content.pm.PackageInfo
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Privacy-respecting crash capture (prod-readiness item 10). On an uncaught
 * exception it writes a self-contained report to the app's private storage and
 * then chains to the platform's default handler, so the process still dies
 * normally (and the OS shows its own "stopped" dialog). Nothing is uploaded —
 * the app holds no `INTERNET` permission. The user submits the report later,
 * by hand, as a Gitea issue (see the ui/crash surfaces).
 *
 * The report is built from a fixed [CrashContext] allowlist — app/Android/device
 * version, locale, time, and the stack trace — and **nothing else**: no device
 * identifiers, no account names, no calendar/event content, no logcat. The user
 * is always shown the full text before it leaves the device.
 */
object CrashReporter {

    /**
     * Install the handler. Called first thing in `CalendulaApp.onCreate()` so it
     * also catches crashes during startup. The handler swallows nothing — it
     * persists, then delegates to the previously-registered handler.
     */
    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            // Capturing must never mask the original crash, so guard every step.
            runCatching {
                val now = System.currentTimeMillis()
                writeReport(appContext, buildCrashReport(CrashContext.from(appContext), throwable, now))
                recordCrashTime(appContext, now)
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    /** The persisted report from the last crash, or null if there is none. */
    fun pendingReport(context: Context): String? {
        val file = reportFile(context)
        return if (file.exists()) runCatching { file.readText() }.getOrNull()?.takeIf { it.isNotBlank() } else null
    }

    /**
     * Whether to surface the report unprompted (on the next launch): a report
     * exists and the user hasn't already waved this one away. Settings reaches
     * the report via [pendingReport] regardless, so "Not now" only stops the
     * auto-prompt — it doesn't discard the report.
     */
    fun shouldPrompt(context: Context): Boolean =
        reportFile(context).exists() && !dismissedFile(context).exists()

    /** Stop auto-prompting for the current report without discarding it. */
    fun dismissPrompt(context: Context) {
        runCatching { dismissedFile(context).apply { parentFile?.mkdirs() }.writeText("") }
    }

    /** Drop the persisted report once the user has reported it (or from Settings). */
    fun clearReport(context: Context) {
        runCatching { reportFile(context).delete() }
        runCatching { dismissedFile(context).delete() }
    }

    /**
     * Whether the app appears to be in a startup crash-loop: at least
     * [LOOP_THRESHOLD] crashes inside [LOOP_WINDOW_MS]. In that case the main UI
     * can't be trusted to start, so the caller routes straight to the standalone
     * report screen instead of re-entering the crashing graph.
     */
    fun isCrashLoop(context: Context): Boolean {
        val times = readCrashTimes(context)
        if (times.size < LOOP_THRESHOLD) return false
        val recent = times.sortedDescending()
        return recent[0] - recent[LOOP_THRESHOLD - 1] <= LOOP_WINDOW_MS
    }

    /**
     * Mark the app as having started successfully, resetting the loop counter so
     * an ordinary single crash much later never trips loop detection. The
     * pending report itself is kept — only the timing trail is cleared.
     */
    fun markHealthy(context: Context) {
        runCatching { timesFile(context).delete() }
    }

    // --- persistence -------------------------------------------------------

    private fun writeReport(context: Context, report: String) {
        val file = reportFile(context).apply { parentFile?.mkdirs() }
        file.writeText(report.take(MAX_REPORT_CHARS))
        // A fresh crash should prompt again, even if the previous one was waved away.
        runCatching { dismissedFile(context).delete() }
    }

    private fun recordCrashTime(context: Context, nowMillis: Long) {
        val kept = (readCrashTimes(context) + nowMillis).takeLast(MAX_TIMES)
        timesFile(context).apply { parentFile?.mkdirs() }
            .writeText(kept.joinToString("\n"))
    }

    private fun readCrashTimes(context: Context): List<Long> {
        val file = timesFile(context)
        if (!file.exists()) return emptyList()
        return runCatching { file.readLines().mapNotNull { it.trim().toLongOrNull() } }.getOrDefault(emptyList())
    }

    private fun crashDir(context: Context) = File(context.filesDir, CRASH_DIR)
    private fun reportFile(context: Context) = File(crashDir(context), REPORT_FILE)
    private fun timesFile(context: Context) = File(crashDir(context), TIMES_FILE)
    private fun dismissedFile(context: Context) = File(crashDir(context), DISMISSED_FILE)

    private const val CRASH_DIR = "crash"
    private const val REPORT_FILE = "last_crash.txt"
    private const val TIMES_FILE = "crash_times.txt"
    private const val DISMISSED_FILE = "dismissed"
    private const val MAX_TIMES = 5
    private const val MAX_REPORT_CHARS = 64 * 1024
    private const val LOOP_THRESHOLD = 2
    private const val LOOP_WINDOW_MS = 10_000L
}

/**
 * The allowlist of non-personal facts that go into a crash report. Built from
 * [Build] and the app's own [PackageInfo]; deliberately holds no identifiers.
 */
data class CrashContext(
    val appVersionName: String,
    val appVersionCode: Long,
    val sdkInt: Int,
    val androidRelease: String,
    val manufacturer: String,
    val model: String,
    val locale: String,
) {
    companion object {
        fun from(context: Context): CrashContext {
            val pkg = runCatching {
                context.packageManager.getPackageInfo(context.packageName, 0)
            }.getOrNull()
            return CrashContext(
                appVersionName = pkg?.versionName ?: "?",
                appVersionCode = pkg?.let { PackageInfoCompat.getLongVersionCode(it) } ?: 0L,
                sdkInt = Build.VERSION.SDK_INT,
                androidRelease = Build.VERSION.RELEASE ?: "?",
                manufacturer = Build.MANUFACTURER ?: "?",
                model = Build.MODEL ?: "?",
                locale = Locale.getDefault().toLanguageTag(),
            )
        }
    }
}

/**
 * Render a crash report from the [ctx] allowlist, the [throwable]'s full stack
 * trace, and the crash [nowMillis]. Pure (no Android, no I/O) so it is unit
 * tested. The leading marker doubles as the file's sanity check in
 * [CrashReporter.pendingReport].
 */
fun buildCrashReport(ctx: CrashContext, throwable: Throwable, nowMillis: Long): String {
    val trace = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString().trim()
    val time = runCatching {
        Instant.ofEpochMilli(nowMillis).atZone(ZoneId.systemDefault()).format(TIME_FORMAT)
    }.getOrDefault(nowMillis.toString())
    return buildString {
        appendLine("Calendula crash report")
        appendLine("App version: ${ctx.appVersionName} (${ctx.appVersionCode})")
        appendLine("Android: ${ctx.androidRelease} (API ${ctx.sdkInt})")
        appendLine("Device: ${ctx.manufacturer} ${ctx.model}")
        appendLine("Locale: ${ctx.locale}")
        appendLine("Time: $time")
        appendLine()
        appendLine("Stack trace:")
        append(trace)
    }
}

private val TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
