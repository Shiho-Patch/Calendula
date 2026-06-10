# Calendula - V1 Design Spec

**Date:** 2026-06-08
**Status:** Draft for review
**Author:** Jean-Luc Makiola (with Claude)

## 1. Motivation & Goal

Eine Android-native, Open-Source-Kalender-App im Material 3 Expressive Design.
Schließt die Lücke, dass es derzeit keinen optisch zeitgemäßen Kalender abseits
von Google Calendar gibt - speziell mit dem 2025er Expressive-Design.

**Was die App NICHT macht:**
- Eigene CalDAV/iCal-Synchronisation - das übernimmt der Android `CalendarContract`
  bzw. Drittsoftware wie DAVx5
- Eigene lokale Event-DB - alle Daten leben im `CalendarContract`

**Was die App macht:**
- Schöne, moderne UI über Android-Bordmittel
- Liest aus `CalendarContract` (alle Quellen: Nextcloud/CalDAV via DAVx5,
  Google, lokal, WebCal-Subscriptions)
- V1 ist read-only; Schreibrechte kommen in V2

## 2. Scope - V1 MVP (Variante "B")

### In-Scope
- 3 Hauptansichten: Monat, Woche, Tag
- Event-Detail-Sheet (read-only Detailansicht)
- Multi-Kalender-Toggle (Sichtbarkeit pro Kalender)
- Heute-Button (Jump-to-Date gestrichen, siehe Out-of-Scope)
- Settings-Screen (Theme, Dynamic Color, Wochenstart, Sprache)
- Permission-Flow für `READ_CALENDAR`
- Empty-States und Error-Recovery
- DE + EN Lokalisierung
- Tests + CI ab Tag 1

### Out-of-Scope (V2+)
- Jump-to-Date / Datum-Picker (aus V1-Scope gestrichen)
- Event-Create/Edit/Delete (V2)
- Home-Screen-Widget
- Volltextsuche
- Quick-Add
- Notifications/Reminders (System macht das schon, nicht doppeln)
- Tablet-/Foldable-spezifische Layouts
- iOS (Kotlin-Native ist explizit Android-only)

## 3. Tech Stack

| Layer | Wahl | Begründung |
|---|---|---|
| Sprache | Kotlin 2.0+ | Android-Native-Standard |
| UI | Jetpack Compose + Material3 Expressive (1.5+) | Echter M3 Expressive Support |
| Min SDK | 29 (Android 10) | Modern, keine Compat-Pfade |
| Target SDK | 36 (Android 16) | Aktuell, wie HouseHoldKeaper CI |
| DI | Hilt | Industriestandard |
| Persistenz Prefs | DataStore (Preferences) | Theme, Wochenstart, Filter-State |
| Persistenz Daten | keine | Source of Truth bleibt `CalendarContract` |
| Datum/Zeit | `kotlinx.datetime` (Domain), `java.time` an Provider-Grenze | Saubere API |
| Navigation | Compose Navigation, Single-Activity | Standard |
| Lokalisierung | Android Resources (`strings.xml`) + Plurals | DE + EN ab V1 |
| Tests | JUnit5, Truth, Turbine, Compose UI Test | JVM-first, Instrumented nur für ContentResolver-Integration |
| Build | Gradle Kotlin DSL + Version Catalog | Lesbar, typsicher |
| CI | Gitea Workflows (adaptiert von HouseHoldKeaper) | Gleiche Konvention wie restliche Projekte |

### Permissions
- `READ_CALENDAR` (einzige Runtime-Permission)
- `android.permission.QUERY_ALL_PACKAGES` **nicht** nötig (Maps-Intent geht ohne)

### App-Identifier
- **App-Name:** Calendula (vom lateinischen *kalendae* - "der erste Tag des Monats", Wortwurzel von "Kalender"; gleichzeitig der Name der Ringelblume)
- **Package:** `de.jeanlucmakiola.calendula`
- Convention identisch zu `HouseHoldKeaper`: `de.jeanlucmakiola.<app_name>`

## 4. Architektur

