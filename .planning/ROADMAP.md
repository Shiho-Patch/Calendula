# Calendula — Roadmap

## v0.x — Pre-Release

| Version | Milestone | Status |
|---|---|---|
| v0.1 | Foundation & CI | complete |
| v0.2 | Data Layer & Permission Flow | complete |
| v0.3 | Month + Week + Day views, view switcher | complete |
| v0.4 | Event Detail (S4) + humanized recurrence | complete |
| v0.5 | Calendar filter (M3) + Settings (M4) | complete |
| v1.0 | Polish pass, F-Droid release | pending |

Delivery ran ahead of the original table: Day view (S3) shipped in v0.3 and
Event Detail (S4) in v0.4, so the Filter/Settings milestone became v0.5.

Jump-to-date (the date-picker half of M2) was **cut from scope** and will not
ship. The "Today" half of M2 already shipped in v0.5 (drawer entry). V1 is
therefore feature-complete; only a polish/QA pass remains.

## v1.0 — First Public Release

All V1 features shipped, polished, on F-Droid. Read-only calendar.
Remaining before v1.0: a UI polish/QA pass.

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
