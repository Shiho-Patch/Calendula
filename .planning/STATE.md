# Calendula — Current State

*Last updated: 2026-06-10*

## Status

**Milestone:** v0.5 — Calendar filter (M3) + Settings (M4) (complete)
**Phase:** All V1 screens done. Jump-to-date (date-picker half of M2) cut from
scope. Next up is v0.6 — full event read (surface every readable
`CalendarContract` field) — then a polish/QA pass before v1.0

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

## Next

1. v0.6 — full event read: reminders, status, availability, attendee role +
   self-status, timezone (when it differs), URL, access level. Read the
   `CalendarContract` columns we don't yet pull and show them in the detail
   view. (Planned, not started — implement another day.)
2. UI polish / QA pass across all views before v1.0
3. F-Droid release of v1.0
