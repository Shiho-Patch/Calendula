# Calendula - Plan 02: Data Layer & Permission Flow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Liefere eine voll funktionsfähige Daten-Pipeline aus dem `CalendarContract`-Provider plus den vollständigen `READ_CALENDAR`-Permission-Flow. Nach Plan 02 startet die App, fragt nach Kalender-Zugriff, und zeigt auf dem (wegwerfbaren) Debug-Screen eine Liste aller Kalender plus die nächsten 50 Event-Instanzen ab heute. `./gradlew lint test assembleDebug` ist grün, ContentObserver triggert Live-Updates, Unit-Tests decken alle defensiven Validierungen aus Spec §8 ab, ein Instrumented-Test fährt gegen den echten Provider.

**Architecture:** Drei Layer ohne Vermischung: `domain/` ist pure Kotlin (keine Android-Imports, `kotlinx.datetime` für Zeitstempel). `data/` kennt `ContentResolver`, `Cursor` und `CalendarContract`; sie übersetzt zwischen `java.time` an der Provider-Grenze und `kotlinx.datetime` im Domain. `ui/` hängt nur an Domain + Repository-Interface, niemals an `Cursor`. Repository ist `@Singleton`, hält einen `ContentObserver` auf `CalendarContract.CONTENT_URI` und re-emittiert über `SharedFlow`. App-eigene ausgeblendete Kalender-IDs liegen als `Set<Long>` in DataStore. Permission-Flow ist ein eigener Screen (Rationale → Request → Granted | Denied → Recovery), gerouted in `MainActivity` über einen einfachen `sealed`-State (keine Compose-Navigation, YAGNI).

**Tech Stack (versions verified 2026-06-08 against canonical registries):**
- `kotlinx-datetime` 0.7.0 — Domain Instant/LocalDate
- `kotlinx-coroutines-core` 1.10.2 — SharedFlow, combine, Dispatchers.IO
- `kotlinx-coroutines-test` 1.10.2 (test) — TestDispatcher, runTest
- `app.cash.turbine` 1.2.0 (test) — Flow-Assertions
- `androidx.hilt:hilt-navigation-compose` 1.3.0 — `hiltViewModel()` in Composables
- `androidx.lifecycle:lifecycle-runtime-compose` 2.10.0 — `collectAsStateWithLifecycle`
- `androidx.test:rules` 1.7.0 (androidTest) — `GrantPermissionRule`
- Existing stack from Plan 01 unchanged (Kotlin 2.3.21, AGP 9.1.1, Compose BOM 2026.05.01, Material 3 1.5.0-alpha21, Hilt 2.59.2, DataStore 1.2.1, JUnit Jupiter 6.1.0, Truth 1.4.5)

---

## File Structure

Files this plan creates or modifies (all relative to project root `/home/jlmak/Projects/jlmak/cal/`):

**Build:**
- Modify: `gradle/libs.versions.toml` — add 7 new artifacts + versions
- Modify: `app/build.gradle.kts` — wire new dependencies

**Domain (pure Kotlin):**
- Create: `app/src/main/java/de/jeanlucmakiola/calendula/domain/Models.kt`

**Data layer (`data/`):**
- Create: `app/src/main/java/de/jeanlucmakiola/calendula/data/calendar/TimeBridge.kt`
- Create: `app/src/main/java/de/jeanlucmakiola/calendula/data/calendar/Projections.kt`
- Create: `app/src/main/java/de/jeanlucmakiola/calendula/data/calendar/CalendarMapper.kt`
- Create: `app/src/main/java/de/jeanlucmakiola/calendula/data/calendar/InstanceMapper.kt`
- Create: `app/src/main/java/de/jeanlucmakiola/calendula/data/calendar/EventDetailMapper.kt`
- Create: `app/src/main/java/de/jeanlucmakiola/calendula/data/calendar/CalendarContentResolver.kt`
- Create: `app/src/main/java/de/jeanlucmakiola/calendula/data/calendar/CalendarRepository.kt`
- Create: `app/src/main/java/de/jeanlucmakiola/calendula/data/calendar/CalendarRepositoryImpl.kt`
- Create: `app/src/main/java/de/jeanlucmakiola/calendula/data/prefs/CalendarPrefs.kt`
- Create: `app/src/main/java/de/jeanlucmakiola/calendula/data/di/DataModule.kt`
- Create: `app/src/main/java/de/jeanlucmakiola/calendula/data/di/Qualifiers.kt`

**UI - Permission Flow:**
- Create: `app/src/main/java/de/jeanlucmakiola/calendula/ui/permission/PermissionUiState.kt`
- Create: `app/src/main/java/de/jeanlucmakiola/calendula/ui/permission/PermissionViewModel.kt`
- Create: `app/src/main/java/de/jeanlucmakiola/calendula/ui/permission/PermissionScreen.kt`

**UI - Debug Screen (wegwerfbar, fliegt mit Plan 03 raus):**
- Create: `app/src/main/java/de/jeanlucmakiola/calendula/ui/debug/DebugUiState.kt`
- Create: `app/src/main/java/de/jeanlucmakiola/calendula/ui/debug/DebugViewModel.kt`
- Create: `app/src/main/java/de/jeanlucmakiola/calendula/ui/debug/DebugScreen.kt`

**Entry point:**
- Modify: `app/src/main/java/de/jeanlucmakiola/calendula/MainActivity.kt` — Permission-State-Routing
- Create: `app/src/main/java/de/jeanlucmakiola/calendula/ui/RootScreen.kt`

**Resources:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-de/strings.xml`

**Unit tests (JVM, JUnit5 + Truth + Turbine):**
- Create: `app/src/test/java/de/jeanlucmakiola/calendula/data/calendar/TimeBridgeTest.kt`
- Create: `app/src/test/java/de/jeanlucmakiola/calendula/domain/ModelsTest.kt`
- Create: `app/src/test/java/de/jeanlucmakiola/calendula/data/calendar/CalendarMapperTest.kt`
- Create: `app/src/test/java/de/jeanlucmakiola/calendula/data/calendar/InstanceMapperTest.kt`
- Create: `app/src/test/java/de/jeanlucmakiola/calendula/data/calendar/EventDetailMapperTest.kt`
- Create: `app/src/test/java/de/jeanlucmakiola/calendula/data/calendar/FakeCalendarContentResolver.kt`
- Create: `app/src/test/java/de/jeanlucmakiola/calendula/data/calendar/CalendarRepositoryImplTest.kt`
- Create: `app/src/test/java/de/jeanlucmakiola/calendula/data/prefs/CalendarPrefsTest.kt`
- Create: `app/src/test/java/de/jeanlucmakiola/calendula/ui/debug/DebugViewModelTest.kt`

**Instrumented tests (androidTest, JUnit4 + Compose UI Test):**
- Create: `app/src/androidTest/java/de/jeanlucmakiola/calendula/data/calendar/CalendarRepositorySmokeTest.kt`
- Create: `app/src/androidTest/java/de/jeanlucmakiola/calendula/ui/permission/PermissionScreenTest.kt`
- Modify: `app/src/androidTest/java/de/jeanlucmakiola/calendula/MainActivitySmokeTest.kt` — replace placeholder asserts with permission-flow asserts

**Docs:**
- Modify: `CHANGELOG.md`
- Modify: `.planning/STATE.md`
- Modify: `.planning/ROADMAP.md`
- Modify: `.planning/REQUIREMENTS.md`

---

## Task 1: Add Dependencies (Catalog + Build Script)

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Append new versions and libraries to `gradle/libs.versions.toml`**

Open `gradle/libs.versions.toml`. In the `[versions]` block, add these lines (alphabetical order, after the last existing entry):

```toml
kotlinxDatetime = "0.7.0"
kotlinxCoroutines = "1.10.2"
turbine = "1.2.0"
hiltNavigationCompose = "1.3.0"
lifecycleCompose = "2.10.0"
androidxTestRules = "1.7.0"
```

In the `[libraries]` block, append these lines after the existing "Android tests" section:

```toml
# Domain time
kotlinx-datetime = { group = "org.jetbrains.kotlinx", name = "kotlinx-datetime", version.ref = "kotlinxDatetime" }

# Coroutines (transitively pulled by hilt-android, but pinned explicit)
kotlinx-coroutines-core = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-core", version.ref = "kotlinxCoroutines" }
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "kotlinxCoroutines" }

# Test - Flow assertions
turbine = { group = "app.cash.turbine", name = "turbine", version.ref = "turbine" }

# Hilt navigation-compose (for hiltViewModel() in Composables)
androidx-hilt-navigation-compose = { group = "androidx.hilt", name = "hilt-navigation-compose", version.ref = "hiltNavigationCompose" }

# Lifecycle compose (for collectAsStateWithLifecycle)
androidx-lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycleCompose" }

# Android tests - GrantPermissionRule
androidx-test-rules = { group = "androidx.test", name = "rules", version.ref = "androidxTestRules" }
```

- [ ] **Step 2: Wire new deps into `app/build.gradle.kts` and enable Android-stub defaults in unit tests**

The new tests touch `android.util.Log` (defensive logging in mappers) and `android.database.MatrixCursor`. JVM unit tests load the AGP "mockable android.jar"; we need `isReturnDefaultValues = true` so `Log.w()` no-ops instead of throwing `RuntimeException("Stub!")`. Update `testOptions` and add the new dependency lines.

In `app/build.gradle.kts`, locate the existing `testOptions` block:

```kotlin
testOptions {
    unitTests.all { it.useJUnitPlatform() }
}
```

Replace it with:

```kotlin
testOptions {
    unitTests {
        all { it.useJUnitPlatform() }
        isReturnDefaultValues = true
    }
}
```

Then in the `dependencies { ... }` block, the existing implementations stay. Append the new lines so the full dependencies block reads:

```kotlin
dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    implementation(libs.androidx.datastore.preferences)

    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.coroutines.core)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.truth)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
}
```

- [ ] **Step 3: Sync and verify catalog parses + dependency graph resolves**

Run:

```bash
./gradlew :app:dependencies --configuration debugRuntimeClasspath -q | head -60
```

Expected: a tree printing without errors, and the following groups appear somewhere in the output: `org.jetbrains.kotlinx:kotlinx-datetime:0.7.0`, `org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2`, `androidx.hilt:hilt-navigation-compose:1.3.0`, `androidx.lifecycle:lifecycle-runtime-compose:2.10.0`.

If you see `Could not resolve` or `Unresolved reference: libs.…`, you typed an artifact name wrong in the catalog. Fix and re-run.

- [ ] **Step 4: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts
git commit -m "build: add kotlinx-datetime, coroutines, turbine, hilt-nav-compose, lifecycle-compose"
```

---

## Task 2: Time Bridge (java.time ↔ kotlinx.datetime)

The provider returns `Long` epoch-millis. Domain holds `kotlinx.datetime.Instant`. One tiny module to bridge.

**Files:**
- Create: `app/src/main/java/de/jeanlucmakiola/calendula/data/calendar/TimeBridge.kt`
- Test: `app/src/test/java/de/jeanlucmakiola/calendula/data/calendar/TimeBridgeTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/de/jeanlucmakiola/calendula/data/calendar/TimeBridgeTest.kt`:

```kotlin
package de.jeanlucmakiola.calendula.data.calendar

import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Test

class TimeBridgeTest {

    @Test
    fun `epoch millis round-trips through Instant`() {
        val original = 1_717_840_800_000L // 2024-06-08T10:00:00Z
        val instant = original.toKotlinInstantFromEpochMillis()
        assertThat(instant.toEpochMillis()).isEqualTo(original)
    }

    @Test
    fun `zero millis maps to Instant epoch`() {
        assertThat(0L.toKotlinInstantFromEpochMillis()).isEqualTo(Instant.fromEpochMilliseconds(0L))
    }

    @Test
    fun `negative epoch millis is supported`() {
        val original = -1_000_000L
        assertThat(original.toKotlinInstantFromEpochMillis().toEpochMillis()).isEqualTo(original)
    }
}
```

- [ ] **Step 2: Run test - confirm it fails**

```bash
./gradlew :app:testDebugUnitTest --tests 'de.jeanlucmakiola.calendula.data.calendar.TimeBridgeTest'
```

Expected: FAIL with `Unresolved reference: toKotlinInstantFromEpochMillis` (or compilation error).

- [ ] **Step 3: Implement TimeBridge.kt**

Create `app/src/main/java/de/jeanlucmakiola/calendula/data/calendar/TimeBridge.kt`:

