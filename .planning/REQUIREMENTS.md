# Calendula — Requirements

See full design spec: `docs/superpowers/specs/2026-06-08-calendar-app-design.md`

## V1 Scope (Variant "B")

### Validated (shipped)
- Foundation & CI infrastructure — v0.1.0 (2026-06-08)

### Active (V1)

- [x] Foundation & CI infrastructure
- [x] Data Layer over `CalendarContract`
- [x] Permission flow (`READ_CALENDAR`)
- [ ] Month view (S1)
- [ ] Week view (S2)
- [ ] Day view (S3)
- [ ] Event Detail Sheet (S4)
- [ ] Multi-Calendar Filter (M3)
- [x] Today button (M2) — shipped v0.5; Jump-to-Date **cut from scope**
- [ ] View-Switcher (M1)
- [ ] Settings screen (M4)
- [ ] Empty / no-permission / no-calendars states
- [ ] German + English localization
- [ ] Loading/Failure/Success states per screen (architectural pattern)

### Out of Scope (V2+)

- Event create / edit / delete (V2)
- Home-screen widget
- Full-text search
- Quick-add
- Custom notifications/reminders (system already handles these)
- Tablet/foldable-specific layouts
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
