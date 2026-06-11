# Calendula — Current State

*Last updated: 2026-06-11*

## Status

**Milestone:** v0.6 — Full event read (complete)
**Phase:** All V1 screens done and the read model is now complete — the detail
view surfaces every readable `CalendarContract` field. Next up is a UI
polish/QA pass before v1.0

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

## Next

1. UI polish / QA pass across all views before v1.0
2. F-Droid release of v1.0