```kotlin
package de.jeanlucmakiola.calendula.data.calendar

import kotlinx.datetime.Instant

/**
 * Provider exposes timestamps as Long epoch-millis. Domain uses kotlinx.datetime.
 * These two extensions are the only sanctioned bridge.
 */

fun Long.toKotlinInstantFromEpochMillis(): Instant = Instant.fromEpochMilliseconds(this)

fun Instant.toEpochMillis(): Long = toEpochMilliseconds()
```

- [ ] **Step 4: Run test - confirm it passes**

```bash
./gradlew :app:testDebugUnitTest --tests 'de.jeanlucmakiola.calendula.data.calendar.TimeBridgeTest'
```

Expected: PASS, 3 tests green.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/de/jeanlucmakiola/calendula/data/calendar/TimeBridge.kt \
        app/src/test/java/de/jeanlucmakiola/calendula/data/calendar/TimeBridgeTest.kt
git commit -m "data: add TimeBridge helpers for epoch-millis ↔ kotlinx.datetime"
```

---

## Task 3: Domain Models (pure Kotlin)

All domain types in one file. Pure Kotlin, no Android imports.

**Files:**
- Create: `app/src/main/java/de/jeanlucmakiola/calendula/domain/Models.kt`
- Test: `app/src/test/java/de/jeanlucmakiola/calendula/domain/ModelsTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/de/jeanlucmakiola/calendula/domain/ModelsTest.kt`:

```kotlin
package de.jeanlucmakiola.calendula.domain

import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Test

class ModelsTest {

    @Test
    fun `CalendarSource is a data class with structural equality`() {
        val a = CalendarSource(1L, "Work", "x@y", "com.google", 0xFF112233.toInt(), true)
        val b = CalendarSource(1L, "Work", "x@y", "com.google", 0xFF112233.toInt(), true)
        assertThat(a).isEqualTo(b)
    }

    @Test
    fun `EventInstance is a data class with structural equality`() {
        val start = Instant.fromEpochMilliseconds(0L)
        val end = Instant.fromEpochMilliseconds(3_600_000L)
        val a = EventInstance(10L, 1L, 1L, "Meet", start, end, false, 0xFF000000.toInt(), null)
        val b = EventInstance(10L, 1L, 1L, "Meet", start, end, false, 0xFF000000.toInt(), null)
        assertThat(a).isEqualTo(b)
    }

    @Test
    fun `AttendeeStatus enum has all five variants`() {
        assertThat(AttendeeStatus.values().toSet()).isEqualTo(
            setOf(
                AttendeeStatus.Accepted,
                AttendeeStatus.Declined,
                AttendeeStatus.Tentative,
                AttendeeStatus.NeedsAction,
                AttendeeStatus.Unknown,
            )
        )
    }

    @Test
    fun `FailureReason enum has all five variants`() {
        assertThat(FailureReason.values().toSet()).isEqualTo(
            setOf(
                FailureReason.PermissionRevoked,
                FailureReason.NoCalendarsConfigured,
                FailureReason.ProviderUnavailable,
                FailureReason.EventNotFound,
                FailureReason.Unknown,
            )
        )
    }

    @Test
    fun `EventDetail composes EventInstance plus extras`() {
        val instance = EventInstance(
            instanceId = 10L,
            eventId = 1L,
            calendarId = 1L,
            title = "Meet",
            start = Instant.fromEpochMilliseconds(0L),
            end = Instant.fromEpochMilliseconds(60_000L),
            isAllDay = false,
            color = 0xFFAABBCC.toInt(),
            location = null,
        )
        val detail = EventDetail(
            instance = instance,
            description = "Brief description",
            organizer = "x@y",
            attendees = listOf(Attendee("Alice", "alice@x", AttendeeStatus.Accepted)),
            rrule = "FREQ=WEEKLY",
        )
        assertThat(detail.instance.title).isEqualTo("Meet")
        assertThat(detail.attendees).hasSize(1)
    }
}
```

- [ ] **Step 2: Run test - confirm it fails**

```bash
./gradlew :app:testDebugUnitTest --tests 'de.jeanlucmakiola.calendula.domain.ModelsTest'
```

Expected: FAIL with `Unresolved reference: CalendarSource` (or similar).

- [ ] **Step 3: Implement domain models**

Create `app/src/main/java/de/jeanlucmakiola/calendula/domain/Models.kt`:

```kotlin
package de.jeanlucmakiola.calendula.domain

import kotlinx.datetime.Instant

/**
 * A configured calendar source on the device (e.g. a Nextcloud DAVx5 calendar,
 * a Google account calendar, or a local calendar). Maps 1:1 to a row in
 * CalendarContract.Calendars.
 */
data class CalendarSource(
    val id: Long,
    val displayName: String,
    val accountName: String,
    val accountType: String,
    val color: Int,
    val isVisibleInSystem: Boolean,
)

/**
 * One concrete occurrence of an event in time. For non-recurring events the
 * instanceId is unique per event. For recurring events there is one
 * EventInstance per occurrence within the queried time range; eventId is
 * shared across all occurrences of the same event.
 *
 * color is the effective color: event.color if set, else calendar.color.
 */
data class EventInstance(
    val instanceId: Long,
    val eventId: Long,
    val calendarId: Long,
    val title: String,
    val start: Instant,
    val end: Instant,
    val isAllDay: Boolean,
    val color: Int,
    val location: String?,
)

/**
 * Read-only detail of a single event. `instance` is reused here for the basic
 * fields; description / organizer / attendees / rrule are detail-only.
 */
data class EventDetail(
    val instance: EventInstance,
    val description: String?,
    val organizer: String?,
    val attendees: List<Attendee>,
    val rrule: String?,
)

data class Attendee(
    val name: String,
    val email: String?,
    val status: AttendeeStatus,
)

enum class AttendeeStatus {
    Accepted,
    Declined,
    Tentative,
    NeedsAction,
    Unknown,
}

/**
 * Why a screen ended up in Failure state. Each screen interprets these into a
 * concrete recovery action (re-request permission, open system settings, retry).
 */
enum class FailureReason {
    PermissionRevoked,
    NoCalendarsConfigured,
    ProviderUnavailable,
    EventNotFound,
    Unknown,
}
```

- [ ] **Step 4: Run test - confirm it passes**

```bash
./gradlew :app:testDebugUnitTest --tests 'de.jeanlucmakiola.calendula.domain.ModelsTest'
```

Expected: PASS, 5 tests green.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/de/jeanlucmakiola/calendula/domain/Models.kt \
        app/src/test/java/de/jeanlucmakiola/calendula/domain/ModelsTest.kt
git commit -m "domain: add pure-Kotlin models (CalendarSource, EventInstance, EventDetail, …)"
```

---

## Task 4: CalendarContract Projections

The exact column names + projection-index constants the rest of `data/calendar/` relies on. No tests — projections are constants.

**Files:**
- Create: `app/src/main/java/de/jeanlucmakiola/calendula/data/calendar/Projections.kt`

- [ ] **Step 1: Create `Projections.kt`**

```kotlin
package de.jeanlucmakiola.calendula.data.calendar

import android.provider.CalendarContract

/**
 * Frozen column projections + their stable indices, indexed-array style.
 *
 * Rule: the projection array and the *_IDX constants must move together. If
 * you reorder one, reorder the other.
 */

internal object CalendarProjection {
    val COLUMNS: Array<String> = arrayOf(
        CalendarContract.Calendars._ID,                 // 0
        CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, // 1
        CalendarContract.Calendars.ACCOUNT_NAME,         // 2
        CalendarContract.Calendars.ACCOUNT_TYPE,         // 3
        CalendarContract.Calendars.CALENDAR_COLOR,       // 4
        CalendarContract.Calendars.VISIBLE,              // 5
    )

    const val IDX_ID = 0
    const val IDX_DISPLAY_NAME = 1
    const val IDX_ACCOUNT_NAME = 2
    const val IDX_ACCOUNT_TYPE = 3
    const val IDX_COLOR = 4
    const val IDX_VISIBLE = 5
}

internal object InstanceProjection {
    val COLUMNS: Array<String> = arrayOf(
        CalendarContract.Instances._ID,           // 0
        CalendarContract.Instances.EVENT_ID,      // 1
        CalendarContract.Instances.CALENDAR_ID,   // 2
        CalendarContract.Instances.TITLE,         // 3
        CalendarContract.Instances.BEGIN,         // 4
        CalendarContract.Instances.END,           // 5
        CalendarContract.Instances.ALL_DAY,       // 6
        CalendarContract.Instances.EVENT_COLOR,   // 7
        CalendarContract.Instances.CALENDAR_COLOR,// 8
        CalendarContract.Instances.EVENT_LOCATION,// 9
    )

    const val IDX_INSTANCE_ID = 0
    const val IDX_EVENT_ID = 1
    const val IDX_CALENDAR_ID = 2
    const val IDX_TITLE = 3
    const val IDX_BEGIN = 4
    const val IDX_END = 5
    const val IDX_ALL_DAY = 6
    const val IDX_EVENT_COLOR = 7
    const val IDX_CALENDAR_COLOR = 8
    const val IDX_LOCATION = 9
}

internal object EventDetailProjection {
    val COLUMNS: Array<String> = arrayOf(
        CalendarContract.Events._ID,              // 0
        CalendarContract.Events.TITLE,            // 1
        CalendarContract.Events.DESCRIPTION,      // 2
        CalendarContract.Events.ORGANIZER,        // 3
        CalendarContract.Events.RRULE,            // 4
        CalendarContract.Events.EVENT_COLOR,      // 5
        CalendarContract.Events.CALENDAR_COLOR,   // 6
        CalendarContract.Events.DTSTART,          // 7
        CalendarContract.Events.DTEND,            // 8
        CalendarContract.Events.ALL_DAY,          // 9
        CalendarContract.Events.EVENT_LOCATION,   // 10
        CalendarContract.Events.CALENDAR_ID,      // 11
    )

    const val IDX_EVENT_ID = 0
    const val IDX_TITLE = 1
    const val IDX_DESCRIPTION = 2
    const val IDX_ORGANIZER = 3
    const val IDX_RRULE = 4
    const val IDX_EVENT_COLOR = 5
    const val IDX_CALENDAR_COLOR = 6
    const val IDX_DTSTART = 7
    const val IDX_DTEND = 8
    const val IDX_ALL_DAY = 9
    const val IDX_LOCATION = 10
    const val IDX_CALENDAR_ID = 11
}

internal object AttendeeProjection {
    val COLUMNS: Array<String> = arrayOf(
        CalendarContract.Attendees.ATTENDEE_NAME,   // 0
        CalendarContract.Attendees.ATTENDEE_EMAIL,  // 1
        CalendarContract.Attendees.ATTENDEE_STATUS, // 2
    )

    const val IDX_NAME = 0
    const val IDX_EMAIL = 1
    const val IDX_STATUS = 2
}

/**
 * Fallback labels for null/empty fields. Localized strings would couple
 * data layer to UI resources — keep these in-language hardcoded; the UI
 * never displays them to a human-facing label without going through ui/
 * (which can choose to override them via stringResource if it wants).
 *
 * For V1 we leave them as-is; users see them only in the Debug screen which
 * is wegwerfbar anyway.
 */
internal object Fallbacks {
    const val UNNAMED_CALENDAR = "(Unbenannter Kalender)"
    const val UNTITLED_EVENT = "(Ohne Titel)"
}
```

- [ ] **Step 2: Compile-check**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/de/jeanlucmakiola/calendula/data/calendar/Projections.kt
git commit -m "data: add CalendarContract column projections + indices"
```

---

## Task 5: Calendar Cursor Mapper

**Files:**
- Create: `app/src/main/java/de/jeanlucmakiola/calendula/data/calendar/CalendarMapper.kt`
- Test: `app/src/test/java/de/jeanlucmakiola/calendula/data/calendar/CalendarMapperTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package de.jeanlucmakiola.calendula.data.calendar

