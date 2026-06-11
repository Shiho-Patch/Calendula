# Calendula — Roadmap

## v0.x — Pre-Release

| Version | Milestone | Status |
|---|---|---|
| v0.1 | Foundation & CI | complete |
| v0.2 | Data Layer & Permission Flow | complete |
| v0.3 | Month + Week + Day views, view switcher | complete |
| v0.4 | Event Detail (S4) + humanized recurrence | complete |
| v0.5 | Calendar filter (M3) + Settings (M4) | complete |
| v0.6 | Full event read — surface every readable field | complete |
| v1.0 | First public release — polish pass, F-Droid | complete |

Delivery ran ahead of the original table: Day view (S3) shipped in v0.3 and
Event Detail (S4) in v0.4, so the Filter/Settings milestone became v0.5.

Jump-to-date (the date-picker half of M2) was **cut from scope** and will not
ship. The "Today" half of M2 already shipped in v0.5 (drawer entry).

## v0.6 — Full event read

Round out the read-only model so a detail view shows everything the system
actually stores, before write support starts. Scope = `CalendarContract`
columns we don't yet read/display:

- **Reminders** (`VALARM`) — read `CalendarContract.Reminders`, list lead times
- **Status** — Confirmed / Tentative / Cancelled (cancelled shown struck-through)
- **Availability** (`TRANSP`) — Free / Busy chip
- **Attendee extras** — role (required / optional / organizer) + the user's own
  `SELF_ATTENDEE_STATUS`
- **Timezone** (`EVENT_TIMEZONE`) — shown only when it differs from the device zone
- **URL** — ~~tappable link card~~ **cut**: `CalendarContract` exposes no
  `Events.URL` column (only `CUSTOM_APP_URI`, an originating-app deep-link).
  URLs are instead surfaced by linkifying the description text
- **Access level / class** (private / confidential) — small chip (optional, trivial)

All of the above shipped in v0.6.0 (2026-06-11).

Deliberately out of v0.6:
- Recurrence exception / modified-occurrence badges — `Instances` already
  resolves correct per-occurrence times for display; this only matters for
  editing, so it folds into v2
- `CATEGORIES`, `ATTACH` — not reliably exposed by `CalendarContract`
  (provider limitation, not our choice)

## v1.0 — First Public Release — shipped 2026-06-11

All V1 features shipped, polished, on F-Droid. Read-only calendar. Cut directly
after v0.6 (full event read) plus the onboarding-screen polish pass.

### Polish backlog (pre-1.0)
- ~~Redesign the initial grant-access (permission) screen~~ — **done**
  (Material 3 Expressive onboarding, shipped in v0.6.0 / v1.0.0)

## v2.0 — Write Support (in progress)

Delivered in four releasable slices (plan:
`docs/superpowers/plans/2026-06-11-03-write-support.md`). The V1 spec is a
guide here, not a contract — scope per slice is decided as we go.

| Version | Milestone | Status |
|---|---|---|
| v1.1 | Write foundation — `WRITE_CALENDAR`, read-only-calendar detection, delete (series + single occurrence) | complete (shipped 2026-06-11) |
| v1.2 | Create event — form, FAB, last-used-calendar preselect | complete (shipped 2026-06-11) |
| v1.2.1 | Form polish after on-device review — card design system, optional fields + settings defaults, OptionCard dialogs, expressive motion | complete (shipped 2026-06-11) |
| v1.3 | Edit event — shared form, scoped recurring writes (this / following / all), recurrence picker | complete (shipped 2026-06-11) |
| v1.4 | Reminder notifications — see below | complete (shipped 2026-06-11) |
| v2.0 | Conflict dialog, polish pass (stale read-only copy in F-Droid/README), release | planned |

v2.0 scope was re-cut on 2026-06-11, after v1.4:
- **Occurrence edit** already shipped early, in v1.3.
- **Quick-add** is **cut from scope**: the full form already opens prefilled
  (visible day, last-used calendar, optional fields hidden), so the sheet
  would only save one screen transition while adding a second create-surface
  to maintain. Revisit only if real-world feedback says creation feels heavy.
- **Calendar switching while editing** moves to the v3 backlog (sync-adapter
  minefield: `CALENDAR_ID` is sync-adapter-owned, AOSP locks the field; an
  honest implementation is copy+delete like Google Calendar, with sync-identity
  and attendee side effects).
- **Conflict dialog** stays (plan 03, decision 5): on save, compare against
  the row as it was when the form loaded; on external change, ask
  overwrite / discard. Closes the silent-clobber gap on synced calendars.

## v1.4 — Reminder Notifications

**Essential**, not nice-to-have: Calendula targets users for whom it is their
*only* calendar app, so reminder delivery can't be delegated to Google/OEM
Calendar. The calendar provider schedules reminders and broadcasts
`android.intent.action.EVENT_REMINDER`, but it does **not** post the visible
notification — a calendar app must. We become that app (the Etar model).

Scope:
- Manifest-registered `BroadcastReceiver` for `EVENT_REMINDER`
  (data scheme `content://com.android.calendar`) — wakes us at reminder time,
  no foreground service.
- Read `CalendarContract.CalendarAlerts` / `Reminders`, filter to
  `METHOD_ALERT` / `METHOD_DEFAULT` (skip `METHOD_EMAIL`); post on a dedicated
  notification channel; tap opens event detail.
- `POST_NOTIFICATIONS` runtime permission (API 33+) — requested in onboarding.
- Onboarding step: (a) request `POST_NOTIFICATIONS`, (b) in-app reminders
  toggle, **default ON**, with copy warning that a second calendar app with
  notifications on will cause duplicate reminders. Mirrored into Settings
  (reversible).

Deliberately deferred (add only if needed):
- Snooze / dismiss notification actions (Etar has them)
- Battery-optimization exemption prompt for delivery reliability

## v3.0 — Power-User Features

- Home-screen widget
- Full-text search
- Tablet / foldable layouts
- Optional: ICS file import (drag-and-drop)
- Optional: move event to another calendar (copy+delete model with a
  consequences warning — deferred from v2.0, see above)

Order is indicative — community feedback after V1 may re-prioritize.
