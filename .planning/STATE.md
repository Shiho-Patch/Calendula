# Calendula — Current State

*Last updated: 2026-06-11*

## Status

**Milestone:** v2.0 — Write support (milestone 2, in progress)
**Phase:** v1.2.1 shipped 2026-06-11 — the create-form polish pass after
Jean-Luc's on-device review (v1.2.0 and v1.1.0 shipped the same day).
Milestone 2 runs in four slices
(`docs/superpowers/plans/2026-06-11-03-write-support.md`); next up is v1.3
(edit event). Note: UI slices now hold release until his explicit approval.

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

## Next

1. v1.3 — edit event: reuse the form, series edit, reminder edit, simple
   recurrence picker
2. Monitor the F-Droid build/publish for v1.1.0 / v1.2.0