import android.database.MatrixCursor
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class CalendarMapperTest {

    private fun cursorOf(vararg rows: Array<Any?>): MatrixCursor {
        val c = MatrixCursor(CalendarProjection.COLUMNS)
        rows.forEach { c.addRow(it) }
        return c
    }

    @Test
    fun `happy path maps all six columns`() {
        val cur = cursorOf(
            arrayOf<Any?>(42L, "Work", "x@y", "com.google", 0xFF112233.toInt(), 1)
        )
        cur.moveToFirst()
        val src = cur.toCalendarSource()
        assertThat(src).isEqualTo(
            de.jeanlucmakiola.calendula.domain.CalendarSource(
                id = 42L,
                displayName = "Work",
                accountName = "x@y",
                accountType = "com.google",
                color = 0xFF112233.toInt(),
                isVisibleInSystem = true,
            )
        )
    }

    @Test
    fun `null displayName falls back to placeholder`() {
        val cur = cursorOf(
            arrayOf<Any?>(7L, null, "x@y", "LOCAL", 0xFF000000.toInt(), 1)
        )
        cur.moveToFirst()
        val src = cur.toCalendarSource()
        assertThat(src!!.displayName).isEqualTo(Fallbacks.UNNAMED_CALENDAR)
    }

    @Test
    fun `visible flag 0 maps to false`() {
        val cur = cursorOf(
            arrayOf<Any?>(1L, "Hidden", "x@y", "LOCAL", 0, 0)
        )
        cur.moveToFirst()
        assertThat(cur.toCalendarSource()!!.isVisibleInSystem).isFalse()
    }

    @Test
    fun `empty cursor returns null when called without moveToFirst`() {
        val cur = cursorOf()
        // No moveToFirst → cursor before first; mapper should not crash, return null
        assertThat(cur.toCalendarSource()).isNull()
    }
}
```

- [ ] **Step 2: Run test - confirm it fails**

```bash
./gradlew :app:testDebugUnitTest --tests 'de.jeanlucmakiola.calendula.data.calendar.CalendarMapperTest'
```

Expected: FAIL with `Unresolved reference: toCalendarSource`.

- [ ] **Step 3: Implement `CalendarMapper.kt`**

```kotlin
package de.jeanlucmakiola.calendula.data.calendar

import android.database.Cursor
import de.jeanlucmakiola.calendula.domain.CalendarSource

/**
 * Maps the cursor's CURRENT row to a CalendarSource. The caller is responsible
 * for cursor positioning. Returns null when the cursor is not positioned on a
 * valid row.
 *
 * Defensive fallback: null displayName → "(Unbenannter Kalender)".
 */
internal fun Cursor.toCalendarSource(): CalendarSource? {
    if (isBeforeFirst || isAfterLast) return null
    return CalendarSource(
        id = getLong(CalendarProjection.IDX_ID),
        displayName = getString(CalendarProjection.IDX_DISPLAY_NAME)
            ?: Fallbacks.UNNAMED_CALENDAR,
        accountName = getString(CalendarProjection.IDX_ACCOUNT_NAME).orEmpty(),
        accountType = getString(CalendarProjection.IDX_ACCOUNT_TYPE).orEmpty(),
        color = getInt(CalendarProjection.IDX_COLOR),
        isVisibleInSystem = getInt(CalendarProjection.IDX_VISIBLE) != 0,
    )
}
```

- [ ] **Step 4: Run test - confirm it passes**

```bash
./gradlew :app:testDebugUnitTest --tests 'de.jeanlucmakiola.calendula.data.calendar.CalendarMapperTest'
```

Expected: PASS, 4 tests green.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/de/jeanlucmakiola/calendula/data/calendar/CalendarMapper.kt \
        app/src/test/java/de/jeanlucmakiola/calendula/data/calendar/CalendarMapperTest.kt
git commit -m "data: add Cursor.toCalendarSource() mapper with defensive fallback"
```

---

## Task 6: Instance Cursor Mapper (with defensive validation per Spec §8)

Every defensive case from Spec §8 has its own test.

**Files:**
- Create: `app/src/main/java/de/jeanlucmakiola/calendula/data/calendar/InstanceMapper.kt`
- Test: `app/src/test/java/de/jeanlucmakiola/calendula/data/calendar/InstanceMapperTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package de.jeanlucmakiola.calendula.data.calendar

import android.database.MatrixCursor
import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Test

class InstanceMapperTest {

    private fun cursorOf(vararg rows: Array<Any?>): MatrixCursor {
        val c = MatrixCursor(InstanceProjection.COLUMNS)
        rows.forEach { c.addRow(it) }
        return c
    }

    /**
     * Convenience: returns a row matching InstanceProjection.COLUMNS with all
     * non-required fields defaulted to known-good values. Callers override only
     * the field they care about.
     */
    private fun row(
        instanceId: Long = 10L,
        eventId: Long = 1L,
        calendarId: Long = 1L,
        title: String? = "Meet",
        begin: Long = 1_000_000_000L,
        end: Long = 1_000_003_600L,
        allDay: Int = 0,
        eventColor: Any? = null,            // null OR Int
        calendarColor: Int = 0xFFAABBCC.toInt(),
        location: String? = null,
    ): Array<Any?> = arrayOf(
        instanceId, eventId, calendarId, title, begin, end, allDay,
        eventColor, calendarColor, location,
    )

    @Test
    fun `happy path - non-allday event`() {
        val cur = cursorOf(row())
        cur.moveToFirst()
        val inst = cur.toEventInstance()
        assertThat(inst).isNotNull()
        assertThat(inst!!.title).isEqualTo("Meet")
        assertThat(inst.isAllDay).isFalse()
        assertThat(inst.start).isEqualTo(Instant.fromEpochMilliseconds(1_000_000_000L))
        assertThat(inst.end).isEqualTo(Instant.fromEpochMilliseconds(1_000_003_600L))
    }

    @Test
    fun `event color falls back to calendar color when null`() {
        val cur = cursorOf(row(eventColor = null, calendarColor = 0xFF112233.toInt()))
        cur.moveToFirst()
        assertThat(cur.toEventInstance()!!.color).isEqualTo(0xFF112233.toInt())
    }

    @Test
    fun `event color wins over calendar color when present`() {
        val cur = cursorOf(
            row(eventColor = 0xFFDEADBE.toInt(), calendarColor = 0xFF112233.toInt())
        )
        cur.moveToFirst()
        assertThat(cur.toEventInstance()!!.color).isEqualTo(0xFFDEADBE.toInt())
    }

    @Test
    fun `null title falls back to placeholder`() {
        val cur = cursorOf(row(title = null))
        cur.moveToFirst()
        assertThat(cur.toEventInstance()!!.title).isEqualTo(Fallbacks.UNTITLED_EVENT)
    }

    @Test
    fun `empty title falls back to placeholder`() {
        val cur = cursorOf(row(title = ""))
        cur.moveToFirst()
        assertThat(cur.toEventInstance()!!.title).isEqualTo(Fallbacks.UNTITLED_EVENT)
    }

    @Test
    fun `dtend before dtstart drops the row`() {
        val cur = cursorOf(row(begin = 2000L, end = 1000L))
        cur.moveToFirst()
        assertThat(cur.toEventInstance()).isNull()
    }

    @Test
    fun `dtstart before unix epoch drops the row`() {
        val cur = cursorOf(row(begin = -1L, end = 1000L))
        cur.moveToFirst()
        assertThat(cur.toEventInstance()).isNull()
    }

    @Test
    fun `all-day flag 1 maps to true`() {
        val cur = cursorOf(row(allDay = 1))
        cur.moveToFirst()
        assertThat(cur.toEventInstance()!!.isAllDay).isTrue()
    }

    @Test
    fun `location passes through when present`() {
        val cur = cursorOf(row(location = "Berlin"))
        cur.moveToFirst()
        assertThat(cur.toEventInstance()!!.location).isEqualTo("Berlin")
    }
}
```

- [ ] **Step 2: Run test - confirm it fails**

```bash
./gradlew :app:testDebugUnitTest --tests 'de.jeanlucmakiola.calendula.data.calendar.InstanceMapperTest'
```

Expected: FAIL with `Unresolved reference: toEventInstance`.

- [ ] **Step 3: Implement `InstanceMapper.kt`**

```kotlin
package de.jeanlucmakiola.calendula.data.calendar

import android.database.Cursor
import android.util.Log
import de.jeanlucmakiola.calendula.domain.EventInstance

private const val TAG = "InstanceMapper"

/**
 * Maps the cursor's CURRENT row to an EventInstance, or returns null if the
 * row fails defensive validation (per Spec §8). Callers are expected to
 * filterNotNull() on the resulting list.
 */
internal fun Cursor.toEventInstance(): EventInstance? {
    if (isBeforeFirst || isAfterLast) return null

    val begin = getLong(InstanceProjection.IDX_BEGIN)
    val end = getLong(InstanceProjection.IDX_END)

    // Defensive: dtstart < epoch → drop
    if (begin < 0L) {
        Log.w(TAG, "Dropping row with negative begin=$begin")
        return null
    }

    // Defensive: dtend < dtstart → drop
    if (end < begin) {
        Log.w(TAG, "Dropping row with end=$end < begin=$begin")
        return null
    }

    val rawTitle = getString(InstanceProjection.IDX_TITLE)
    val title = if (rawTitle.isNullOrEmpty()) Fallbacks.UNTITLED_EVENT else rawTitle

    // Effective color: event color wins, else calendar color.
    val color = if (isNull(InstanceProjection.IDX_EVENT_COLOR)) {
        getInt(InstanceProjection.IDX_CALENDAR_COLOR)
    } else {
        getInt(InstanceProjection.IDX_EVENT_COLOR)
    }

    return EventInstance(
        instanceId = getLong(InstanceProjection.IDX_INSTANCE_ID),
        eventId = getLong(InstanceProjection.IDX_EVENT_ID),
        calendarId = getLong(InstanceProjection.IDX_CALENDAR_ID),
        title = title,
        start = begin.toKotlinInstantFromEpochMillis(),
        end = end.toKotlinInstantFromEpochMillis(),
        isAllDay = getInt(InstanceProjection.IDX_ALL_DAY) != 0,
        color = color,
        location = getString(InstanceProjection.IDX_LOCATION),
    )
}
```

- [ ] **Step 4: Run test - confirm it passes**

```bash
./gradlew :app:testDebugUnitTest --tests 'de.jeanlucmakiola.calendula.data.calendar.InstanceMapperTest'
```

Expected: PASS, 9 tests green.

`Log.w` calls inside `InstanceMapper` are no-ops in the JVM tests because Task 1 already set `testOptions.unitTests.isReturnDefaultValues = true`. If you skipped that change, revisit Task 1.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/de/jeanlucmakiola/calendula/data/calendar/InstanceMapper.kt \
        app/src/test/java/de/jeanlucmakiola/calendula/data/calendar/InstanceMapperTest.kt
git commit -m "data: add Cursor.toEventInstance() with defensive validation (§8)"
```

---

## Task 7: Event Detail + Attendee Mapper

**Files:**
- Create: `app/src/main/java/de/jeanlucmakiola/calendula/data/calendar/EventDetailMapper.kt`
- Test: `app/src/test/java/de/jeanlucmakiola/calendula/data/calendar/EventDetailMapperTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package de.jeanlucmakiola.calendula.data.calendar

import android.database.MatrixCursor
import android.provider.CalendarContract
import com.google.common.truth.Truth.assertThat
import de.jeanlucmakiola.calendula.domain.AttendeeStatus
import org.junit.jupiter.api.Test

class EventDetailMapperTest {

    private fun detailCursor(
        eventId: Long = 1L,
        title: String? = "Meet",
        description: String? = "Body",
        organizer: String? = "x@y",
        rrule: String? = null,
        eventColor: Any? = null,
        calendarColor: Int = 0xFFAABBCC.toInt(),
        dtstart: Long = 1_000_000_000L,
        dtend: Long = 1_000_003_600L,
        allDay: Int = 0,
        location: String? = "Berlin",
        calendarId: Long = 7L,
    ): MatrixCursor {
        val c = MatrixCursor(EventDetailProjection.COLUMNS)
        c.addRow(
            arrayOf<Any?>(
                eventId, title, description, organizer, rrule,
                eventColor, calendarColor, dtstart, dtend, allDay, location, calendarId,
            )
        )
        return c
    }

    private fun attendeeCursor(vararg attendees: Array<Any?>): MatrixCursor {
        val c = MatrixCursor(AttendeeProjection.COLUMNS)
        attendees.forEach { c.addRow(it) }
        return c
    }

    @Test
    fun `happy path detail maps all fields and embeds matching EventInstance`() {
        val cur = detailCursor()
        cur.moveToFirst()
        val detail = cur.toEventDetailCore(attendees = emptyList())
        assertThat(detail).isNotNull()
        assertThat(detail!!.description).isEqualTo("Body")
        assertThat(detail.organizer).isEqualTo("x@y")
        assertThat(detail.instance.title).isEqualTo("Meet")
        assertThat(detail.instance.location).isEqualTo("Berlin")
        assertThat(detail.attendees).isEmpty()
    }

