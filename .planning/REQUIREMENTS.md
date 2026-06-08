# Calendula — Requirements

See full design spec: `docs/superpowers/specs/2026-06-08-calendar-app-design.md`

## V1 Scope (Variant "B")

### Validated (shipped)
- (none yet — first milestone in progress)

### Active (V1)

- [ ] Foundation & CI infrastructure
- [ ] Data Layer over `CalendarContract`
- [ ] Permission flow (`READ_CALENDAR`)
- [ ] Month view (S1)
- [ ] Week view (S2)
- [ ] Day view (S3)
- [ ] Event Detail Sheet (S4)
- [ ] Multi-Calendar Filter (M3)
- [ ] Today button + Jump-to-Date (M2)
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
- **Platform:** Android 10+ (API 29 minimum), Android 16 (API 36) target
- **Offline-first:** all data lives in `CalendarContract`; no app-side network
- **Privacy:** zero telemetry, no analytics
- **i18n:** German + English from day one
- **Tests + CI from day one**
- **License:** MIT
