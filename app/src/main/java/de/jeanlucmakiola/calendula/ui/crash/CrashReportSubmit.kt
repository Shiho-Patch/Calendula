package de.jeanlucmakiola.calendula.ui.crash

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.net.toUri
import de.jeanlucmakiola.calendula.R

/**
 * Hand the captured crash report off to the user's chosen channel: the report
 * is copied to the clipboard (the reliable path for a full stack trace) and the
 * project's Gitea "new issue" page is opened with the body prefilled. Nothing is
 * sent automatically — the app has no network access; the user reviews and
 * submits the issue themselves.
 */
fun submitCrashReport(context: Context, report: String) {
    copyReportToClipboard(context, report)
    val opened = runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, buildIssueUri(context, report)))
    }.isSuccess
    val message = if (opened) R.string.crash_report_copied else R.string.crash_report_open_failed
    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
}

/** Open the issue tracker's template chooser for a manual (non-crash) report. */
fun openIssueTracker(context: Context) {
    val uri = context.getString(R.string.report_issue_choose_url).toUri()
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
}

private fun copyReportToClipboard(context: Context, report: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    val label = context.getString(R.string.crash_report_clip_label)
    clipboard.setPrimaryClip(ClipData.newPlainText(label, report))
}

/**
 * The Gitea `issues/new` URL with `title` and `body` prefilled. A full report
 * can blow past URL-length limits, so an over-long one is left out of the link
 * (with a "paste from clipboard" placeholder) — the clipboard copy is the
 * source of truth in that case.
 */
private fun buildIssueUri(context: Context, report: String) =
    context.getString(R.string.report_issue_url).toUri().buildUpon()
        .appendQueryParameter("title", context.getString(R.string.crash_report_issue_title))
        .appendQueryParameter("body", buildIssueBody(context, report))
        .build()

private fun buildIssueBody(context: Context, report: String): String {
    val block = if (report.length > MAX_URL_REPORT_CHARS) {
        context.getString(R.string.crash_report_body_paste)
    } else {
        "```\n$report\n```"
    }
    return context.getString(R.string.crash_report_body_template, block)
}

/** Keep the prefilled body comfortably under common URL-length ceilings. */
private const val MAX_URL_REPORT_CHARS = 6_000