    @Test
    fun `event color falls back to calendar color when null`() {
        val cur = detailCursor(eventColor = null, calendarColor = 0xFF112233.toInt())
        cur.moveToFirst()
        val detail = cur.toEventDetailCore(attendees = emptyList())
        assertThat(detail!!.instance.color).isEqualTo(0xFF112233.toInt())
    }

    @Test
    fun `dtend before dtstart drops detail`() {
        val cur = detailCursor(dtstart = 2000L, dtend = 1000L)
        cur.moveToFirst()
        assertThat(cur.toEventDetailCore(attendees = emptyList())).isNull()
    }

    @Test
    fun `rrule passes through when present`() {
        val cur = detailCursor(rrule = "FREQ=WEEKLY;BYDAY=MO")
        cur.moveToFirst()
        assertThat(cur.toEventDetailCore(attendees = emptyList())!!.rrule)
            .isEqualTo("FREQ=WEEKLY;BYDAY=MO")
    }

    @Test
    fun `attendee status mapping covers all five variants`() {
        val cur = attendeeCursor(
            arrayOf<Any?>("Alice", "alice@x", CalendarContract.Attendees.ATTENDEE_STATUS_ACCEPTED),
            arrayOf<Any?>("Bob", "bob@x", CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED),
            arrayOf<Any?>("Carol", "carol@x", CalendarContract.Attendees.ATTENDEE_STATUS_TENTATIVE),
            arrayOf<Any?>("Dave", "dave@x", CalendarContract.Attendees.ATTENDEE_STATUS_INVITED),
            arrayOf<Any?>("Eve", "eve@x", 42), // Unknown int code
            arrayOf<Any?>(null, null, CalendarContract.Attendees.ATTENDEE_STATUS_NONE),
        )

        val results = mutableListOf<AttendeeStatus>()
        cur.moveToFirst()
        do {
            results += cur.toAttendee().status
        } while (cur.moveToNext())

        assertThat(results).containsExactly(
            AttendeeStatus.Accepted,
            AttendeeStatus.Declined,
            AttendeeStatus.Tentative,
            AttendeeStatus.NeedsAction,
            AttendeeStatus.Unknown,
            AttendeeStatus.Unknown,
        ).inOrder()
    }

    @Test
    fun `attendee with null name maps to empty string`() {
        val cur = attendeeCursor(
            arrayOf<Any?>(null, "alice@x", CalendarContract.Attendees.ATTENDEE_STATUS_ACCEPTED),
        )
        cur.moveToFirst()
        assertThat(cur.toAttendee().name).isEqualTo("")
    }
}
```

- [ ] **Step 2: Run test - confirm it fails**

```bash
./gradlew :app:testDebugUnitTest --tests 'de.jeanlucmakiola.calendula.data.calendar.EventDetailMapperTest'
```

Expected: FAIL with `Unresolved reference: toEventDetailCore` (or `toAttendee`).

- [ ] **Step 3: Implement `EventDetailMapper.kt`**

```kotlin
package de.jeanlucmakiola.calendula.data.calendar

import android.database.Cursor
import android.provider.CalendarContract
import android.util.Log
import de.jeanlucmakiola.calendula.domain.Attendee
import de.jeanlucmakiola.calendula.domain.AttendeeStatus
import de.jeanlucmakiola.calendula.domain.EventDetail
import de.jeanlucmakiola.calendula.domain.EventInstance

private const val TAG = "EventDetailMapper"

/**
 * Maps the cursor's CURRENT row (from an Events query using EventDetailProjection)
 * to an EventDetail. Attendees are loaded separately (CalendarContract requires
 * a different query) and passed in.
 *
 * Returns null when defensive validation fails (e.g. dtend < dtstart).
 */
internal fun Cursor.toEventDetailCore(attendees: List<Attendee>): EventDetail? {
    if (isBeforeFirst || isAfterLast) return null

    val begin = getLong(EventDetailProjection.IDX_DTSTART)
    val end = getLong(EventDetailProjection.IDX_DTEND)

    if (begin < 0L) {
        Log.w(TAG, "Dropping event with negative dtstart=$begin")
        return null
    }
    if (end < begin) {
        Log.w(TAG, "Dropping event with dtend=$end < dtstart=$begin")
        return null
    }

    val rawTitle = getString(EventDetailProjection.IDX_TITLE)
    val title = if (rawTitle.isNullOrEmpty()) Fallbacks.UNTITLED_EVENT else rawTitle

    val color = if (isNull(EventDetailProjection.IDX_EVENT_COLOR)) {
        getInt(EventDetailProjection.IDX_CALENDAR_COLOR)
    } else {
        getInt(EventDetailProjection.IDX_EVENT_COLOR)
    }

    val eventId = getLong(EventDetailProjection.IDX_EVENT_ID)
    val instance = EventInstance(
        instanceId = eventId, // For non-recurring detail, instance == event.
        eventId = eventId,
        calendarId = getLong(EventDetailProjection.IDX_CALENDAR_ID),
        title = title,
        start = begin.toKotlinInstantFromEpochMillis(),
        end = end.toKotlinInstantFromEpochMillis(),
        isAllDay = getInt(EventDetailProjection.IDX_ALL_DAY) != 0,
        color = color,
        location = getString(EventDetailProjection.IDX_LOCATION),
    )

    return EventDetail(
        instance = instance,
        description = getString(EventDetailProjection.IDX_DESCRIPTION),
        organizer = getString(EventDetailProjection.IDX_ORGANIZER),
        attendees = attendees,
        rrule = getString(EventDetailProjection.IDX_RRULE),
    )
}

/**
 * Maps the cursor's CURRENT row (from an Attendees query using AttendeeProjection)
 * to an Attendee. Defensive: null name → "".
 */
internal fun Cursor.toAttendee(): Attendee = Attendee(
    name = getString(AttendeeProjection.IDX_NAME).orEmpty(),
    email = getString(AttendeeProjection.IDX_EMAIL),
    status = mapAttendeeStatus(getInt(AttendeeProjection.IDX_STATUS)),
)

private fun mapAttendeeStatus(raw: Int): AttendeeStatus = when (raw) {
    CalendarContract.Attendees.ATTENDEE_STATUS_ACCEPTED -> AttendeeStatus.Accepted
    CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED -> AttendeeStatus.Declined
    CalendarContract.Attendees.ATTENDEE_STATUS_TENTATIVE -> AttendeeStatus.Tentative
    CalendarContract.Attendees.ATTENDEE_STATUS_INVITED -> AttendeeStatus.NeedsAction
    else -> AttendeeStatus.Unknown
}
```

- [ ] **Step 4: Run test - confirm it passes**

```bash
./gradlew :app:testDebugUnitTest --tests 'de.jeanlucmakiola.calendula.data.calendar.EventDetailMapperTest'
```

Expected: PASS, 6 tests green.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/de/jeanlucmakiola/calendula/data/calendar/EventDetailMapper.kt \
        app/src/test/java/de/jeanlucmakiola/calendula/data/calendar/EventDetailMapperTest.kt
git commit -m "data: add Cursor.toEventDetailCore() and Cursor.toAttendee() mappers"
```

---

## Task 8: CalendarContentResolver Interface + Android Impl

A thin wrapper to make the repository testable without a real `ContentResolver`.

**Files:**
- Create: `app/src/main/java/de/jeanlucmakiola/calendula/data/calendar/CalendarContentResolver.kt`

- [ ] **Step 1: Create the interface and Android implementation**

```kotlin
package de.jeanlucmakiola.calendula.data.calendar

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.database.Cursor
import android.provider.CalendarContract
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin seam over Android's ContentResolver, so the repository can be unit-tested
 * with an in-memory fake.
 *
 * All query methods return a raw Cursor — the caller (CalendarRepositoryImpl) is
 * responsible for `use { … }` to close them and for iterating + mapping.
 */
interface CalendarContentResolver {
    fun queryCalendars(): Cursor?
    fun queryInstances(beginMillis: Long, endMillis: Long): Cursor?
    fun queryEvent(eventId: Long): Cursor?
    fun queryAttendees(eventId: Long): Cursor?
    fun registerObserver(observer: ContentObserver)
    fun unregisterObserver(observer: ContentObserver)
}

@Singleton
class AndroidCalendarContentResolver @Inject constructor(
    @ApplicationContext private val context: Context,
) : CalendarContentResolver {

    private val resolver: ContentResolver get() = context.contentResolver

    override fun queryCalendars(): Cursor? = resolver.query(
        CalendarContract.Calendars.CONTENT_URI,
        CalendarProjection.COLUMNS,
        null, null,
        CalendarContract.Calendars.CALENDAR_DISPLAY_NAME + " ASC",
    )

    override fun queryInstances(beginMillis: Long, endMillis: Long): Cursor? {
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().apply {
            ContentUris.appendId(this, beginMillis)
            ContentUris.appendId(this, endMillis)
        }.build()
        return resolver.query(
            uri,
            InstanceProjection.COLUMNS,
            null, null,
            CalendarContract.Instances.BEGIN + " ASC",
        )
    }

    override fun queryEvent(eventId: Long): Cursor? = resolver.query(
        ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId),
        EventDetailProjection.COLUMNS,
        null, null, null,
    )

    override fun queryAttendees(eventId: Long): Cursor? = resolver.query(
        CalendarContract.Attendees.CONTENT_URI,
        AttendeeProjection.COLUMNS,
        CalendarContract.Attendees.EVENT_ID + " = ?",
        arrayOf(eventId.toString()),
        null,
    )

    override fun registerObserver(observer: ContentObserver) {
        resolver.registerContentObserver(
            CalendarContract.CONTENT_URI,
            /* notifyForDescendants = */ true,
            observer,
        )
    }

    override fun unregisterObserver(observer: ContentObserver) {
        resolver.unregisterContentObserver(observer)
    }
}
```

- [ ] **Step 2: Compile-check**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/de/jeanlucmakiola/calendula/data/calendar/CalendarContentResolver.kt
git commit -m "data: add CalendarContentResolver seam over ContentResolver"
```

---

## Task 9: CalendarRepository Interface + Impl (with ContentObserver + SharedFlow)

The heart of the data layer. Hilt-Singleton, holds one ContentObserver, re-emits whenever the provider notifies a change.

**Files:**
- Create: `app/src/main/java/de/jeanlucmakiola/calendula/data/calendar/CalendarRepository.kt`
- Create: `app/src/main/java/de/jeanlucmakiola/calendula/data/calendar/CalendarRepositoryImpl.kt`
- Create: `app/src/main/java/de/jeanlucmakiola/calendula/data/di/Qualifiers.kt`
- Create: `app/src/test/java/de/jeanlucmakiola/calendula/data/calendar/FakeCalendarContentResolver.kt`
- Test: `app/src/test/java/de/jeanlucmakiola/calendula/data/calendar/CalendarRepositoryImplTest.kt`

- [ ] **Step 1: Create the `@IoDispatcher` qualifier**

`app/src/main/java/de/jeanlucmakiola/calendula/data/di/Qualifiers.kt`:

```kotlin
package de.jeanlucmakiola.calendula.data.di

import javax.inject.Qualifier

/**
 * Marks the IO-bound CoroutineDispatcher (Dispatchers.IO) for Hilt injection,
 * separate from any default/Main dispatchers we may bind later.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher
```

- [ ] **Step 2: Define the repository interface**

`app/src/main/java/de/jeanlucmakiola/calendula/data/calendar/CalendarRepository.kt`:

```kotlin
package de.jeanlucmakiola.calendula.data.calendar

import de.jeanlucmakiola.calendula.domain.CalendarSource
import de.jeanlucmakiola.calendula.domain.EventDetail
import de.jeanlucmakiola.calendula.domain.EventInstance
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

/**
 * The single read-side surface against CalendarContract. Implementations
 * are responsible for ContentObserver wiring and re-emitting fresh data.
 *
 * Methods return Flow so consumers automatically pick up external changes
 * (DAVx5 sync, user edits in Google Calendar, …) via the observer.
 */
interface CalendarRepository {

    /**
     * All configured calendar sources, sorted by display name ascending.
     * Emits a new value after every provider change.
     */
    fun calendars(): Flow<List<CalendarSource>>

    /**
     * All event instances overlapping the given inclusive instant range,
     * sorted by start ascending. Recurrence expansion is done by the provider.
     */
    fun instances(range: ClosedRange<Instant>): Flow<List<EventInstance>>

    /**
     * One-shot lookup of an event's read-only detail (including attendees).
     * Throws NoSuchEventException if the event id is gone or invalid.
     */
    suspend fun eventDetail(eventId: Long): EventDetail
}

class NoSuchEventException(eventId: Long) :
    NoSuchElementException("No event with id=$eventId")
