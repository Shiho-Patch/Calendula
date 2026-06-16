# Calendula — Current State

*Last updated: 2026-06-16*

## Status

**Milestone:** 2 (write support) **complete** — v2.0.0 shipped 2026-06-11;
v2.1.0 (month event grid, drawer view tabs, cursor fix) shipped 2026-06-15.
**Phase:** post-2.1 backlog work. v2.2.0 (tap-to-create in day/week + local
calendar management with per-calendar "manage in source app" deep-links)
shipped 2026-06-16. The backlog is now organised by theme in `ROADMAP.md`.

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

- [x] v1.3 edit event (shipped 2026-06-11) — `EventEditScreen` reused for
  edit (detail-screen Edit action, `canModify`-gated, contextual WRITE
  upgrade), dirty-checked partial `update` on the Events row (recurring:
  series DTSTART moves by the user's delta, DURATION instead of DTEND),
  reminder diff by minutes (kept rows keep their method), simple recurrence
  picker (FREQ/INTERVAL/UNTIL/COUNT; complex RRULEs preserved verbatim and
  shown humanized), `EventFormField.Recurrence` incl. settings default,
  recurrence also available on create; domain/mapper/repository tests.
  Review round 1: weekly BYDAY day-toggles in the custom picker ("every week
  on Mon+Fri"). Review rounds 2–4: occurrence edit pulled forward from v2.0
  and made three-way like delete ("this" = exception row via
  `CONTENT_EXCEPTION_URI`, "this and following" = series split, "all" =
  series update); delete equally three-way (truncation via RRULE UNTIL);
  the edit-scope question moved to save time (Google model) — dirty
  recurring saves park in `SaveUiState.AwaitingScope`, a changed rule drops
  the "only this event" option

- [x] v1.4 reminder notifications (shipped 2026-06-11) — exported
  `EVENT_REMINDER` receiver → `CalendarAlerts` (SCHEDULED & due) →
  dedicated channel, tap opens detail (singleTop deep link); best-effort
  FIRED marking; one-time onboarding step requesting `POST_NOTIFICATIONS`
  with duplicate-reminders warning; Settings mirror. Provider only fires
  `METHOD_ALERT` rows (AOSP-verified), so email reminders never reach us

- [x] v2.0 conflict dialog + store polish (shipped 2026-06-11 as v2.0.0) —
  `EditSnapshot` compare on save (overwrite/discard; deleted → close),
  quick-add cut, calendar-switch → v3 backlog; F-Droid/README copy
  refreshed, fastlane screenshots DE+EN captured on-device

- [x] v2.1 (shipped 2026-06-15) — month grid shows real events as
  continuous multi-day bars; navigation-drawer View section
  (Month/Week/Day); cursor-jump fix in event text fields

- [x] v2.2 (shipped 2026-06-16) — tap an empty slot in day/week to create
  (prefilled with that day + tapped hour, snapped to the hour); local
  calendar management in a full-screen editor from Settings →
  Calendars: create/rename/recolor/delete device-only calendars
  (`ACCOUNT_TYPE_LOCAL`, sync-adapter insert) with name, pastel-previewed
  colour, and description (stored in `CAL_SYNC1`); synced calendars listed
  read-only grouped by account with a per-account "manage in source app"
  deep-link (resolved from the account's authenticator: DAVx5/ICSx5/…) and
  an add-account shortcut. Shared `InlineTextField` extracted to `ui.common`

## Next

1. Monitor the F-Droid build/publish for the v2.2.0 tag
2. Decide the "Locations & People" and "remote calendar create/edit"
   go/no-go calls (both hinge on the INTERNET permission) — see `ROADMAP.md`
3. **Settings redesign & restructure** is the agreed high-prio next item
   (2026-06-16) — group into M3 cards / sub-screens, and migrate the
   theme/week-start/language `DropdownMenu` selectors to the OptionCard modal
   default (current dropdowns violate `option-card-modal-style-default`).
   Structure + style pass only, no new settings features.
4. **Per-event color** follows — reuses the color picker + palette plumbing
   from local calendar management; finishes the create/edit theme.
5. Then agenda view (strategic, backs a future widget); jump-to-date and
   duplicate event remain cheap follow-ups. Full ranked sequence in
   `ROADMAP.md` → "Near-term sequence".
