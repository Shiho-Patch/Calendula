# Calendula — Current State

*Last updated: 2026-06-11*

## Status

**Milestone:** v1.0.0 — First public release (shipped 2026-06-11)
**Phase:** V1 is complete and released. All screens done, the read model
surfaces every readable `CalendarContract` field, and the onboarding screen
got its Material 3 Expressive polish pass. Next horizon is v2.0 (write support)

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

1. v1.0.0 released — monitor the F-Droid build/publish
2. Plan v2.0 (write support: create / edit / delete, quick-add, conflict UX)
