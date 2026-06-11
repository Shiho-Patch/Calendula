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

## v2.0 — Write Support

- Event create / edit / delete via `CalendarContract` writes
- Quick-add sheet
- Conflict UX (event modified externally during edit)

## v3.0 — Power-User Features

- Home-screen widget
- Full-text search
- Tablet / foldable layouts
- Optional: ICS file import (drag-and-drop)

Order is indicative — community feedback after V1 may re-prioritize.
