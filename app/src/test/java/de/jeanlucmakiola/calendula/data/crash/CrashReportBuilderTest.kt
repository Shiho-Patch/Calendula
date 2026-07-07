package de.jeanlucmakiola.calendula.data.crash

import com.google.common.truth.Truth.assertThat
import de.jeanlucmakiola.floret.crash.CrashContext
import de.jeanlucmakiola.floret.crash.buildCrashReport
import org.junit.jupiter.api.Test

/**
 * Guards the privacy contract of the report Calendula ships: the report builder
 * itself now lives in floret-kit's core-crash, but the allowlist — exactly the
 * app/Android/device/locale/time header lines plus the stack trace, and nothing
 * else — is what Calendula relies on, so it stays pinned here with Calendula's
 * own app label.
 */
class CrashReportBuilderTest {

    private val context = CrashContext(
        appVersionName = "2.7.0",
        appVersionCode = 20700,
        sdkInt = 34,
        androidRelease = "14",
        manufacturer = "Google",
        model = "Pixel 7",
        locale = "en-DE",
    )

    @Test
    fun `report carries the allowlisted facts and the stack trace`() {
        val report = buildCrashReport(context, IllegalStateException("boom"), nowMillis = 0L, appLabel = "Calendula")

        assertThat(report).startsWith("Calendula crash report")
        assertThat(report).contains("App version: 2.7.0 (20700)")
        assertThat(report).contains("Android: 14 (API 34)")
        assertThat(report).contains("Device: Google Pixel 7")
        assertThat(report).contains("Locale: en-DE")
        // The exception type + message and a frame from this test are present.
        assertThat(report).contains("IllegalStateException")
        assertThat(report).contains("boom")
        assertThat(report).contains("CrashReportBuilderTest")
    }

    @Test
    fun `nested causes are included`() {
        val cause = NullPointerException("inner")
        val report = buildCrashReport(context, RuntimeException("outer", cause), nowMillis = 0L, appLabel = "Calendula")

        assertThat(report).contains("outer")
        assertThat(report).contains("Caused by")
        assertThat(report).contains("inner")
    }

    @Test
    fun `report holds only the allowlisted lines before the stack trace`() {
        val report = buildCrashReport(context, Exception("x"), nowMillis = 0L, appLabel = "Calendula")
        val header = report.substringBefore("Stack trace:").trim().lines()

        // No identifiers, accounts, or extra fields ever creep into the header:
        // it is exactly the six allowlisted lines plus the title.
        assertThat(header).hasSize(6)
        assertThat(header.first()).isEqualTo("Calendula crash report")
        assertThat(header.map { it.substringBefore(":") }).containsExactly(
            "Calendula crash report", "App version", "Android", "Device", "Locale", "Time",
        ).inOrder()
    }
}
