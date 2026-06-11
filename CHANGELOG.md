# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Reminder notifications (v1.4): Calendula now delivers event reminders as
  notifications itself — the system schedules them but posts nothing, so a
  calendar app must (essential when Calendula is the only one installed).
  Due reminders appear on a dedicated "Event reminders" channel; tapping one
  opens the event's detail screen. Email reminders are never posted (the
  provider only schedules alert-type reminders)
- A one-time onboarding step after the calendar grant introduces reminders,
  requests the notification permission (Android 13+), and warns that a second
  calendar app with notifications on will duplicate them. "Not now" leaves
  the feature off
- Settings gained a "Notifications" section mirroring the choice: an event-
  reminders toggle (default on) with the duplicate-reminders hint; turning it
  on re-requests the notification permission when missing

## [1.3.0] — 2026-06-11

### Added
- Event editing: a pencil action on the detail screen (writable calendars
  only) opens the event form prefilled with the event. Only fields you
  actually changed are written back; saving an untouched form is a no-op.
  Sections holding data are always shown, regardless of the form-field
  defaults; the calendar itself can't be changed while editing
- Recurring events — scoped writes, chosen when saving (Google model):
  "only this event" (a modified-occurrence exception), "this and all
  following" (the series is split at the occurrence), or "all events in
  the series". Changing the recurrence rule rules out "only this event"
- Deleting a recurring event gained the middle option too: "this and all
  following events" ends the series just before the chosen occurrence
- Recurrence picker (create and edit): one-tap daily/weekly/monthly/yearly
  presets plus a custom step with interval + unit, weekday toggles for
  weekly rules ("every week on Mon and Fri"), and an end condition (never /
  on a date / after a number of times). Rules the picker can't express
  (e.g. "second Thursday monthly") are shown humanized and preserved
  verbatim unless replaced. Recurrence also joined the optional form
  fields and their settings defaults
- Validation: a repeat that would end before the event starts is flagged
  (it would otherwise vanish from every view)

### Changed
- Editing reminders reconciles against the provider's actual rows:
  reminders you didn't touch keep their method (e.g. email reminders on
  synced events survive unrelated edits)
- The contextual WRITE_CALENDAR upgrade for v1.0 installs covers the edit
  action like delete

### Fixed
- Splitting a series ("this and following") sends the complete time-column
  set in one update, so the provider regenerates its cached instances — an
  RRULE-only update left a stale duplicate of the tapped occurrence on the
  split day
- RRULE UNTIL values are written as the local end of day expressed in UTC
  (instead of a flat `T235959Z`), so recurrences can't leak an extra day in
  timezones ahead of UTC
- `versionName`/`versionCode` bumped to 1.3.0 / 11

## [1.2.1] — 2026-06-11

### Added
- Optional event-form fields with user-controlled defaults: reminders,
  availability (busy/free), and visibility (default/public/private/
  confidential) joined location and description as form sections. Settings
  gained a "New event form" section choosing which show by default; the rest
  unfold via a "More fields" picker
- Reminders editor: stacked rows with right-bound remove, full-width add
  action; the picker offers one-tap presets and a custom amount + unit
  (minutes/hours/days/weeks) step
- `OptionCard` — the app's standard selection-dialog row (full-width tonal
  card, optional icon + supporting line, highlighted selection). All dialogs
  (calendar, visibility, more-fields, reminder presets, recurring-delete)
  now use it; radio-row dialogs are retired

### Changed
- Event form redesigned onto the detail screen's design system: tonal cards
  with gutter icons (top-aligned on tall cards), borderless inline text
  fields, calendar-coloured accent bar under the title, no dividers, no
  top-bar title; placeholders render clearly fainter than input
- M3 Expressive motion: the theme now provides a MotionScheme
  (`MaterialExpressiveTheme`, standard springs — expressive bounce reviewed
  as overdone), the FAB stack and "more fields" reveals animate on theme
  springs
- The jump-to-today slide is direction-aware (future → today slides in from
  the left, past → from the right)
- `versionName`/`versionCode` bumped to 1.2.1 / 10

### Fixed
- The keyboard no longer pans the whole event form; the screen stays
  anchored and the focused field scrolls into view (`adjustResize` +
  `imePadding`)

## [1.2.0] — 2026-06-11

### Added
- Create events (milestone 2, slice 2):
  - A "+" FAB on the month, week, and day views opens a new full-screen event
    form, prefilled with the visible day (today at the next full hour, or
    09:00 on other days)
  - The form covers title, all-day toggle, start/end with Material 3 date and
    time pickers (moving the start drags the end along, preserving duration),
    target calendar, location, and description
  - The calendar picker offers only writable calendars and preselects the one
    you last created an event in
  - Validation on save ("ends before it starts", no writable calendar), with
    the same contextual write-permission upgrade as delete
  - All-day events are stored provider-correctly (UTC midnights, exclusive
    end), timed events in the device time zone

### Changed
- The jump-to-today pill now stacks above the new "+" FAB instead of being
  the only floating action
- `versionName`/`versionCode` bumped to 1.2.0 / 9

## [1.1.0] — 2026-06-11

### Added
- Write foundation (milestone 2, slice 1): Calendula can now **delete events**.
  - Delete action on the event detail screen, with a confirmation dialog;
    recurring events choose between "Only this event" (a cancelled exception,
    so the rest of the series survives) and "All events in the series"
  - `WRITE_CALENDAR` permission: onboarding asks for read+write in one system
    dialog, but only read access is required — declining write keeps the app
    fully usable read-only. Existing v1.0 installs are asked for the write
    upgrade in place, on their first delete
  - Read-only calendars (WebCal subscriptions, birthday calendars, …) are
    detected via `CALENDAR_ACCESS_LEVEL` and show no edit/delete actions at all

### Changed
- Onboarding copy no longer claims "read-only"; it now says your data stays on
  the device (still no internet permission, still zero telemetry)
- The placeholder Edit button on the detail screen (a no-op since v0.4) is
  removed until editing ships in a later slice
- `versionName`/`versionCode` bumped to 1.1.0 / 8

## [1.0.0] — 2026-06-11

First public release. Calendula is a read-only, Material 3 Expressive calendar
that lives entirely on top of Android's `CalendarContract` — every calendar
synced to the device (CalDAV via DAVx5, Google, local, WebCal, …) shows up
automatically, with zero telemetry and no internet permission.

### Highlights (accumulated across v0.1 → v0.6)
- Month, week, and day views with a view switcher, swipe navigation, and
  Loading / Failure / Success states on every screen
- Full-screen event detail surfacing every readable `CalendarContract` field —
  times, recurrence (humanised), location, description (with tappable links),
  attendees + roles + your own response, reminders, status, availability,
  access level, and foreign time zones
- Per-calendar visibility filter (grouped by account, persisted) and a Settings
  screen (theme, Material You dynamic colour, week start, app language)
- Material 3 Expressive first-run onboarding for calendar access
- German + English localization throughout

### Changed
- `versionName`/`versionCode` bumped to 1.0.0 / 7

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
- Redesigned the first-run grant-access screen — the onboarding a new user
  sees. Material 3 Expressive layout: branded launcher-mark hero, an app-name
  eyebrow, a benefit-led headline, three trust rows (on-device, every calendar,
  no tracking) with tonal icon chips, a full-width filled CTA with a trailing
  arrow, and a "Read-only · no internet permission" footnote (the app declares
  only `READ_CALENDAR`). The denied/recovery state shares the same shell with a
  lock-badged hero and Open-settings / Try-again actions
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
