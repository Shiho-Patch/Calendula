# Calendula — Current State

*Last updated: 2026-06-22*

## Status

**Milestone:** 2 (write support) **complete** — v2.0.0 shipped 2026-06-11.
**Phase:** post-2.x theme-based backlog work (organised in `ROADMAP.md`).
**Latest released tag: v2.7.5.** The whole Tier 4 (reliability/data-safety/
interop) arc is now done or in flight:
- v2.4.0 per-event colors (2026-06-17)
- v2.5.0 jump-to-date, Agenda view, agenda + month home-screen widgets, "New
  event" launcher shortcut (2026-06-17)
- v2.6.0 default reminders (global + per-calendar override, all-day default,
  battery-exemption row) + system per-app language (2026-06-18)
- v2.7.0 **`.ics` engine** — single-event share, local-calendar backup export,
  open/receive `.ics` (single → form, many → bulk import, dedup by UID)
  (2026-06-18)
- v2.7.1–v2.7.5 — crash-reporting + F-Droid reproducible-build hardening + fixes

**Next release `release/v2.8.0` (integration branch, not yet cut to main):**
holds crash reports via the public Codeberg tracker (MR !27) + reminder
snooze/dismiss notification actions (MR !28). Version bump to 2.8.0 happens at
release-cut.

## Progress

- [x] Design spec written and committed (`docs/superpowers/specs/2026-06-08-calendar-app-design.md`)
- [x] V1 design decisions resolved (App name "Calendula", icon, seed color)
- [x] Plan 01 written and executed — foundation lands (theme, icon, i18n, Hilt, DataStore, CI green)
- [x] Plan 02 written and executed — data layer + permission flow + debug screen
- [x] Month view (S1) — 6-week grid, event dots, today marker, swipe nav, three states (replaces debug screen)
- [x] Week view (S2) — time schedule with overlap-resolved lanes, all-day strip, swipe nav, three states
- [x] Day view (S3) — single-column slice reusing the week layout
- [x] View-switcher (M1) wired — cycles Month ↔ Week ↔ Day
- [x] Event-detail screen (S4) — full-screen, humanized recurrence
- [x] Filter sheet (M3) — per-calendar visibility, grouped by account, persisted, applied centrally in the repository
- [x] Settings (M4) — appearance (theme, dynamic colour, week start), language (per-app locales), about
- [~] Jump-to-date (M2) — **cut from scope**; "Today" half shipped in v0.5, date-picker dropped
- [x] Full event read (v0.6) — reminders, status, availability, access level,
  attendee role + self-response, foreign timezone, and linkified description
  URLs in the detail view; new domain enums + mapper unit tests. (A dedicated
  URL field was cut — no `CalendarContract` column backs it.)

- [x] v1.1 write foundation — `WRITE_CALENDAR` (onboarding asks READ+WRITE,
  only READ gates; contextual upgrade for v1.0 installs), read-only-calendar
  detection (`CALENDAR_ACCESS_LEVEL` → `canModifyContents`, actions hidden for
  WebCal/birthday calendars), delete from the detail screen (recurring:
  "only this event" via cancelled exception / "all events in the series"),
  repository + mapper tests

- [x] v1.2 create event — full-screen `EventEditScreen` (title, all-day,
  M3 date/time pickers with duration-preserving start moves, writable-only
  calendar picker preselecting the last-used calendar, location, description),
  "+" FAB on all three views prefilled with the visible day, `insertEvent`
  with provider-correct all-day normalisation (UTC midnights, exclusive end),
  domain/mapper/repository tests

