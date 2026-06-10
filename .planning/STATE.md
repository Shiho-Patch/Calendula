# Calendula — Current State

*Last updated: 2026-06-10*

## Status

**Milestone:** v0.4 — Week view (in progress)
**Phase:** Month + Week views implemented; cross-cutting wiring (detail sheet,
filter, settings, jump-to-date) still stubbed

## Progress

- [x] Design spec written and committed (`docs/superpowers/specs/2026-06-08-calendar-app-design.md`)
- [x] V1 design decisions resolved (App name "Calendula", icon, seed color)
- [x] Plan 01 written and executed — foundation lands (theme, icon, i18n, Hilt, DataStore, CI green)
- [x] Plan 02 written and executed — data layer + permission flow + debug screen
- [x] Month view (S1) — 6-week grid, event dots, today marker, swipe nav, three states (replaces debug screen)
- [x] Week view (S2) — time schedule with overlap-resolved lanes, all-day strip, swipe nav, three states
- [x] View-switcher (M1) wired — cycles Month ↔ Week (Day joins once S3 lands)
- [ ] Day view (S3)
- [ ] Event-detail sheet (S4) — week/month event taps are currently no-ops
- [ ] Filter sheet (M3), Settings (M4), Jump-to-date (M2) — drawer entries stubbed

## Next

1. Day view (S3) — slot it into the view-switcher cycle
2. Event-detail sheet (S4) — wire month-day and week-event taps to it
3. Revisit month/week UI polish + shared anchor-date continuity across views
