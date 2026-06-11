# Calendula — Requirements

See full design spec: `docs/superpowers/specs/2026-06-08-calendar-app-design.md`

## V1 Scope (Variant "B") — shipped in full (v1.0.0, 2026-06-11)

- [x] Foundation & CI infrastructure — v0.1.0 (2026-06-08)
- [x] Data Layer over `CalendarContract`
- [x] Permission flow (`READ_CALENDAR`)
- [x] Month view (S1)
- [x] Week view (S2)
- [x] Day view (S3)
- [x] Event Detail Sheet (S4) — became a full screen, plus full event read (v0.6)
- [x] Multi-Calendar Filter (M3)
- [x] Today button (M2) — shipped v0.5; Jump-to-Date **cut from scope**
- [x] View-Switcher (M1)
- [x] Settings screen (M4)
- [x] Empty / no-permission / no-calendars states
- [x] German + English localization
- [x] Loading/Failure/Success states per screen (architectural pattern)

## V2 Scope — write support, shipped in full (v2.0.0, 2026-06-11)

- [x] Write foundation: `WRITE_CALENDAR`, read-only-calendar detection, delete (v1.1)
- [x] Create event: form, FAB, last-used calendar (v1.2; polish v1.2.1)
- [x] Edit event: shared form, scoped recurring writes, recurrence picker (v1.3)
- [x] Reminder notifications (v1.4) — **reversal of the original
  "system handles reminders" assumption:** Calendula targets
  sole-calendar-app users, so it posts reminder notifications itself
  (Etar model), incl. `POST_NOTIFICATIONS` onboarding
- [x] Conflict dialog on save + store polish (v2.0)
- Quick-add — **cut from scope** (the prefilled form covers it)
- Calendar switching while editing — moved to v3 backlog

### Out of Scope (V3+)

- Home-screen widget
- Full-text search
- Tablet/foldable-specific layouts
- Locations & People ideas (contact picker, OSM autocomplete) — see
  `ROADMAP.md` idea backlog, undecided
- iOS support (Android-only by design)

## Constraints

- **Tech stack:** Kotlin + Jetpack Compose + Material 3 Expressive, Hilt, DataStore
- **Tech stack pin:** Hilt 2.59.2 + KSP 2.3.9; Kotlin 2.3.21 (KSP for Kotlin 2.4.0 not released yet). Material 3 pinned to `1.5.0-alpha21` (Expressive APIs only exist in alpha). Re-evaluate when KSP/Material3 stable land.
- **Platform:** Android 10+ (API 29 minimum), Android 16 (API 36) target
- **Offline-first:** all data lives in `CalendarContract`; no app-side network
- **Privacy:** zero telemetry, no analytics
- **i18n:** German + English from day one
- **Tests + CI from day one**
- **License:** MIT