- [x] v1.3 edit event (shipped 2026-06-11) — `EventEditScreen` reused for
  edit (detail-screen Edit action, `canModify`-gated, contextual WRITE
  upgrade), dirty-checked partial `update` on the Events row (recurring:
  series DTSTART moves by the user's delta, DURATION instead of DTEND),
  reminder diff by minutes (kept rows keep their method), simple recurrence
  picker (FREQ/INTERVAL/UNTIL/COUNT; complex RRULEs preserved verbatim and
  shown humanized), `EventFormField.Recurrence` incl. settings default,
  recurrence also available on create; domain/mapper/repository tests.
  Review round 1: weekly BYDAY day-toggles in the custom picker ("every week
  on Mon+Fri"). Review rounds 2–4: occurrence edit pulled forward from v2.0
  and made three-way like delete ("this" = exception row via
  `CONTENT_EXCEPTION_URI`, "this and following" = series split, "all" =
  series update); delete equally three-way (truncation via RRULE UNTIL);
  the edit-scope question moved to save time (Google model) — dirty
  recurring saves park in `SaveUiState.AwaitingScope`, a changed rule drops
  the "only this event" option

- [x] v1.4 reminder notifications (shipped 2026-06-11) — exported
  `EVENT_REMINDER` receiver → `CalendarAlerts` (SCHEDULED & due) →
  dedicated channel, tap opens detail (singleTop deep link); best-effort
  FIRED marking; one-time onboarding step requesting `POST_NOTIFICATIONS`
  with duplicate-reminders warning; Settings mirror. Provider only fires
  `METHOD_ALERT` rows (AOSP-verified), so email reminders never reach us

- [x] v2.0 conflict dialog + store polish (shipped 2026-06-11 as v2.0.0) —
  `EditSnapshot` compare on save (overwrite/discard; deleted → close),
  quick-add cut, calendar-switch → v3 backlog; F-Droid/README copy
  refreshed, fastlane screenshots DE+EN captured on-device

- [x] v2.1 (shipped 2026-06-15) — month grid shows real events as
  continuous multi-day bars; navigation-drawer View section
  (Month/Week/Day); cursor-jump fix in event text fields

- [x] v2.2 (shipped 2026-06-16) — tap an empty slot in day/week to create
  (prefilled with that day + tapped hour, snapped to the hour); local
  calendar management in a full-screen editor from Settings →
  Calendars: create/rename/recolor/delete device-only calendars
  (`ACCOUNT_TYPE_LOCAL`, sync-adapter insert) with name, pastel-previewed
  colour, and description (stored in `CAL_SYNC1`); synced calendars listed
  read-only grouped by account with a per-account "manage in source app"
  deep-link (resolved from the account's authenticator: DAVx5/ICSx5/…) and
  an add-account shortcut. Shared `InlineTextField` extracted to `ui.common`

- [x] v2.3 settings/calendars/drawer redesign (shipped 2026-06-16) — adopted a
  shared Material 3 grouped-list blueprint, modelled on the ReFra gallery app
  and extracted to `ui/common/GroupedList.kt` (`CollapsingScaffold` with a
  `LargeTopAppBar` exit-until-collapsed title; `GroupedRow` with Position-based
  corner grouping, press-animated corners, `selected` + `minHeight` knobs).
    - Settings: category hub (About card on top → version mark at the foot) with
      sliding sub-pages (Appearance / New event form / Notifications); token-
      based icon chips; theme/week-start/language pickers migrated from
      `DropdownMenu` to OptionCard dialogs. New `ic_gitea.xml` (Simple Icons,
      verbatim path) for the About "Source" button; en+de strings.
    - Calendar manager: same collapsing scaffold + grouped rows; shared
      `CalendarColorChip` (neutral chip, pastelised calendar glyph).
    - Navigation drawer: branded header, grouped View switcher (active view
      highlighted via `secondaryContainer`), the filter list restyled to
      grouped rows with a trailing checkbox; the whole drawer scrolls as one.
    - Cards use `surfaceContainerHigh` for readable contrast against `surface`.
    - Donate button on the About card deferred (target still TBD).

- [x] v2.4 per-event color (shipped 2026-06-17) — an optional "Color" field in
  the event form. Read/render already resolved `EVENT_COLOR` with a calendar
  fallback; this adds the write side and the picker. Palette-backed calendars
  (Google, some CalDAV) pick from the account's `Colors` (`TYPE_EVENT`) and
  write `EVENT_COLOR_KEY` so the color round-trips through sync; local
  calendars write a raw `EVENT_COLOR` from the shared `CALENDAR_COLOR_PALETTE`
  (extracted with the swatch row to `ui/common/ColorSwatchRow.kt`). Switching
  calendars resets the choice (a key is account-scoped). A settings toggle
  ("Allow colors on unsupported calendars", off by default) extends the raw
  path to synced calendars with no palette, with an honest "may not survive
  sync" warning on the picker and in Settings. Color writes flow through
  insert / dirty-checked update / occurrence-exception; mapper + form tests.

- [x] v2.5 (shipped 2026-06-17) — Agenda view (4th top-level view),
  jump-to-date drawer date picker, two home-screen widgets (scrolling
  "Upcoming" agenda + month grid), and a "New event" launcher long-press
  shortcut

- [x] v2.6 (shipped 2026-06-18) — default reminders: global timed default +
  separate all-day default + per-calendar override (timed), applied on create
  with dirty-flag handling; three pickers + override list in Settings →
  Notifications; battery-optimisation exemption row (status + system deep-link,
  no new permission). Plus system per-app language (Android 13+) and an
  immediate-effect fix for the in-app language picker

- [x] v2.7 (shipped 2026-06-18) — the `.ics` engine: share a single event as
  `.ics` from the detail screen; back up local calendars (Settings → Calendars
  → Export) to a SAF file; open/receive an `.ics` — one event prefills the
  create form, many events open a bulk import into a chosen calendar (dedup by
  UID, skip-and-report unrepresentable VTIMEZONE / RECURRENCE-ID / guests).
  Hand-rolled serializer/parser, zero deps. Plus all-day single-day UTC fix and
  a widget R8 keep-rule crash fix

- [x] v2.7.1–v2.7.5 (2026-06-21) — launch crash fix (listener before grant),
  user-controlled crash reporting, widget loading-spinner R8 keep rule, and
  F-Droid reproducible-build cleanups for the official repo

- [~] release/v2.8.0 (not yet cut) — crash reports via the public Codeberg
  tracker (MR !27) + reminder snooze/dismiss notification actions (MR !28,
  snooze self-schedules an exact alarm; primary delivery stays provider-broadcast)

## Next

1. Cut **v2.8.0** from `release/v2.8.0` (bump versionName → tag via the
   merge-driven pipeline) once on-device review signs off
2. **Attendee editing** — the committed next feature (Tier 5 #12, high-
   importance, opened 2026-06-22). Attendees are already read & shown on the
   detail screen since v0.6; the gap is the write side — make guests editable
   in `EventEditScreen` / `EventForm` (add by typed email, role, remove),
   persisted by diffing `CalendarContract.Attendees`. No new permission for the
   typed-email path. **Invitation behavior DECIDED 2026-06-22: record-only on all
   writable calendars** — Calendula never sends (no INTERNET); honest backend-aware
   copy ("your account may email guests when it syncs" on synced calendars, "no one
   is notified" on local). Full sketch in `ROADMAP.md` → "Attendee editing".
3. Then: the two INTERNET go/no-go calls (OSM autocomplete, remote calendar
   create/edit) and Tier 2/3 leftovers (quick-settings tile, now-line, week
   numbers in month, full-text search, accessibility pass). Drag-and-drop
   rescheduling is **rejected**.