```

- [ ] **Step 3: Implement the repository**

`app/src/main/java/de/jeanlucmakiola/calendula/data/calendar/CalendarRepositoryImpl.kt`:

```kotlin
package de.jeanlucmakiola.calendula.data.calendar

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import de.jeanlucmakiola.calendula.data.di.IoDispatcher
import de.jeanlucmakiola.calendula.domain.Attendee
import de.jeanlucmakiola.calendula.domain.CalendarSource
import de.jeanlucmakiola.calendula.domain.EventDetail
import de.jeanlucmakiola.calendula.domain.EventInstance
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository impl. One ContentObserver lives for the lifetime of the
 * Application process (we never unregister — the receiver is also process-bound).
 *
 * Public flows: re-query on subscription, then re-query whenever the observer
 * fires a tick. Tick stream is a Buffered SharedFlow so multiple concurrent
 * subscribers all see the same update.
 */
@Singleton
class CalendarRepositoryImpl @Inject constructor(
    private val resolver: CalendarContentResolver,
    @IoDispatcher private val io: CoroutineDispatcher,
) : CalendarRepository {

    private val ticks = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 1,
    )

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            ticks.tryEmit(Unit)
        }
    }

    init {
        resolver.registerObserver(observer)
    }

    override fun calendars(): Flow<List<CalendarSource>> =
        ticks
            .onStart { emit(Unit) }
            .mapLatestList { queryCalendars() }
            .flowOn(io)

    override fun instances(range: ClosedRange<Instant>): Flow<List<EventInstance>> =
        ticks
            .onStart { emit(Unit) }
            .mapLatestList {
                queryInstances(
                    beginMillis = range.start.toEpochMillis(),
                    endMillis = range.endInclusive.toEpochMillis(),
                )
            }
            .flowOn(io)

    override suspend fun eventDetail(eventId: Long): EventDetail = withContext(io) {
        val attendees = readAttendees(eventId)
        val cursor = resolver.queryEvent(eventId)
            ?: throw NoSuchEventException(eventId)
        cursor.use {
            if (!it.moveToFirst()) throw NoSuchEventException(eventId)
            it.toEventDetailCore(attendees)
                ?: throw NoSuchEventException(eventId)
        }
    }

    private fun queryCalendars(): List<CalendarSource> =
        resolver.queryCalendars()?.use { c ->
            val out = mutableListOf<CalendarSource>()
            while (c.moveToNext()) c.toCalendarSource()?.let(out::add)
            out
        } ?: emptyList()

    private fun queryInstances(beginMillis: Long, endMillis: Long): List<EventInstance> =
        resolver.queryInstances(beginMillis, endMillis)?.use { c ->
            val out = mutableListOf<EventInstance>()
            while (c.moveToNext()) c.toEventInstance()?.let(out::add)
            out
        } ?: emptyList()

    private fun readAttendees(eventId: Long): List<Attendee> =
        resolver.queryAttendees(eventId)?.use { c ->
            val out = mutableListOf<Attendee>()
            while (c.moveToNext()) out += c.toAttendee()
            out
        } ?: emptyList()
}

/**
 * Helper: re-runs `block` on every emission of the upstream Flow<Unit>.
 * Equivalent to flatMapLatest { flow { emit(block()) } } but simpler.
 */
private fun <T> Flow<Unit>.mapLatestList(block: suspend () -> List<T>): Flow<List<T>> =
    flow {
        collect { emit(block()) }
    }
```

- [ ] **Step 4: Create the FakeCalendarContentResolver helper for tests**

`app/src/test/java/de/jeanlucmakiola/calendula/data/calendar/FakeCalendarContentResolver.kt`:

```kotlin
package de.jeanlucmakiola.calendula.data.calendar

import android.database.ContentObserver
import android.database.Cursor
import android.database.MatrixCursor

/**
 * Test-only fake. Allows you to seed in-memory Matrix cursors per query type,
 * and to "tick" the observer manually to simulate provider changes.
 */
internal class FakeCalendarContentResolver : CalendarContentResolver {

    var calendarsCursorFactory: () -> Cursor? = { MatrixCursor(CalendarProjection.COLUMNS) }
    var instancesCursorFactory: (Long, Long) -> Cursor? = { _, _ ->
        MatrixCursor(InstanceProjection.COLUMNS)
    }
    var eventCursorFactory: (Long) -> Cursor? = { MatrixCursor(EventDetailProjection.COLUMNS) }
    var attendeesCursorFactory: (Long) -> Cursor? = { MatrixCursor(AttendeeProjection.COLUMNS) }

    private val observers = mutableListOf<ContentObserver>()

    override fun queryCalendars(): Cursor? = calendarsCursorFactory()
    override fun queryInstances(beginMillis: Long, endMillis: Long): Cursor? =
        instancesCursorFactory(beginMillis, endMillis)
    override fun queryEvent(eventId: Long): Cursor? = eventCursorFactory(eventId)
    override fun queryAttendees(eventId: Long): Cursor? = attendeesCursorFactory(eventId)

    override fun registerObserver(observer: ContentObserver) {
        observers += observer
    }

    override fun unregisterObserver(observer: ContentObserver) {
        observers -= observer
    }

    /** Manually trigger every registered observer (simulates a provider change). */
    fun tick() {
        observers.forEach { it.onChange(/* selfChange = */ false) }
    }
}
```

- [ ] **Step 5: Write the failing repository test**

`app/src/test/java/de/jeanlucmakiola/calendula/data/calendar/CalendarRepositoryImplTest.kt`:

```kotlin
package de.jeanlucmakiola.calendula.data.calendar

import android.database.MatrixCursor
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Test

class CalendarRepositoryImplTest {

    private fun calCursor(vararg rows: Array<Any?>): MatrixCursor {
        val c = MatrixCursor(CalendarProjection.COLUMNS)
        rows.forEach { c.addRow(it) }
        return c
    }

    private fun instCursor(vararg rows: Array<Any?>): MatrixCursor {
        val c = MatrixCursor(InstanceProjection.COLUMNS)
        rows.forEach { c.addRow(it) }
        return c
    }

    private fun calRow(id: Long, name: String) =
        arrayOf<Any?>(id, name, "x@y", "LOCAL", 0xFF112233.toInt(), 1)

    private fun instRow(
        instanceId: Long,
        title: String,
        begin: Long = 1_000_000_000L,
        end: Long = 1_000_003_600L,
    ) = arrayOf<Any?>(
        instanceId, instanceId, /* calendarId = */ 1L, title,
        begin, end, 0, null, 0xFFAABBCC.toInt(), null,
    )

