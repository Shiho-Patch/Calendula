# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.6.0] — 2026-06-11

### Added
- Full event read (v0.6): the detail screen now surfaces every readable
  `CalendarContract` field that V1 had been dropping —
  - **Reminders** — each configured lead time, humanised ("10 minutes before",
    "1 day before", "At time of event"), read from `CalendarContract.Reminders`
  - **Status** — Tentative / Cancelled chip under the title; a cancelled event
    also strikes through its title (Confirmed shows no chip)
  - **Availability** — a "Free" pill pinned top-right of the title when the
    event doesn't block your time (`Events.AVAILABILITY`, the iCal TRANSP
    field); the default "Busy" is left implicit to avoid noise on every event
  - **Access level** — a Private / Confidential chip when the event isn't public
  - **Attendee role** — organizer / optional / resource badge under each
    attendee, plus the device user's own response ("Your response: …") from
    `Events.SELF_ATTENDEE_STATUS`
  - **Time zone** — shown only for timed events pinned to a zone other than the
    device's, so cross-zone events read unambiguously
  - **Linked URLs** — http(s) links in the description are now tappable
- Domain model rounded out with `Reminder`, `EventStatus`, `Availability`,
  `AccessLevel`, `AttendeeRelationship`, `AttendeeType`, and the attendee/self
  status fields; mappers + unit tests cover every new column's integer codes

### Changed
- `versionName`/`versionCode` bumped to 0.6.0 / 6

### Notes
- A dedicated event **URL** field was dropped from scope: `CalendarContract`
  has no `Events.URL` column (only `CUSTOM_APP_URI`, an app deep-link), so URLs
  are surfaced by linkifying the description instead

## [0.5.0] — 2026-06-10

### Added
- Calendar filter (M3): the navigation drawer now hosts the calendar list
  inline — every calendar grouped by account, each with a colour swatch and a
  visibility switch. Hiding a calendar is persisted app-side (DataStore,
  separate from the system VISIBLE flag) and applied centrally in the
  repository, so month/week/day re-filter live the moment a switch flips.
  The drawer was trimmed to just Today, the calendar filter, and Settings
  (the stubbed jump-to-date entry was removed; jump-to-date was later cut
  from scope entirely)
- Settings (M4): a full-screen destination with
  - **Appearance** — theme (System / Light / Dark), Material You dynamic colour
    (auto-disabled below Android 12), week start (Automatic / Monday / Sunday)
  - **Language** — app language (System / Deutsch / English) via per-app
    locales, persisted across cold starts down to Android 10
  - **About** — version, license, and a link to the source on Gitea
- Week-start preference now drives the month grid and week view; "Automatic"
  follows the active locale (Monday in DE, Sunday in en-US)

### Changed
- Theme is driven by one activity-scoped settings source, so a theme or
  dynamic-colour change applies app-wide immediately
- `versionName`/`versionCode` bumped to 0.5.0 / 5 (the in-repo version had
  lagged behind the release tags); the About screen reads it directly

## [0.4.0] — 2026-06-10

### Added
- Event detail (S4): full-screen destination (MD3 list→detail, not a bottom
  sheet) opened by tapping an event in the week/day timeline — title with a
  calendar-colour accent line, a card per field (when, calendar, location,
  description, attendees, recurrence) with leading icons, location tap opens a
  maps intent, Loading/Failure/Success states, slide-in/out over the calendar
- Human-readable recurrence: RRULE rendered as e.g. "Every week on _Tue_ and
  _Thu_ until 31 Dec 2026" (FREQ/INTERVAL/BYDAY/UNTIL/COUNT, abbreviated +
  italicised day names, localized list formatting), with a generic fallback
- Month → day navigation: tapping a day cell opens the day view on that date

### Fixed
- Recurring events failed to open in the detail view: the series row stores
  DURATION instead of DTEND, so the mapper dropped it (EventNotFound). The
  detail now keeps such events and shows the tapped occurrence's own times
  (from CalendarContract.Instances) instead of the series start

## [0.3.0] — 2026-06-10

### Added
- Month view (S1): Material 3 Expressive card-per-day grid (only the current
  month's weeks; neighbouring days left blank), per-day event dots with "+N"
  overflow, today emphasised via `primaryContainer`, spring-based press
  feedback from the active motion scheme, swipe + drawer navigation,
  Loading/Failure/Success states
- Week view (S2): vertical time schedule with overlap-resolved lanes,
  separate all-day strip, midnight-spanning events clipped per day, swipe
  navigation, Loading/Failure/Success states
- Day view (S3): single-column slice of the week schedule reusing its
  overlap-lane layout, per-day swipe navigation, noon-centred scroll that
  persists across swipes, animated all-day strip, compact top bar with the
  full date, Loading/Failure/Success states
- Functional view-switcher (M1) cycling Month ↔ Week ↔ Day
- Shared calendar UI building blocks in `ui/common/` (navigation drawer,
  failure screen, view-switcher pill, color pastelizer, observable locale)

### Removed
- Throwaway debug screen — superseded by the month view

## [0.2.1] — 2026-06-09

### Changed
- Regenerated the F-Droid catalog `icon.png` (512x512, both locales) so it
  is pixel-faithful to the on-device adaptive launcher icon: same slate
  background (`#5C6B7A`), off-white mark (`#FAF6F0`), and the foreground
  group transform (`scale 0.5`, pivot `114,108`, translate `2,8`) baked in.
- Added `design/icon/calendula_launcher.svg` — the composed full-bleed
  icon (background + transformed mark) as the single source of truth for
  store/F-Droid renders.

## [0.2.0] — 2026-06-08

### Added
- Domain models for calendars, event instances, event detail, attendees
- `CalendarContract`-backed `CalendarRepository` with `ContentObserver`-driven live updates
- DataStore preference for app-side hidden-calendar visibility
- `READ_CALENDAR` permission flow (rationale + denied recovery + system-settings shortcut)
- Wegwerfbarer Debug-Screen: zeigt alle Kalender + die nächsten 50 Termine ab heute
- Hilt-Wiring für Data-Layer (Repository, DataSource, DataStore, IO-Dispatcher)
- Unit-Tests für Cursor-Mapping (alle §8-Defensiv-Cases), Repository-Flows mit Turbine, DataStore round-trip
- Instrumented smoke test against the real CalendarContract provider

### Changed
- Redesigned launcher icon: line-art calendar with a stylized "1" inside
  (kalendae reference) and a small calendula bloom badge in the
  bottom-right corner. Replaces the simple "1"-only foreground from
  v0.1.0. Source SVG checked in at `design/icon/calendula_mark.svg`,
  also used to regenerate the F-Droid catalog `icon.png` (512x512)
  per locale.

## [0.1.1] — 2026-06-08

### Fixed
- F-Droid metadata format: renamed locale dirs from `de/` to `de-DE/`,
  `short_description.txt` to `summary.txt`, `full_description.txt` to
  `description.txt` (fastlane format that fdroidserver actually reads,
  matching the working HouseHoldKeaper convention)
- Added `icon.png` (512x512) per locale; fdroidserver does NOT
  auto-extract icons from APKs that only contain XML adaptive icons
  (which is what minSdk-29 apps produce), so the app was rendered
  blank-iconed in F-Droid clients

### Changed
- CI pipeline cleanup: `lintDebug`/`testDebugUnitTest` instead of full
  `lint`/`test` (cuts ~50% of lint work since release variant lint is
  redundant for V1 single-variant build)
- Release workflow drops the lint step from its CI-sanity job since
  the same lint already ran via `ci.yaml` when the underlying commit
  hit main

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
