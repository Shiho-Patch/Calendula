# Calendula — Current State

*Last updated: 2026-06-11*

## Status

**Milestone:** v2.0 — Write support (milestone 2, in progress)
**Phase:** v1.1.0 shipped 2026-06-11 (write foundation + delete). Milestone 2
runs in four slices (`docs/superpowers/plans/2026-06-11-03-write-support.md`);
next up is v1.2 (create event).

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

## Next

1. v1.2 — create event: form screen, FAB, default-calendar pref, `insertEvent`
2. Monitor the F-Droid build/publish for v1.1.0