    @Test
    fun `calendars emits initial query result on subscribe`() = runTest {
        val fake = FakeCalendarContentResolver().apply {
            calendarsCursorFactory = { calCursor(calRow(1L, "A"), calRow(2L, "B")) }
        }
        val repo = CalendarRepositoryImpl(fake, UnconfinedTestDispatcher(testScheduler))

        repo.calendars().test {
            val first = awaitItem()
            assertThat(first.map { it.id }).containsExactly(1L, 2L).inOrder()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `calendars re-emits after observer tick`() = runTest {
        var rows = listOf(calRow(1L, "A"))
        val fake = FakeCalendarContentResolver().apply {
            calendarsCursorFactory = { calCursor(*rows.toTypedArray()) }
        }
        val repo = CalendarRepositoryImpl(fake, UnconfinedTestDispatcher(testScheduler))

        repo.calendars().test {
            assertThat(awaitItem().map { it.id }).containsExactly(1L)

            rows = listOf(calRow(1L, "A"), calRow(2L, "B"))
            fake.tick()

            assertThat(awaitItem().map { it.id }).containsExactly(1L, 2L).inOrder()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `instances queries with correct epoch-millis bounds`() = runTest {
        var observedBegin: Long? = null
        var observedEnd: Long? = null
        val fake = FakeCalendarContentResolver().apply {
            instancesCursorFactory = { b, e ->
                observedBegin = b
                observedEnd = e
                instCursor(instRow(10L, "X"))
            }
        }
        val repo = CalendarRepositoryImpl(fake, UnconfinedTestDispatcher(testScheduler))

        val range = Instant.fromEpochMilliseconds(1_000L)..Instant.fromEpochMilliseconds(2_000L)
        repo.instances(range).test {
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(observedBegin).isEqualTo(1_000L)
        assertThat(observedEnd).isEqualTo(2_000L)
    }

    @Test
    fun `instances drops rows that fail defensive validation`() = runTest {
        val fake = FakeCalendarContentResolver().apply {
            instancesCursorFactory = { _, _ ->
                instCursor(
                    instRow(10L, "Good"),
                    instRow(11L, "BadOrder", begin = 5000L, end = 1000L),
                )
            }
        }
        val repo = CalendarRepositoryImpl(fake, UnconfinedTestDispatcher(testScheduler))

        val range = Instant.fromEpochMilliseconds(0)..Instant.fromEpochMilliseconds(10_000L)
        repo.instances(range).test {
            val first = awaitItem()
            assertThat(first.map { it.title }).containsExactly("Good")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `eventDetail throws when cursor is empty`() = runTest {
        val fake = FakeCalendarContentResolver().apply {
            eventCursorFactory = { MatrixCursor(EventDetailProjection.COLUMNS) }
        }
        val repo = CalendarRepositoryImpl(fake, Dispatchers.Unconfined)

        try {
            repo.eventDetail(eventId = 999L)
            error("Expected NoSuchEventException")
        } catch (expected: NoSuchEventException) {
            assertThat(expected.message).contains("999")
        }
    }
}
```

- [ ] **Step 6: Run test - confirm it fails (it depends on Steps 2-3 actually existing)**

```bash
./gradlew :app:testDebugUnitTest --tests 'de.jeanlucmakiola.calendula.data.calendar.CalendarRepositoryImplTest'
```

Expected: PASS if Steps 2-3 (interface + impl) were saved correctly. If you see `Unresolved reference: CalendarRepositoryImpl` or similar, recheck that the files in Steps 2-3 were written. The test asserts behavior that the impl already provides.

If the test legitimately fails (e.g. Turbine timeout), recheck `mapLatestList` for a bug; the correct shape is the helper in CalendarRepositoryImpl.kt that re-collects.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/de/jeanlucmakiola/calendula/data/di/Qualifiers.kt \
        app/src/main/java/de/jeanlucmakiola/calendula/data/calendar/CalendarRepository.kt \
        app/src/main/java/de/jeanlucmakiola/calendula/data/calendar/CalendarRepositoryImpl.kt \
        app/src/test/java/de/jeanlucmakiola/calendula/data/calendar/FakeCalendarContentResolver.kt \
        app/src/test/java/de/jeanlucmakiola/calendula/data/calendar/CalendarRepositoryImplTest.kt
git commit -m "data: add CalendarRepository + Impl with ContentObserver-backed SharedFlow"
```

---

## Task 10: DataStore — hiddenCalendarIds

DataStore for the app-side "user has hidden these calendars" preference. Stored as a comma-separated string (DataStore Preferences has no `Set<Long>` primitive).

**Files:**
- Create: `app/src/main/java/de/jeanlucmakiola/calendula/data/prefs/CalendarPrefs.kt`
- Test: `app/src/test/java/de/jeanlucmakiola/calendula/data/prefs/CalendarPrefsTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package de.jeanlucmakiola.calendula.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class CalendarPrefsTest {

    private fun newDataStore(tempDir: Path): DataStore<Preferences> {
        return PreferenceDataStoreFactory.create(
            produceFile = { tempDir.resolve("test_prefs.preferences_pb").toFile() },
        )
    }

    @Test
    fun `hiddenCalendarIds defaults to empty when unset`(@TempDir tempDir: Path) = runTest {
        val prefs = CalendarPrefs(newDataStore(tempDir))
        assertThat(prefs.hiddenCalendarIds.first()).isEmpty()
    }

    @Test
    fun `setHiddenCalendarIds round-trips through DataStore`(@TempDir tempDir: Path) = runTest {
        val store = newDataStore(tempDir)
        val prefs = CalendarPrefs(store)
        prefs.setHiddenCalendarIds(setOf(1L, 42L, 7L))
        assertThat(prefs.hiddenCalendarIds.first()).isEqualTo(setOf(1L, 42L, 7L))
    }

    @Test
    fun `setting empty set clears storage`(@TempDir tempDir: Path) = runTest {
        val prefs = CalendarPrefs(newDataStore(tempDir))
        prefs.setHiddenCalendarIds(setOf(1L))
        prefs.setHiddenCalendarIds(emptySet())
        assertThat(prefs.hiddenCalendarIds.first()).isEmpty()
    }

    @Test
    fun `garbage stored string is parsed defensively`(@TempDir tempDir: Path) = runTest {
        // Older builds (or a corrupted prefs file) might leave invalid tokens.
        // The flow should defensively skip them.
        val store = newDataStore(tempDir)
        val prefs = CalendarPrefs(store)
        // Use the public API to write a "good" set then directly inject a tampered key
        prefs.setHiddenCalendarIds(setOf(1L, 2L))
        // Simulate corruption by writing a manual value with bad tokens
        store.updateData { p ->
            val mutable = p.toMutablePreferences()
            mutable[CalendarPrefs.HIDDEN_IDS_KEY] = "1,abc,3"
            mutable
        }
        assertThat(prefs.hiddenCalendarIds.first()).isEqualTo(setOf(1L, 3L))
    }
}
```

- [ ] **Step 2: Run test - confirm it fails**

```bash
./gradlew :app:testDebugUnitTest --tests 'de.jeanlucmakiola.calendula.data.prefs.CalendarPrefsTest'
```

Expected: FAIL with `Unresolved reference: CalendarPrefs`.

- [ ] **Step 3: Implement `CalendarPrefs.kt`**

```kotlin
package de.jeanlucmakiola.calendula.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-side preference for "calendars the user has hidden in this app",
 * separate from the system's per-calendar VISIBLE flag.
 *
 * Persisted as a comma-separated string of Long ids; non-numeric tokens are
 * silently skipped (defensive — see CalendarPrefsTest).
 */
@Singleton
class CalendarPrefs @Inject constructor(
    private val store: DataStore<Preferences>,
) {

    val hiddenCalendarIds: Flow<Set<Long>> = store.data.map { prefs ->
        prefs[HIDDEN_IDS_KEY].orEmpty()
            .split(',')
            .mapNotNull { it.trim().toLongOrNull() }
            .toSet()
    }

    suspend fun setHiddenCalendarIds(ids: Set<Long>) {
        store.edit { prefs ->
            if (ids.isEmpty()) {
                prefs.remove(HIDDEN_IDS_KEY)
            } else {
                prefs[HIDDEN_IDS_KEY] = ids.sorted().joinToString(",")
            }
        }
    }

    companion object {
        internal val HIDDEN_IDS_KEY = stringPreferencesKey("hidden_calendar_ids")
    }
}
```

- [ ] **Step 4: Run test - confirm it passes**

```bash
./gradlew :app:testDebugUnitTest --tests 'de.jeanlucmakiola.calendula.data.prefs.CalendarPrefsTest'
```

Expected: PASS, 4 tests green.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/de/jeanlucmakiola/calendula/data/prefs/CalendarPrefs.kt \
        app/src/test/java/de/jeanlucmakiola/calendula/data/prefs/CalendarPrefsTest.kt
git commit -m "data: add CalendarPrefs (hidden calendar ids in DataStore)"
```

---

## Task 11: Hilt Data Module

Bind interfaces to implementations + provide the DataStore singleton + IO dispatcher.

**Files:**
- Create: `app/src/main/java/de/jeanlucmakiola/calendula/data/di/DataModule.kt`

- [ ] **Step 1: Create `DataModule.kt`**

```kotlin
package de.jeanlucmakiola.calendula.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import de.jeanlucmakiola.calendula.data.calendar.AndroidCalendarContentResolver
import de.jeanlucmakiola.calendula.data.calendar.CalendarContentResolver
import de.jeanlucmakiola.calendula.data.calendar.CalendarRepository
import de.jeanlucmakiola.calendula.data.calendar.CalendarRepositoryImpl
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Singleton

private val Context.calendulaDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "calendula_prefs",
)

@Module
@InstallIn(SingletonComponent::class)
abstract class DataBindModule {

    @Binds
    @Singleton
    abstract fun bindCalendarContentResolver(
        impl: AndroidCalendarContentResolver,
    ): CalendarContentResolver

    @Binds
    @Singleton
    abstract fun bindCalendarRepository(
        impl: CalendarRepositoryImpl,
    ): CalendarRepository
}

@Module
@InstallIn(SingletonComponent::class)
object DataProvideModule {

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.calendulaDataStore

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
}
```

- [ ] **Step 2: Compile-check**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL. KSP runs Hilt and generates the singleton component.

If you see `Hilt: ... requires @Singleton` or a similar Hilt error, double-check that `CalendarRepositoryImpl` and `AndroidCalendarContentResolver` are annotated `@Singleton` (Tasks 8 and 9).

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/de/jeanlucmakiola/calendula/data/di/DataModule.kt
git commit -m "di: wire CalendarRepository, ContentResolver, DataStore, IoDispatcher"
```

---

## Task 12: i18n Strings for Permission + Debug

Append the new keys to both `values/strings.xml` and `values-de/strings.xml`. Keep ordering consistent across both files.

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-de/strings.xml`

- [ ] **Step 1: Append new keys to `values/strings.xml`**

The full file should now read:

```xml
<resources>
    <string name="app_name">Calendula</string>
    <string name="app_tagline">A modern calendar.</string>

    <!-- Loading / Failure / Success generic strings (used across screens) -->
    <string name="state_loading">Loading…</string>
    <string name="state_retry">Retry</string>
    <string name="state_failure_unknown">Something went wrong.</string>
    <string name="state_failure_permission">Calendar access is required.</string>
    <string name="state_failure_permission_action">Grant access</string>
    <string name="state_failure_no_calendars">No calendars configured.</string>
    <string name="state_failure_no_calendars_action">Open system calendar settings</string>
    <string name="state_failure_provider">Could not read the calendar.</string>

    <!-- Permission flow (F1) -->
    <string name="permission_rationale_title">Calendar access</string>
    <string name="permission_rationale_body">Calendula reads only your device calendar — no data leaves your device.</string>
    <string name="permission_request_button">Continue</string>
    <string name="permission_denied_title">Calendar access denied</string>
    <string name="permission_denied_body">Calendula cannot show events without calendar access. You can grant it again in the system settings.</string>
    <string name="permission_open_settings_button">Open system settings</string>
    <string name="permission_retry_button">Try again</string>

    <!-- Debug screen (wegwerfbar — entfällt mit Plan 03) -->
    <string name="debug_banner">DEBUG — replaced by month view in Plan 03</string>
    <string name="debug_calendars_header">Calendars</string>
    <string name="debug_events_header">Next 50 events</string>
    <string name="debug_no_calendars">No calendars configured. Add one via DAVx5 or system settings.</string>
    <string name="debug_no_events">No upcoming events in the next 30 days.</string>
</resources>
```

- [ ] **Step 2: Append new keys to `values-de/strings.xml`**

The full German file:

```xml
<resources>
    <string name="app_name">Calendula</string>
    <string name="app_tagline">Ein moderner Kalender.</string>

    <string name="state_loading">Lädt…</string>
    <string name="state_retry">Erneut versuchen</string>
    <string name="state_failure_unknown">Etwas ist schiefgelaufen.</string>
    <string name="state_failure_permission">Zugriff auf den Kalender wird benötigt.</string>
    <string name="state_failure_permission_action">Zugriff erlauben</string>
    <string name="state_failure_no_calendars">Keine Kalender eingerichtet.</string>
    <string name="state_failure_no_calendars_action">System-Kalender-Einstellungen öffnen</string>
    <string name="state_failure_provider">Kalender konnte nicht gelesen werden.</string>

    <!-- Permission-Flow (F1) -->
    <string name="permission_rationale_title">Kalender-Zugriff</string>
    <string name="permission_rationale_body">Calendula liest nur deinen Gerätekalender — keine Daten verlassen das Gerät.</string>
    <string name="permission_request_button">Weiter</string>
    <string name="permission_denied_title">Kalender-Zugriff abgelehnt</string>
    <string name="permission_denied_body">Ohne Kalender-Zugriff kann Calendula keine Termine anzeigen. Du kannst den Zugriff in den System-Einstellungen wieder erlauben.</string>
    <string name="permission_open_settings_button">System-Einstellungen öffnen</string>
    <string name="permission_retry_button">Erneut versuchen</string>

    <!-- Debug-Screen (wegwerfbar — entfällt mit Plan 03) -->
    <string name="debug_banner">DEBUG — wird mit Plan 03 durch die Monatsansicht ersetzt</string>
    <string name="debug_calendars_header">Kalender</string>
    <string name="debug_events_header">Nächste 50 Termine</string>
    <string name="debug_no_calendars">Keine Kalender eingerichtet. Füge einen über DAVx5 oder die System-Einstellungen hinzu.</string>
    <string name="debug_no_events">Keine anstehenden Termine in den nächsten 30 Tagen.</string>
</resources>
```

- [ ] **Step 3: Compile-check (verify lint sees both locales as complete)**

```bash
./gradlew :app:lintDebug
```

Expected: BUILD SUCCESSFUL. If lint complains about missing translations, the two files have drifted — re-verify every `<string name="…">` appears in both files.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/res/values/strings.xml app/src/main/res/values-de/strings.xml
git commit -m "i18n: add permission + debug screen strings (en, de)"
```

---

## Task 13: PermissionUiState + ViewModel

`PermissionViewModel` exposes a small state that the UI dispatches on. Permission-status checks against the system happen through helper methods called by the activity (the ViewModel is otherwise context-free).

**Files:**
- Create: `app/src/main/java/de/jeanlucmakiola/calendula/ui/permission/PermissionUiState.kt`
- Create: `app/src/main/java/de/jeanlucmakiola/calendula/ui/permission/PermissionViewModel.kt`
- Test: tests for the state machine happen via UI test in Task 14 (PermissionScreenTest)

- [ ] **Step 1: Define the UI state**

```kotlin
package de.jeanlucmakiola.calendula.ui.permission

/**
 * Permission flow state. The screen lives in one of these three at any time.
 *
 * - Rationale: first-time prompt; user has not yet seen the system dialog
 * - Denied: user said no — show recovery (retry or open settings)
 * - Granted: terminal — screen calls onGranted and unmounts
 */
sealed interface PermissionUiState {
    data object Rationale : PermissionUiState
    data object Denied : PermissionUiState
    data object Granted : PermissionUiState
}
```

- [ ] **Step 2: Implement the ViewModel**

```kotlin
package de.jeanlucmakiola.calendula.ui.permission

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class PermissionViewModel @Inject constructor() : ViewModel() {

    private val _state = MutableStateFlow<PermissionUiState>(PermissionUiState.Rationale)
    val state: StateFlow<PermissionUiState> = _state.asStateFlow()

    fun onGranted() {
        _state.value = PermissionUiState.Granted
    }

    fun onDenied() {
        _state.value = PermissionUiState.Denied
    }

    fun onRetry() {
        // Move back to Rationale → the screen will trigger the system dialog again
        _state.value = PermissionUiState.Rationale
    }
}
```

- [ ] **Step 3: Compile-check**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/de/jeanlucmakiola/calendula/ui/permission/PermissionUiState.kt \
        app/src/main/java/de/jeanlucmakiola/calendula/ui/permission/PermissionViewModel.kt
git commit -m "ui: add PermissionViewModel with three-state machine"
```

---

## Task 14: Permission Screen Composable

The first screen the user sees when permission is not granted. Hands off `onGranted` upward when permission is acquired.

**Files:**
- Create: `app/src/main/java/de/jeanlucmakiola/calendula/ui/permission/PermissionScreen.kt`
- Test: `app/src/androidTest/java/de/jeanlucmakiola/calendula/ui/permission/PermissionScreenTest.kt`

- [ ] **Step 1: Implement `PermissionScreen.kt`**

```kotlin
package de.jeanlucmakiola.calendula.ui.permission

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.jeanlucmakiola.calendula.R

@Composable
fun PermissionScreen(
    onGranted: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PermissionViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) viewModel.onGranted() else viewModel.onDenied()
    }

    LaunchedEffect(state) {
        if (state == PermissionUiState.Granted) onGranted()
    }

    when (state) {
        is PermissionUiState.Rationale -> RationaleContent(
            onRequest = { launcher.launch(Manifest.permission.READ_CALENDAR) },
            modifier = modifier,
        )
        is PermissionUiState.Denied -> DeniedContent(
            onRetry = {
                viewModel.onRetry()
                launcher.launch(Manifest.permission.READ_CALENDAR)
            },
            modifier = modifier,
        )
        is PermissionUiState.Granted -> {
            // Transient — the LaunchedEffect above fires and the parent
            // composable will replace us. Render nothing.
        }
    }
}

@Composable
private fun RationaleContent(
    onRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.permission_rationale_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.permission_rationale_body),
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(32.dp))
        Button(onClick = onRequest) {
            Text(stringResource(R.string.permission_request_button))
        }
    }
}

@Composable
private fun DeniedContent(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.permission_denied_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.permission_denied_body),
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(32.dp))
        Button(onClick = onRetry) {
            Text(stringResource(R.string.permission_retry_button))
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            },
        ) {
            Text(stringResource(R.string.permission_open_settings_button))
        }
    }
}
```

- [ ] **Step 2: Write the instrumented test**

`app/src/androidTest/java/de/jeanlucmakiola/calendula/ui/permission/PermissionScreenTest.kt`:

```kotlin
package de.jeanlucmakiola.calendula.ui.permission

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.jeanlucmakiola.calendula.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PermissionScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val res = InstrumentationRegistry.getInstrumentation().targetContext.resources

    @Test
    fun rationale_renders_title_and_button() {
        composeTestRule.setContent {
            PermissionScreen(onGranted = {})
        }
        composeTestRule.onNodeWithText(res.getString(R.string.permission_rationale_title))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(res.getString(R.string.permission_request_button))
            .assertIsDisplayed()
    }
}
```

- [ ] **Step 3: Run the test on an emulator**

```bash
./gradlew :app:connectedDebugAndroidTest --tests 'de.jeanlucmakiola.calendula.ui.permission.PermissionScreenTest'
```

Expected: PASS, 1 test green. Skip this step if no emulator is connected locally; CI will run it.

- [ ] **Step 4: Compile-check the main source**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/de/jeanlucmakiola/calendula/ui/permission/PermissionScreen.kt \
        app/src/androidTest/java/de/jeanlucmakiola/calendula/ui/permission/PermissionScreenTest.kt
git commit -m "ui: add PermissionScreen with rationale and denied recovery"
```

