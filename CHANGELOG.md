# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [2.11.0] — 2026-06-27

### Added
- Start the week on any day. The **Week starts on** setting (Settings →
  Appearance) now offers every weekday — not just Monday or Sunday — alongside
  the automatic, locale-based default. Thanks to @zmaherdev for the suggestion
  ([#3]).
- Choose a 12- or 24-hour clock. A new **Time format** setting (Settings →
  Appearance) lets you force a 12-hour (2:00 PM) or 24-hour (14:00) clock, or
  follow the system setting automatically. It applies everywhere times appear —
  the week and day timelines, agenda, event details, search and reminders — so
  the format is now consistent across the whole app. Thanks to @zmaherdev for
  the suggestion ([#6]).
- Optional hour lines in the timeline. A new **Hour lines** switch (Settings →
  Appearance) draws a faint separator at each hour in the week and day views,
  making it easier to see when events start and end. Off by default. Thanks to
  @zmaherdev for the suggestion ([#5]).
- Limit how far ahead the agenda looks. New **Agenda range** and **Agenda widget
  range** settings (Settings → Appearance) let the agenda screen and its widget
  each show just today, the rest of this week, the rest of this month, a rolling
  7 or 30 days, or a custom number of days. "This week" follows your week-start
  preference. Thanks to @zmaherdev for the suggestion ([#4]).

## [2.10.0] — 2026-06-25

### Added
- Turn a calendar off across the whole app. Settings → Calendars now has a
  switch on every calendar — both your own and synced ones. Switching one off
  removes it everywhere: its events disappear from all views and search, and it
  drops out of the drawer's hide/show filter, the event-form calendar picker and
  the import target picker. Unlike hiding (the quick per-view checkbox in the
  drawer), a disabled calendar leaves the app entirely until you turn it back on.
  Nothing is deleted and no other app is affected — it's a Calendula-only view
  choice — so you can re-enable it any time from the same screen, where disabled
  calendars stay listed but dimmed.
- An optional way to support development. Settings → About now has a "Support
  development" button that opens Ko-fi in your browser. It's a plain donation
  link with no perks attached, and it needs no new permissions — Calendula still
  has no internet access of its own and just hands the link to your browser.

## [2.9.0] — 2026-06-25

### Added
- Choose the view Calendula opens on. A new **Default view** setting (Settings →
  Appearance) lets you pick Month, Week, Day or Agenda as the view shown each
  time you start the app, instead of always opening on Week. Thanks to
  @devinside for the suggestion ([#1]).
- The month widget is now interactive: tap any day to open it, or tap an event to
  open its details. Previously only the month's prev/next/today controls
  responded. Thanks to @devinside for spotting this ([#2]).
- Predictive back. Swiping back from a screen — an event, the editor, search or
  settings — now follows your finger, shrinking the screen to preview what's
  behind so you can see where Back will take you before you let go; slide it back
  to cancel. The preview needs Android 14 or newer; on older versions Back works
  as before.
- Respect for "Remove animations". If you've turned animations off in your
  device's Accessibility settings, Calendula now honours it everywhere: motion
  collapses to a quick fade and the back preview is skipped.

### Changed
- Smoother, more consistent motion throughout. Expanding sections, list updates
  in search and agenda, and the onboarding screens now animate the same way
  across the app, instead of some places sliding or growing while others popped
  in. Switching between Month, Week, Day and Agenda now cross-fades rather than
  snapping, while paging within a view keeps its slide.

### Fixed
- Home-screen widgets now back out the way you came in. A day or event opened
  from the Agenda widget keeps you in the agenda context, and from the Month
  widget in the month context — pressing Back returns there instead of dropping
  you on the week view. More broadly, the app now keeps a real view history:
  switching views and drilling into a day are retraced by Back one step at a
  time, down to your default view before the app exits. Thanks to @devinside for
  reporting ([#2]).

## [2.8.0] — 2026-06-23

### Added
- Find events fast. A search button in the top bar of every calendar view opens
  a search box — type a couple of letters and matching events (by title,
  location or description) appear, soonest first with past events below. Tap a
  result to open it. Search covers your whole calendar, not just what's on
  screen, and skips calendars you've hidden. A recurring event shows its next
  occurrence rather than the date the series first started.
- A current-time line in the day and week views. A thin coloured line marks the
  present moment across today's column, so you can see at a glance where you are
  in the day. It updates every minute and only appears when today is in view.
- Add and remove event guests. The create/edit form now has a Guests section:
  add people by email or pick them from your contacts, mark each as required or
  optional, and remove them. Calendula never sends invitations itself (it has no
  internet access) — it only records the guests; if the event lives on a synced
  account, that account may email them when it syncs, and on a local calendar no
  one is notified. The form tells you which applies. Picking a guest from
  contacts needs no contacts permission.
- Pick a location from your contacts. A contacts button beside the location
  field drops a contact's address straight into an event — handy for a meeting
  at someone's home or office. Like the guest picker, it needs no contacts
  permission.
- A "New event" Quick Settings tile. Add it to your quick settings to jump
  straight into the new-event form from anywhere. Settings → New event has a
  one-tap button to add the tile (Android 13+); on older versions you can add it
  from the quick-settings editor.
- Snooze and dismiss buttons on reminder notifications. Dismiss clears the
  reminder; snooze hides it and brings it back after a delay you pick in
  Settings → Notifications (5 to 60 minutes, default 10). Android's calendar
  system won't re-post a reminder on its own, so Calendula schedules an exact
  alarm to bring a snoozed one back on time.

### Changed
- Event details now show each guest's email beneath their name, instead of only
  when no name is available.
- Crash and problem reports now open on the project's public Codeberg tracker,
  where anyone can register and file an issue. Nothing is sent automatically —
  you still review the report and submit it yourself in the browser.

## [2.7.5] — 2026-06-21

### Changed
- Further build cleanup for the official F-Droid repository: stopped embedding
  AGP's dependency-metadata block in the APK, which F-Droid's reproducible-build
  scanner rejects as an extra signing block. No functional or visible changes —
  the same app as 2.7.4, just without that Play-oriented metadata blob.

## [2.7.4] — 2026-06-21

### Changed
- Build cleanup that lets Calendula ship in the official F-Droid repository:
  removed an unused Gradle toolchain-resolver plugin, which F-Droid's offline,
  reproducible build process disallows. No functional or visible changes — this
  is the same app as 2.7.3.

## [2.7.3] — 2026-06-21

### Fixed
- Home-screen widgets no longer get stuck on a loading spinner in the published
  (F-Droid) release build. They render via Android's background-work system, and
  release optimisation (R8) was stripping a helper class it loads by name, so the
  render job never ran. Added the missing keep rule — widgets now load normally.

## [2.7.2] — 2026-06-21

### Added
- Crash reporting you control. If Calendula closes unexpectedly, it now captures
  a technical report and, on the next launch, offers to send it as an issue on
  the project's tracker. Nothing is uploaded automatically — the report stays on
  your device until you choose to share it, it contains no personal data or
  calendar content (only the app, Android and device versions plus the stack
  trace), and you see the full text before sending. There's also a "Report a
  problem" entry in Settings, and if the app ever fails to start repeatedly, a
  minimal recovery screen still lets you send the report.

## [2.7.1] — 2026-06-21

### Fixed
- Fixed the app crashing immediately on launch whenever calendar access hadn't
  been granted yet (a fresh install, or after revoking the permission). The app
  set up its live calendar-change listener before the permission screen could
  appear, which newer Android versions reject outright — so the app died before
  you could grant access. The listener now waits for the permission and attaches
  itself the moment it's granted.

## [2.7.0] — 2026-06-18

### Added
- Share a single event as an `.ics` file from the event detail screen — hands a
  standard calendar file to any app via the system share sheet.
- Back up your local (device-only) calendars: Settings → Calendars → Export as
  `.ics` file writes every event of your on-device calendars to a file you
  choose. Local calendars aren't synced anywhere, so this is their only backup.
- Open or share an `.ics` file into Calendula: a single event opens the create
  form prefilled for review, while a file with many events (e.g. a backup) opens
  a bulk import — pick a calendar and import them all. Re-importing a backup
  won't create duplicates (events are matched by their unique identifier), and
  anything Calendula can't represent (changed recurring occurrences, guest
  lists) is reported rather than silently dropped.

### Fixed
- All-day events that cover a single day (e.g. a birthday) no longer show up on
  the following day as well — in the day, week and month views or on the event
  detail screen. The extra day came from interpreting the all-day date range in
  the device's time zone instead of UTC.
- Fixed the app crashing immediately on every launch in the optimized release
  build: release code-shrinking (R8) was stripping a database class the
  home-screen widget framework needs, so the app died at startup before showing
  anything. Added the missing keep rule.

## [2.6.0] — 2026-06-18

### Added
- App language can now be set from Android's system per-app language settings
  (Android 13+), in addition to the in-app picker in Settings — and the app is
  set up so further languages can be added by community translators

### Fixed
- Changing the app language in Settings now takes effect immediately; the
  picker previously had no effect

## [2.5.0] — 2026-06-17

### Added
- Home-screen widgets (two of them): an "Upcoming" agenda widget — a scrolling
  list of the next month of events grouped under day headers, with refresh and
  "New event" buttons — and a month-grid widget showing the full month with
  today highlighted, connected multi-day event bars, and prev/next/today
  navigation. Both reuse the in-app grouping and layout so they match the app
  exactly, respect your hidden-calendar choices, and refresh automatically when
  the calendar changes or the day rolls over. Tapping a day opens that day;
  tapping an event opens its details
- App shortcut: long-press the Calendula icon for a "New event" action that
  jumps straight into the create-event form
- Agenda view — a fourth top-level view alongside Month/Week/Day: a
  forward-looking list of upcoming events grouped under "Today"/"Tomorrow"/date
  headers, reachable from the view switcher
- Jump to date — a "Jump to date" row in the navigation drawer opens a date
  picker and moves the active view (Month/Week/Day/Agenda) to the chosen day

## [2.4.0] — 2026-06-17

### Added
- Per-event colors: give a single event its own color, instead of always
  inheriting its calendar's. Add the new "Color" field from "More fields" in
  the event form. On calendars that publish their own color set — such as
  Google — you pick from that calendar's palette, so the color is stored
  with the event and shows correctly on every synced device. On local
  calendars you pick from Calendula's palette. "Reset" returns an event to
  its calendar's color
- A new "Allow colors on unsupported calendars" setting (New event form,
  off by default) extends per-event colors to calendars that publish no
  color set of their own (some CalDAV). Such a color is kept on the device
  and may be dropped or overwritten on that calendar's next sync — a
  limitation of those calendars, called out plainly in the setting and on
  the color picker

## [2.3.0] — 2026-06-16

### Changed
- Redesigned Settings around the Material 3 grouped-list pattern: a large
  title that collapses into the toolbar as you scroll, category cards on the
  main screen, and dedicated sub-pages for Appearance, the new-event form, and
  Notifications. The theme, week-start and language pickers now use the app's
  standard option-card dialogs instead of dropdown menus
- About moved to the top of Settings as a card — app icon, author, and quick
  links to the source code and licence — with the version shown plainly at the
  foot of the list
- The Calendars screen now uses the same grouped-card layout and collapsing
  title, and each calendar shows a soft pastel-tinted calendar glyph rather
  than a plain colour swatch
- Redesigned the navigation drawer to match: a branded header, the
  Month / Week / Day switch and your calendars as grouped cards (with the
  active view highlighted), and the whole drawer now scrolls as one

## [2.2.0] — 2026-06-16

### Added
- Tap an empty slot in the day or week view to create an event there: the
  create form opens prefilled with that day and the tapped hour (snapped to
  the hour, one hour long). Tapping an existing event still opens it
- Local calendars: create and manage device-only calendars that live
  entirely on this phone — no account, no sync — from a new "Calendars"
  screen in Settings. Give each a name, a colour, and an optional
  description; rename, recolour, or delete them later. Useful when you want
  a calendar without setting up an account
- The Calendars screen also lists your synced calendars (DAVx5, ICSx5, …)
  grouped by account, each with a "Manage" button that opens the app the
  calendar actually comes from, plus an "Add account" shortcut to the
  system account settings. Calendula never touches a synced calendar's
  server itself — that stays with its own app

### Changed
- Colour swatches in the calendar editor now preview the soft, pastel tone
  a calendar is actually drawn with, instead of a bright raw colour
- The calendar editor reuses the event form's field and button styling for
  a consistent look

## [2.1.0] — 2026-06-15

### Added
- The month view now shows real events in each day instead of coloured
  dots: all-day and multi-day events render as continuous bars at the top
  (a multi-day event is one connected bar across the days it spans, not a
  chip per day), with single-day timed events as filled pills beneath.
  Up to three rows show per day, then a "+N" dot indicator for the rest.
  Each day keeps a rounded surface background, matching the week and day
  views; today is marked with a filled circle on its number
- The slide-out panel now has a "View" section to switch between Month,
  Week, and Day, mirroring the top-bar switcher pill — tapping a view
  selects it and closes the drawer. The current view is highlighted

### Fixed
- Typing in the event title, location, and description fields no longer
  makes the cursor jump around: the form state's round-trip to the UI was
  hopping to a background dispatcher, so the text field saw a lagging value
  while typing. Only the calendar/preferences reads stay off the main
  thread now; the keystroke path is synchronous again

## [2.0.0] — 2026-06-11

### Added
- Conflict handling when saving an edit: if the event changed elsewhere
  (sync, another device) while the form was open, saving now asks whether
  to keep or discard your changes instead of silently overwriting the
  edited fields — and tells you when the event was deleted in the meantime.
  "Keep" still writes only the fields you touched; external changes to
  untouched fields survive either way
- F-Droid store screenshots (German + English), captured with demo data

### Changed
- F-Droid description and README no longer claim the app is read-only —
  they now describe write support and reminder delivery

### Fixed
- `versionName`/`versionCode` bumped to 2.0.0 / 13 — closing out the
  write-support milestone (v1.1 through v2.0)

## [1.4.0] — 2026-06-11

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

### Fixed
- `versionName`/`versionCode` bumped to 1.4.0 / 12

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

[#1]: https://codeberg.org/jlmakiola/calendula/issues/1
[#2]: https://codeberg.org/jlmakiola/calendula/issues/2
[#3]: https://codeberg.org/jlmakiola/calendula/issues/3
[#4]: https://codeberg.org/jlmakiola/calendula/issues/4
[#5]: https://codeberg.org/jlmakiola/calendula/issues/5
[#6]: https://codeberg.org/jlmakiola/calendula/issues/6