### Modul-Struktur
Single Gradle module `:app` für V1. Feature-Split (`:core`, `:feature-*`) erst
wenn nötig - YAGNI.

### Package-Layout
```
de.jeanlucmakiola.calendula/
├── CalendulaApp.kt + MainActivity.kt
├── data/          ContentResolver-Wrapper, Repositories
├── domain/        Pure-Kotlin Models (Event, CalendarSource)
└── ui/
    ├── theme/     M3 Expressive Theme, Dynamic Color
    ├── month/     Monatsansicht (Composable + ViewModel + State)
    ├── week/      Wochenansicht
    ├── day/       Tagesansicht
    ├── detail/    Event-Detail-Sheet
    ├── filter/    Kalender-Filter-Sheet
    ├── settings/  Settings-Screen
    ├── permission/ Permission-Request-Screen
    └── common/    Geteilte Composables (LoadingScreen-Helper, FailureScreen-Helper)
```

### Layer-Verantwortlichkeiten
- **data/**: Nur Layer der Android-Klassen kennt (`ContentResolver`, `Cursor`,
  `CalendarContract`). Mapped auf Domain-Modelle.
- **domain/**: Pure Kotlin. Keine Android-Imports.
- **ui/**: Compose-Code, ViewModels. Hängt von domain ab, niemals direkt an data.

## 5. Datenfluss & Domain-Modell

### Domain-Modelle (pure Kotlin)

```kotlin
data class CalendarSource(
    val id: Long,
    val displayName: String,
    val accountName: String,    // z.B. "jlmak@nextcloud.example.com"
    val accountType: String,    // z.B. "at.bitfire.davdroid" (DAVx5), "com.google" (Google), "LOCAL"
    val color: Int,
    val isVisibleInSystem: Boolean,  // CalendarContract.Calendars.VISIBLE
)

data class EventInstance(
    val instanceId: Long,        // Instances._ID (eindeutig pro Vorkommen)
    val eventId: Long,           // Events._ID (gleich für alle Vorkommen)
    val calendarId: Long,
    val title: String,
    val start: Instant,
    val end: Instant,
    val isAllDay: Boolean,
    val color: Int,              // Effektiv: Event.color ?: Calendar.color
    val location: String?,
)

data class EventDetail(
    val instance: EventInstance,
    val description: String?,
    val organizer: String?,
    val attendees: List<Attendee>,
    val rrule: String?,          // Read-only: nur "wiederkehrt wöchentlich" anzeigen
)

data class Attendee(val name: String, val email: String?, val status: AttendeeStatus)
enum class AttendeeStatus { Accepted, Declined, Tentative, NeedsAction, Unknown }
```

### Repository

```kotlin
interface CalendarRepository {
    fun calendars(): Flow<List<CalendarSource>>
    fun instances(range: ClosedRange<Instant>): Flow<List<EventInstance>>
    suspend fun eventDetail(eventId: Long): EventDetail
}
```

**Implementation-Details:**
- Hält `ContentObserver` auf `CalendarContract.CONTENT_URI` registriert
- Bei Observer-Trigger: re-query, emit neuer Wert via SharedFlow
- Queries laufen auf `Dispatchers.IO`
- `instances(range)` nutzt `CalendarContract.Instances.CONTENT_BY_DAY_URI`
  oder `CONTENT_URI` mit Time-Range - Recurrence-Expansion macht der Provider
- App-eigene "ausgeblendete Kalender-IDs" leben in DataStore als `Set<Long>`,
  kombinieren via `combine()` mit Calendar-Flow

### ViewModel-Pattern

Ein ViewModel pro Top-Level-Screen. State immer als sealed interface
(siehe Section 7 - Loading/Failure/Success).

ViewModel kennt aktuelle "Cursor"-Position (welcher Monat/Woche/Tag) und
fragt Repository nur für sichtbaren Range an - kein "alle Events der Welt".

## 6. Screens & Menüs

### Hauptscreens

**S1 - Monatsansicht**
- Zeigt einen Monat im Überblick
- Pro Tag erkennbar: hat-Events / keine-Events, ggf. Andeutung über Anzahl/Farbe
- Navigation: vorwärts/zurück zwischen Monaten
- Tap auf Tag → Tagesansicht für diesen Tag
- Heute deutlich markiert

**S2 - Wochenansicht**
- Zeigt eine Woche mit Zeitschiene
- Events auf ihrer Uhrzeit, Calendar-Farbe
- Overlap-Events: nebeneinander aufgelöst
- All-Day-Events extra dargestellt
- Navigation: vorwärts/zurück zwischen Wochen
- Tap Event → Event-Detail-Sheet

**S3 - Tagesansicht**
- Eine Spalte, mehr Detail pro Event als Wochenansicht
- All-Day-Events extra
- Navigation: vorwärts/zurück zwischen Tagen
- Tap Event → Event-Detail-Sheet

**S4 - Event-Detail-Sheet (Bottom-Sheet, ModalBottomSheet)**
- Pflicht-Inhalte: Titel, Start/Ende oder "Ganztägig", Kalender-Zugehörigkeit
- Konditional: Ort (Tap → Maps-Intent), Beschreibung, Teilnehmer, RRULE-Hinweis
- Dismissable via Drag oder Back-Geste

### Menüs

**M1 - View-Switcher**
- Wechsel zwischen Monat / Woche / Tag
- Immer erreichbar von allen Hauptansichten
- State persistent (zuletzt aktive Ansicht)

**M2 - Heute**
- Schnell zurück zu "heute" (Drawer-Eintrag, ausgeliefert in v0.5)
- ~~Springe zu beliebigem Datum via Datum-Picker~~ — **gestrichen**, siehe Out-of-Scope
- Erreichbar von allen Hauptansichten

**M3 - Kalender-Filter (Bottom-Sheet)**
- Sichtbare Kalender ein-/ausblenden
- Gruppiert pro Account (Nextcloud / Local / Google / …)
- Pro Eintrag: Name, Calendar-Farbe
- Persistiert in DataStore
- Erreichbar von allen Hauptansichten

**M4 - Settings**
- Theme: System / Light / Dark
- Dynamic Color: an/aus (auto disabled wenn API < 31)
- Wochenstart: Auto (aus Locale) / Mo / So
- Sprache: Auto / DE / EN
- About: Version, Lizenz, Link zum Quellcode (Gitea)

### Spezial-Flows

**F1 - Erst-Start / Permission-Flow**
- Beim ersten App-Start: `READ_CALENDAR`-Request
- Erklärungs-Text: "Wir lesen nur deinen Gerätekalender - keine Daten verlassen das Gerät"
- Bei Denial: friendlicher Recovery-Screen mit Re-Request-Button + Link zu System-Settings

**F2 - Empty-State (keine Kalender / keine Events)**
- Keine Kalender konfiguriert: Hinweis "Füge in DAVx5 oder System-Settings einen Kalender hinzu" mit Intent-Link zu System-Calendar-Settings
- Kalender da, aber aktuelle Ansicht leer: dezent, kein nerviger Placeholder

**F3 - Reaktion auf externe Änderungen**
- DAVx5/System-Calendar ändert sich → App aktualisiert sich automatisch via ContentObserver
- Kein manueller Pull-to-Refresh

## 7. UI-State-Modell: Loading / Failure / Success

**Pflicht-Pattern für jeden Screen.** Keine Ausnahmen.

### ViewModel-State

```kotlin
sealed interface MonthUiState {
    data object Loading : MonthUiState
    data class Failure(val reason: FailureReason) : MonthUiState
    data class Success(
        val month: YearMonth,
        val eventsPerDay: Map<LocalDate, List<EventInstance>>,
        val visibleCalendars: List<CalendarSource>,
    ) : MonthUiState
}

enum class FailureReason {
    PermissionRevoked,        // → Re-Request-Screen
    NoCalendarsConfigured,    // → Empty-State mit Intent zu System-Settings
    ProviderUnavailable,      // → Retry-Screen
    EventNotFound,            // → nur für Event-Detail-Sheet
    Unknown,                  // → Fallback
}
```

### Composable-Dispatch

```kotlin
@Composable
fun MonthScreen(viewModel: MonthViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    when (val s = state) {
        is MonthUiState.Loading -> MonthLoadingScreen()
        is MonthUiState.Failure -> MonthFailureScreen(s.reason, onRetry = viewModel::retry)
        is MonthUiState.Success -> MonthSuccessScreen(s, ...)
    }
}
```

### Pflicht-Composables pro Screen

| Screen | Loading | Failure-Varianten | Success |
|---|---|---|---|
| Monat | Skelett-Grid (Shimmer) | Permission, NoCalendars, Provider | Grid mit Events |
| Woche | Skelett-Schiene | Permission, NoCalendars, Provider | Schiene mit Events |
| Tag | Skelett-Schiene | Permission, NoCalendars, Provider | Schiene mit Events |
| Event-Detail | kompakter Skelett-Sheet | EventNotFound, Provider | Voll-Detail |
| Kalender-Filter | Skelett-Liste | Provider | Liste |
| Settings | sofort (DataStore ist instant) | - | direkt |

**Regeln:**
- Loading ist ein bewusst gestalteter Screen (Skeleton + Shimmer), kein loser Spinner
- Failure ist ein eigener Screen mit Erklärung + Recovery-Action, kein Toast
- Beim UI-Design später: alle drei Varianten pro Screen skizzieren, nie nur Success
- Tests: pro Screen mindestens `renders_loading`, `renders_failure`, `renders_success`

## 8. Error Handling & Edge Cases

### Philosophie
Calendar-Apps dürfen niemals an leeren/malformen Daten crashen. Defensive
Validierung im Repository, kaputte Instanzen still droppen + via `Log.w` loggen.

### Konkrete Fehlerfälle
| Fall | Verhalten |
|---|---|
| ContentResolver-Query wirft (Permission revoked zur Laufzeit) | State → `Failure(PermissionRevoked)`, UI zeigt Re-Request |
| Calendar `displayName` null | Fallback "(Unbenannter Kalender)" |
| Event `title` null/leer | Fallback "(Ohne Titel)" |
| `dtend < dtstart` | Event droppen, Warn-Log |
| `dtstart` vor Unix-Epoch | Event droppen, Warn-Log |
| Maps-Intent fehlt | SnackBar "Keine Karten-App installiert" |
| DataStore-IO-Fehler | Defaults verwenden, weiter, Warn-Log |

### Edge Cases im UI
- **All-Day-Events über mehrere Tage:** in Wochen-/Tagesansicht über mehrere
  Tage gespannter All-Day-Strip
- **Events über Mitternacht:** in Wochen-/Tagesansicht am Folgetag fortsetzen
- **Instant Events (start == end):** Mindesthöhe rendern für Tap-Target
- **Viele Events an einem Tag:** in Monatsansicht "+N more" statt Overflow
- **Timezones:** alle Berechnungen in Geräte-Local-TZ, außer all-day = floating

## 9. i18n & Accessibility

### i18n
- `res/values/strings.xml` = englische Master-Strings
- `res/values-de/strings.xml` = deutsche Übersetzungen
- Alle Strings extrahiert, auch Plurals (`<plurals>` für "1 Event" / "N Events")
- Wochentags-/Monatsnamen via `java.time.format.DateTimeFormatter` mit aktiver
  Locale (kein Hardcoding)
- Sprach-Override aus Settings via `AppCompatDelegate.setApplicationLocales`

### Accessibility (V1-Minimum)
- Alle interaktiven Elemente: `contentDescription`, Tap-Target ≥ 48dp
- Event-Items: semantisches Label "Titel, Start-Zeit, Dauer, Kalender X"
- Calendar-Color **immer** mit Label/Form kombiniert (nie nur-Farbe-Information)
- Dynamic-Text-Size respektiert (keine fixen sp-Werte für Text)
- Hoher-Kontrast: M3-Theme reagiert automatisch
- TalkBack-Smoke-Tests im UI-Test-Plan

## 10. Testing

Best Practices, kein Diskussionsbedarf:
- **Unit-Tests:** JUnit5 + Truth + Turbine. Repository, ViewModels, Date/Time-Helpers,
  ContentResolver-Wrapper (mit Mock-Cursor)
- **UI-Tests:** Compose UI Test pro Screen, mindestens `renders_loading` /
  `renders_failure` / `renders_success` + 1-2 Interaktions-Tests
- **Coverage-Ziel:** pragmatisch ~70% lines, 100% für Repository + Date-Logik.
  Kein Coverage-Gate in CI, aber Pflicht-Run
- **Instrumented-Tests:** nur für ContentResolver-Integration (echte
  CalendarContract-Queries auf Emulator)

## 11. CI/CD

Adaption der `HouseHoldKeaper`-Pipeline, nur Flutter-Steps durch Gradle ersetzt.

### `.gitea/workflows/ci.yaml` (push + PR)
- Setup Java 17 + Android SDK 36
- `./gradlew lint`
- `./gradlew test`
- `./gradlew assembleDebug`
- Trivy Filesystem-Scan (HIGH/CRITICAL, `continue-on-error` wie HHK)

### `.gitea/workflows/release.yaml` (auf Git-Tags)
- Alles aus `ci.yaml`
- Version aus Git-Tag in `app/build.gradle.kts`:
  - `versionName = "${tag#v}"`
  - `versionCode = MAJOR*10000 + MINOR*100 + PATCH` (HHK-Konvention)
- Keystore aus Gitea Secrets (`KEYSTORE_BASE64`, `KEY_PASSWORD`, `KEY_ALIAS`)
- `./gradlew assembleRelease`
- F-Droid-Pipeline 1:1 wie HHK: Hetzner-Sync, `fdroid update -c`, Re-Upload

### Repo-Konventionen
- `CHANGELOG.md` wird beim Taggen gepflegt (patch/minor/major)
- `fdroid-metadata/de.jeanlucmakiola.calendula/` Verzeichnis-Struktur
- `LICENSE` = MIT, Jean-Luc Makiola, 2026
- `.planning/` mit `PROJECT.md`, `REQUIREMENTS.md`, `ROADMAP.md`, `STATE.md`

## 12. Design-Decisions (gelöst)

### Theme-Seed-Color (Fallback wenn kein Dynamic Color verfügbar)
**`0xFF5C6B7A`** - desaturiertes Schiefer-Blaugrau.
- Bewusst anders als HouseHoldKeaper's Sage (`0xFF7A9A6D`), damit beide Apps unterscheidbar sind
- Mid-Saturation → M3 Expressive Dynamic Color generiert daraus eine ausgewogene Palette
- Cool aber nicht kalt → passt zu "modern functional"
- Funktioniert in Light- und Dark-Theme

### App-Icon (Adaptive Launcher)
**Statische "1" auf M3-Expressive-Squircle.**
- **Foreground:** Stilisierte Ziffer "1" (bold), zentriert auf einem Squircle
- **Background:** Seed-Color `0xFF5C6B7A` (slate)
- **Bedeutung:** Die "1" referenziert *kalendae* (der erste Tag des Monats) - Wortwurzel sowohl von "Kalender" als auch "Calendula". Die App heisst Calendula, aber das Icon zeigt klar: dies ist ein Kalender.
- Adaptive-Icon-Spec: Foreground 432dp x 432dp Safe-Zone in 108dp Tile, Background fest
- Vektor-basiert (kein PNG), in `res/drawable/ic_launcher_*.xml` als VectorDrawable

### Konkretes UI-Layout pro Screen
**Bewusst offen** - wird in eigener UI-Design-Iteration nach Spec-Approval entworfen
(Mockups pro Screen, alle drei States, vor Implementation).

## 13. Nächste Schritte nach Spec-Approval

1. Implementation-Plan via `writing-plans`-Skill aus diesem Spec ableiten
2. Initiales Gradle-Projekt-Scaffolding
3. Tooling: Lint-Config, Detekt o.ä., CI-Workflows initial
4. Iterative UI-Design-Phase (Mockups pro Screen, alle drei States,
   bevor implementiert wird)
5. Feature-by-Feature-Implementation gegen den Plan