---

## Task 15: Debug ViewModel + State

The Debug ViewModel combines `repository.calendars()` and `repository.instances(today..today+30d)` into one state.

**Files:**
- Create: `app/src/main/java/de/jeanlucmakiola/calendula/ui/debug/DebugUiState.kt`
- Create: `app/src/main/java/de/jeanlucmakiola/calendula/ui/debug/DebugViewModel.kt`
- Test: `app/src/test/java/de/jeanlucmakiola/calendula/ui/debug/DebugViewModelTest.kt`

- [ ] **Step 1: Define the UI state**

```kotlin
package de.jeanlucmakiola.calendula.ui.debug

import de.jeanlucmakiola.calendula.domain.CalendarSource
import de.jeanlucmakiola.calendula.domain.EventInstance
import de.jeanlucmakiola.calendula.domain.FailureReason

sealed interface DebugUiState {
    data object Loading : DebugUiState
    data class Failure(val reason: FailureReason) : DebugUiState
    data class Success(
        val calendars: List<CalendarSource>,
        val nextEvents: List<EventInstance>,
    ) : DebugUiState
}
```

- [ ] **Step 2: Write the failing test**

`app/src/test/java/de/jeanlucmakiola/calendula/ui/debug/DebugViewModelTest.kt`:

```kotlin
package de.jeanlucmakiola.calendula.ui.debug

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import de.jeanlucmakiola.calendula.data.calendar.CalendarRepository
import de.jeanlucmakiola.calendula.data.calendar.NoSuchEventException
import de.jeanlucmakiola.calendula.domain.CalendarSource
import de.jeanlucmakiola.calendula.domain.EventDetail
import de.jeanlucmakiola.calendula.domain.EventInstance
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Test

class DebugViewModelTest {

    private class FakeRepo(
        val calendarsFlow: MutableStateFlow<List<CalendarSource>> = MutableStateFlow(emptyList()),
        val instancesFlow: MutableStateFlow<List<EventInstance>> = MutableStateFlow(emptyList()),
    ) : CalendarRepository {
        override fun calendars(): Flow<List<CalendarSource>> = calendarsFlow
        override fun instances(range: ClosedRange<Instant>): Flow<List<EventInstance>> = instancesFlow
        override suspend fun eventDetail(eventId: Long): EventDetail =
            throw NoSuchEventException(eventId)
    }

    private fun makeCal(id: Long, name: String) =
        CalendarSource(id, name, "x@y", "LOCAL", 0xFF112233.toInt(), true)

    private fun makeEvent(id: Long, title: String) = EventInstance(
        instanceId = id, eventId = id, calendarId = 1L,
        title = title,
        start = Instant.fromEpochMilliseconds(0L),
        end = Instant.fromEpochMilliseconds(60_000L),
        isAllDay = false, color = 0xFF000000.toInt(), location = null,
    )

    @Test
    fun `initial state is Loading then Success`() = runTest {
        val repo = FakeRepo()
        val vm = DebugViewModel(repo, UnconfinedTestDispatcher(testScheduler))
        vm.state.test {
            assertThat(awaitItem()).isEqualTo(DebugUiState.Loading)

            repo.calendarsFlow.value = listOf(makeCal(1L, "A"))
            repo.instancesFlow.value = listOf(makeEvent(10L, "X"))

            val success = awaitItem() as DebugUiState.Success
            assertThat(success.calendars.map { it.id }).containsExactly(1L)
            assertThat(success.nextEvents.map { it.title }).containsExactly("X")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `instances are capped at 50`() = runTest {
        val repo = FakeRepo()
        val vm = DebugViewModel(repo, UnconfinedTestDispatcher(testScheduler))
        vm.state.test {
            awaitItem() // Loading

            repo.calendarsFlow.value = listOf(makeCal(1L, "A"))
            repo.instancesFlow.value = (1L..100L).map { makeEvent(it, "E$it") }

            val success = awaitItem() as DebugUiState.Success
            assertThat(success.nextEvents).hasSize(50)
            assertThat(success.nextEvents.first().instanceId).isEqualTo(1L)
            assertThat(success.nextEvents.last().instanceId).isEqualTo(50L)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
```

- [ ] **Step 3: Run test - confirm it fails**

```bash
./gradlew :app:testDebugUnitTest --tests 'de.jeanlucmakiola.calendula.ui.debug.DebugViewModelTest'
```

Expected: FAIL with `Unresolved reference: DebugViewModel`.

- [ ] **Step 4: Implement the ViewModel**

```kotlin
package de.jeanlucmakiola.calendula.ui.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.jeanlucmakiola.calendula.data.calendar.CalendarRepository
import de.jeanlucmakiola.calendula.data.di.IoDispatcher
import de.jeanlucmakiola.calendula.domain.FailureReason
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.Clock
import kotlin.time.Duration.Companion.days
import javax.inject.Inject

private const val MAX_DEBUG_EVENTS = 50
private val DEBUG_WINDOW = 30.days

@HiltViewModel
class DebugViewModel @Inject constructor(
    private val repository: CalendarRepository,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {

    val state: StateFlow<DebugUiState> = run {
        val now = Clock.System.now()
        val range = now..(now + DEBUG_WINDOW)
        combine(
            repository.calendars(),
            repository.instances(range),
        ) { calendars, instances ->
            DebugUiState.Success(
                calendars = calendars,
                nextEvents = instances.take(MAX_DEBUG_EVENTS),
            ) as DebugUiState
        }
            .catch { emit(DebugUiState.Failure(FailureReason.ProviderUnavailable)) }
            .flowOn(io)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000L),
                initialValue = DebugUiState.Loading,
            )
    }
}
```

- [ ] **Step 5: Run test - confirm it passes**

```bash
./gradlew :app:testDebugUnitTest --tests 'de.jeanlucmakiola.calendula.ui.debug.DebugViewModelTest'
```

Expected: PASS, 2 tests green.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/de/jeanlucmakiola/calendula/ui/debug/DebugUiState.kt \
        app/src/main/java/de/jeanlucmakiola/calendula/ui/debug/DebugViewModel.kt \
        app/src/test/java/de/jeanlucmakiola/calendula/ui/debug/DebugViewModelTest.kt
git commit -m "ui: add DebugViewModel combining calendars + next 30d instances"
```

---

## Task 16: Debug Screen Composable

The screen visually validates that data is flowing.

**Files:**
- Create: `app/src/main/java/de/jeanlucmakiola/calendula/ui/debug/DebugScreen.kt`

- [ ] **Step 1: Implement `DebugScreen.kt`**

```kotlin
package de.jeanlucmakiola.calendula.ui.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.jeanlucmakiola.calendula.R
import de.jeanlucmakiola.calendula.domain.CalendarSource
import de.jeanlucmakiola.calendula.domain.EventInstance
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun DebugScreen(
    modifier: Modifier = Modifier,
    viewModel: DebugViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(modifier = modifier.fillMaxSize()) {
        DebugBanner()
        when (val s = state) {
            DebugUiState.Loading -> LoadingContent()
            is DebugUiState.Failure -> FailureContent()
            is DebugUiState.Success -> SuccessContent(s)
        }
    }
}

@Composable
private fun DebugBanner() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = stringResource(R.string.debug_banner),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
    }
}

@Composable
private fun LoadingContent() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun FailureContent() {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.state_failure_provider),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun SuccessContent(state: DebugUiState.Success) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item { SectionHeader(stringResource(R.string.debug_calendars_header)) }
        if (state.calendars.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.debug_no_calendars),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            items(state.calendars, key = { it.id }) { CalendarRow(it) }
        }

        item { Spacer(Modifier.height(16.dp)) }
        item { SectionHeader(stringResource(R.string.debug_events_header)) }

        if (state.nextEvents.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.debug_no_events),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            items(state.nextEvents, key = { it.instanceId }) { EventRow(it) }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(text = text, style = MaterialTheme.typography.titleMedium)
        HorizontalDivider()
    }
}

@Composable
private fun CalendarRow(cal: CalendarSource) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(Color(cal.color), CircleShape),
        )
        Text(
            text = "  ${cal.displayName}  (${cal.accountName})",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 20.dp),
        )
    }
}

