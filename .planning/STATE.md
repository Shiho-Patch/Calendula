# Calendula — Current State

*Last updated: 2026-06-10*

## Status

**Milestone:** v0.5 — Calendar filter (M3) + Settings (M4) (complete)
**Phase:** All V1 screens and cross-cutting wiring done except jump-to-date
(M2), which is deferred to the v1.0 polish pass

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
- [ ] Jump-to-date (M2) — drawer entry still stubbed (deferred to v1.0)

## Next

1. Jump-to-date (M2) — date picker from the drawer, reachable on every view
2. UI polish / QA pass across all views before v1.0
3. F-Droid release of v1.0
