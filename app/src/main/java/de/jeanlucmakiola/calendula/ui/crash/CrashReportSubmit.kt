package de.jeanlucmakiola.calendula.ui.crash

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.net.toUri
import de.jeanlucmakiola.calendula.R

/**
 * Hand the captured crash report off to the user's email app: the report is
 * copied to the clipboard (a safety net) and a pre-addressed email is composed
 * with the report as the body. Nothing is sent automatically — the app has no
 * network access; the user reviews and sends the mail themselves.
 *
 * Email is used rather than the web issue tracker because filing a Gitea issue
 * requires an account on the instance, which anonymous reporters don't have —
 * whereas anyone can send an email from the app they already use.
 */
fun submitCrashReport(context: Context, report: String) {
    copyReportToClipboard(context, report)
    val subject = context.getString(R.string.crash_report_issue_title)
    val body = context.getString(R.string.crash_report_body_template, "```\n$report\n```")
    val opened = sendReportEmail(context, subject, body)
    val message = if (opened) R.string.crash_report_copied else R.string.crash_report_open_failed
    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
}

/** Compose a fresh problem-report email for a manual (non-crash) report. */
fun reportIssueByEmail(context: Context) {
    val subject = context.getString(R.string.report_issue_subject)
    val body = context.getString(R.string.report_issue_body_template)
    sendReportEmail(context, subject, body)
}

/**
 * Open the user's email app with a pre-addressed, prefilled message. The
 * `mailto:` data URI guarantees only email apps resolve the intent (a plain
 * ACTION_SEND would also offer messengers). The body rides in an extra rather
 * than the URI, so there is no URL-length ceiling on the report.
 */
private fun sendReportEmail(context: Context, subject: String, body: String): Boolean {
    val email = context.getString(R.string.report_issue_email)
    val intent = Intent(Intent.ACTION_SENDTO, "mailto:$email".toUri()).apply {
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, body)
    }
    return runCatching { context.startActivity(intent) }.isSuccess
}

private fun copyReportToClipboard(context: Context, report: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    val label = context.getString(R.string.crash_report_clip_label)
    clipboard.setPrimaryClip(ClipData.newPlainText(label, report))
}