@Composable
private fun EventRow(event: EventInstance) {
    val zone = TimeZone.currentSystemDefault()
    val start = event.start.toLocalDateTime(zone)
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(text = event.title, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = formatStart(start),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun formatStart(start: kotlinx.datetime.LocalDateTime): String {
    val date = "%04d-%02d-%02d".format(start.year, start.monthNumber, start.dayOfMonth)
    val time = "%02d:%02d".format(start.hour, start.minute)
    return "$date  $time"
}
```

- [ ] **Step 2: Compile-check**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/de/jeanlucmakiola/calendula/ui/debug/DebugScreen.kt
git commit -m "ui: add DebugScreen showing calendars + next 50 instances"
```

---

## Task 17: RootScreen + MainActivity Routing

Replace the Plan 01 placeholder. `MainActivity` now defers all routing to a single `RootScreen` composable that reads system permission state and chooses between `PermissionScreen` and `DebugScreen`.

**Files:**
- Create: `app/src/main/java/de/jeanlucmakiola/calendula/ui/RootScreen.kt`
- Modify: `app/src/main/java/de/jeanlucmakiola/calendula/MainActivity.kt`

- [ ] **Step 1: Create `RootScreen.kt`**

```kotlin
package de.jeanlucmakiola.calendula.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import de.jeanlucmakiola.calendula.ui.debug.DebugScreen
import de.jeanlucmakiola.calendula.ui.permission.PermissionScreen

@Composable
fun RootScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR)
                == PackageManager.PERMISSION_GRANTED
        )
    }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasPermission = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.READ_CALENDAR
                ) == PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycle.addObserver(obs)
    }

    Scaffold(modifier = modifier) { innerPadding ->
        if (hasPermission) {
            DebugScreen(modifier = Modifier.padding(innerPadding))
        } else {
            PermissionScreen(
                onGranted = { hasPermission = true },
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}
```

- [ ] **Step 2: Rewrite `MainActivity.kt`**

Replace the existing `MainActivity.kt` with:

```kotlin
package de.jeanlucmakiola.calendula

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import dagger.hilt.android.AndroidEntryPoint
import de.jeanlucmakiola.calendula.ui.RootScreen
import de.jeanlucmakiola.calendula.ui.theme.CalendulaTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CalendulaTheme {
                RootScreen(modifier = Modifier.fillMaxSize())
            }
        }
    }
}
```

The Plan 01 `PlaceholderScreen` and `PlaceholderPreview` composables are removed entirely.

- [ ] **Step 3: Compile-check**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL. If you see `Unresolved reference: app_tagline`, that is fine — it was used only in the removed `PlaceholderScreen`. The string itself stays in `strings.xml` (other features may use it later).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/de/jeanlucmakiola/calendula/ui/RootScreen.kt \
        app/src/main/java/de/jeanlucmakiola/calendula/MainActivity.kt
git commit -m "ui: replace placeholder with RootScreen routing permission ↔ debug"
```

---

## Task 18: Update MainActivitySmokeTest

Plan 01's smoke test asserts on the placeholder's text. That text no longer renders. Replace with a smoke that runs without `READ_CALENDAR` granted (no `@get:Rule GrantPermissionRule`), confirming the permission rationale shows.

**Files:**
- Modify: `app/src/androidTest/java/de/jeanlucmakiola/calendula/MainActivitySmokeTest.kt`

- [ ] **Step 1: Replace `MainActivitySmokeTest.kt`**

Full file:

```kotlin
package de.jeanlucmakiola.calendula

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Smoke: launches MainActivity and asserts the permission rationale renders
 * when calendar access has not yet been granted. Without GrantPermissionRule
 * the system reports NOT GRANTED on first launch so we land in PermissionScreen.
 */
@RunWith(AndroidJUnit4::class)
class MainActivitySmokeTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private val res = InstrumentationRegistry.getInstrumentation().targetContext.resources

    @Test
    fun permissionRationale_isDisplayed_onLaunch_withoutPermission() {
        composeTestRule.onNodeWithText(res.getString(R.string.permission_rationale_title))
            .assertIsDisplayed()
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/androidTest/java/de/jeanlucmakiola/calendula/MainActivitySmokeTest.kt
git commit -m "test: replace placeholder smoke with permission-rationale assert"
```

---

## Task 19: Instrumented Repository Smoke Test

A single end-to-end smoke against the real `CalendarContract` provider on the emulator. Uses `GrantPermissionRule` so we can call the repository without manually clicking the dialog.

**Files:**
- Create: `app/src/androidTest/java/de/jeanlucmakiola/calendula/data/calendar/CalendarRepositorySmokeTest.kt`

- [ ] **Step 1: Create the test**

```kotlin
package de.jeanlucmakiola.calendula.data.calendar

import android.Manifest
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Duration.Companion.days

/**
 * Smoke test against the real ContentResolver. Verifies that the repository
 * returns sane (possibly empty) lists and does not crash on a clean emulator.
 *
 * The emulator may or may not have any calendars configured — both outcomes
 * are valid for this test. We only assert "did not throw" and "returns a list".
 */
@RunWith(AndroidJUnit4::class)
class CalendarRepositorySmokeTest {

    @get:Rule
    val permissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.READ_CALENDAR)

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun newRepo(): CalendarRepositoryImpl {
        val resolver = AndroidCalendarContentResolver(context)
        return CalendarRepositoryImpl(resolver, Dispatchers.IO)
    }

    @Test
    fun calendars_returnsListWithoutCrashing() = runBlocking {
        val repo = newRepo()
        val first = repo.calendars().first()
        assertThat(first).isNotNull()
    }

    @Test
    fun instances_returnsListWithoutCrashing() = runBlocking {
        val repo = newRepo()
        val now = Clock.System.now()
        val first = repo.instances(now..(now + 1.days)).first()
        assertThat(first).isNotNull()
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/androidTest/java/de/jeanlucmakiola/calendula/data/calendar/CalendarRepositorySmokeTest.kt
git commit -m "test: instrumented repository smoke against real CalendarContract"
```

---

## Task 20: Update CHANGELOG, STATE, ROADMAP, REQUIREMENTS

- [ ] **Step 1: Update `CHANGELOG.md`**

Replace the `[Unreleased]` section so the file reads:

```markdown
# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.2.0] — 2026-06-08

### Added
- Domain models for calendars, event instances, event detail, attendees
- `CalendarContract`-backed `CalendarRepository` with `ContentObserver`-driven live updates
- DataStore preference for app-side hidden-calendar visibility
- `READ_CALENDAR` permission flow (rationale + denied recovery + system-settings shortcut)
- Wegwerfbarer Debug-Screen: zeigt alle Kalender + die nächsten 50 Termine ab heute
- Hilt-Wiring für Data-Layer (Repository, ContentResolver, DataStore, IO-Dispatcher)
- Unit-Tests für Cursor-Mapping (alle §8-Defensiv-Cases), Repository-Flows mit Turbine, DataStore round-trip
- Instrumented smoke test against the real CalendarContract provider

## [0.1.0] — 2026-06-08

### Added
- Initial project scaffold (Gradle Kotlin DSL, Version Catalog, Hilt, DataStore)
- Material 3 Expressive theme with Dynamic Color (API 31+) and slate-derived fallback
- Adaptive launcher icon — stylized "1" on slate squircle (references *kalendae*)
- German + English localization infrastructure
- Permission declaration for `READ_CALENDAR` (no UI flow yet — that's Plan 02)
- Gitea CI workflow: lint, unit tests, debug build, Trivy scan
- Gitea release workflow: signed release APK + F-Droid metadata sync to Hetzner
- F-Droid metadata stubs (DE + EN short/full descriptions)
- `.planning/` project-tracking documents
```

- [ ] **Step 2: Update `.planning/STATE.md`**

```markdown
# Calendula — Current State

*Last updated: 2026-06-08*

## Status

**Milestone:** v0.2 — Data Layer & Permission Flow
**Phase:** Plan 02 complete; UI-design iteration pending before Plan 03

## Progress

- [x] Design spec written and committed (`docs/superpowers/specs/2026-06-08-calendar-app-design.md`)
- [x] V1 design decisions resolved (App name "Calendula", icon, seed color)
- [x] Plan 01 written and executed — foundation lands (theme, icon, i18n, Hilt, DataStore, CI green)
- [x] Plan 02 written and executed — data layer + permission flow + debug screen
- [ ] UI-design iteration (mockups for Month/Week/Day/Detail/Filter/Settings, all three states)
- [ ] Plan 03 (Month view)

## Next

1. Iterate on UI design (mockups per screen, all three states)
2. Write Plan 03: Month view
3. Execute Plan 03 — Debug screen gets replaced by month view
```

- [ ] **Step 3: Update `.planning/ROADMAP.md`**

```markdown
# Calendula — Roadmap

## v0.x — Pre-Release

| Version | Milestone | Status |
|---|---|---|
| v0.1 | Foundation & CI | complete |
| v0.2 | Data Layer & Permission Flow | complete |
| v0.3 | Month view | pending |
| v0.4 | Week view | pending |
| v0.5 | Day view | pending |
| v0.6 | Event Detail Sheet | pending |
| v0.7 | Filter & Settings | pending |

## v1.0 — First Public Release

All V1 features shipped, polished, on F-Droid. Read-only calendar.

## v2.0 — Write Support

- Event create / edit / delete via `CalendarContract` writes
- Quick-add sheet
- Conflict UX (event modified externally during edit)

## v3.0 — Power-User Features

- Home-screen widget
- Full-text search
- Tablet / foldable layouts
- Optional: ICS file import (drag-and-drop)

Order is indicative — community feedback after V1 may re-prioritize.
```

- [ ] **Step 4: Update `.planning/REQUIREMENTS.md`**

Find the "Active (V1)" list and tick the relevant items so it reads:

```markdown
### Active (V1)

- [x] Foundation & CI infrastructure
- [x] Data Layer over `CalendarContract`
- [x] Permission flow (`READ_CALENDAR`)
- [ ] Month view (S1)
- [ ] Week view (S2)
- [ ] Day view (S3)
- [ ] Event Detail Sheet (S4)
- [ ] Multi-Calendar Filter (M3)
- [ ] Today button + Jump-to-Date (M2)
- [ ] View-Switcher (M1)
- [ ] Settings screen (M4)
- [ ] Empty / no-permission / no-calendars states
- [ ] German + English localization
- [ ] Loading/Failure/Success states per screen (architectural pattern)
```

The `### Validated (shipped)` block above stays at `(none yet — first milestone in progress)` — those tick only at v1.0.

- [ ] **Step 5: Commit**

```bash
git add CHANGELOG.md .planning/STATE.md .planning/ROADMAP.md .planning/REQUIREMENTS.md
git commit -m "docs: record v0.2.0 data-layer + permission flow in CHANGELOG, planning"
```

---

## Task 21: Final Verification

- [ ] **Step 1: Run the full local verification**

```bash
./gradlew lint test assembleDebug
```

Expected: All three steps BUILD SUCCESSFUL. Unit tests count: 3 (Plan 01) + 3 (TimeBridge) + 5 (Models) + 4 (CalendarMapper) + 9 (InstanceMapper) + 6 (EventDetailMapper) + 5 (Repository) + 4 (CalendarPrefs) + 2 (DebugViewModel) = 41 unit tests, all green.

- [ ] **Step 2: Install on a connected device / emulator and smoke-check manually**

```bash
./gradlew :app:installDebug
adb shell am start -n de.jeanlucmakiola.calendula.debug/de.jeanlucmakiola.calendula.MainActivity
```

Expected UX:
1. App opens to a Permission-Rationale-Screen showing "Kalender-Zugriff" (or "Calendar access" depending on locale) plus a "Weiter" / "Continue" button.
2. Tapping the button triggers the system permission dialog.
3. After granting, the Debug-Screen renders with a yellow "DEBUG — wird mit Plan 03 …" banner, the list of configured calendars, and the next 50 event instances.
4. Tapping "Deny" instead lands on the Denied-Recovery-Screen with retry + system-settings buttons.

- [ ] **Step 3: Run the instrumented test on the emulator**

```bash
./gradlew :app:connectedDebugAndroidTest
```

Expected: 3 instrumented tests green (MainActivitySmokeTest x1, PermissionScreenTest x1, CalendarRepositorySmokeTest x2). Skip locally if no emulator; CI runs them.

- [ ] **Step 4: Push and let Gitea CI run**

```bash
git push origin main
```

Then observe the workflow run in Gitea. The same `.gitea/workflows/ci.yaml` from Plan 01 covers lint + test + assembleDebug; nothing in the workflow itself needed to change.

- [ ] **Step 5: Tag v0.2.0 — Only after CI is green**

```bash
# After CI is green:
git tag -a v0.2.0 -m "v0.2.0 — data layer & permission flow"
git push origin v0.2.0
```

The release workflow signs and uploads to F-Droid. The Debug-Screen ships in v0.2.0 — explicit pre-release; users who install will see the banner that explains it.

---

## Verification Checklist (post-execution)

After all 21 tasks are completed, verify:

- [ ] `./gradlew lint` exits 0
- [ ] `./gradlew test` exits 0; all 41 unit tests pass
- [ ] `./gradlew assembleDebug` produces a working APK
- [ ] APK launches and renders the permission rationale on first run
- [ ] Granting the permission swaps to the Debug screen showing calendars + events
- [ ] Denying the permission shows the recovery screen with two buttons (retry, open settings)
- [ ] "Open system settings" launches the app-info screen for `de.jeanlucmakiola.calendula.debug`
- [ ] Modifying the system calendar externally (e.g. adding an event via the system calendar app or `adb shell content insert`) causes the Debug screen list to update without restart (ContentObserver works)
- [ ] DE locale shows German UI; EN locale shows English UI
- [ ] Light/Dark theme still respects system setting
- [ ] On API 31+ Dynamic Color still picks up wallpaper colors
- [ ] `.planning/STATE.md` reflects "Plan 02 complete"
- [ ] `CHANGELOG.md` has a v0.2.0 entry

On Gitea after pushing:

- [ ] First CI run after the Plan 02 push is green
- [ ] Tag `v0.2.0` triggers the release workflow successfully
- [ ] F-Droid repo shows v0.2.0 alongside v0.1.0

---

## What Plan 02 Does NOT Do (deferred to subsequent plans)

- No Month / Week / Day views → Plans 03 / 04 / 05 (the Debug screen is a stop-gap that those plans replace)
- No Event-Detail-Sheet → Plan 06 (the repository's `eventDetail(id)` is wired and tested, but no UI consumes it yet)
- No Kalender-Filter-Sheet → Plan 07 (DataStore + `hiddenCalendarIds` is in place, but no UI surfaces the toggle yet, and the Debug screen ignores the hidden set on purpose)
- No Settings screen → Plan 07
- No UI-design polish on the Permission / Debug screens — they are deliberately raw; their visual treatment becomes part of the UI-design iteration that precedes Plan 03
- No widget, no notifications, no search, no write support — those are V2/V3 milestones

The data layer is the load-bearing piece of V1. Every later UI plan layers a thin Compose surface over this same repository — no new data-access code needed for screens.
