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

## v2.0 — Write Support (complete, shipped 2026-06-11)

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
| v2.0 | Conflict dialog, polish pass (store copy refresh, F-Droid screenshots), release | complete (shipped 2026-06-11) |

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

## v2.1 — Month event grid + drawer view tabs (shipped 2026-06-15)

- Month grid shows real events as continuous multi-day bars (not just dots)
- View section in the navigation drawer to switch Month / Week / Day
- Fix: text cursor no longer jumps in event text fields

## v2.2 — Tap-to-create + local calendar management (shipped 2026-06-16)

- Tap an empty slot in day/week → create form prefilled with that day + the
  tapped hour (snapped to the hour, 1 h long)
- Local (device-only) calendar management in a full-screen editor from
  Settings → Calendars: create / rename / recolor / delete, with name,
  pastel-previewed colour, and description (stored in `CAL_SYNC1`)
- Synced calendars listed read-only, grouped by account, each with a
  per-account "manage in source app" deep-link (resolved from the account's
  authenticator — DAVx5/ICSx5/…) + an add-account shortcut
- Shared `InlineTextField` extracted to `ui.common` (event form + calendar
  editor share one input style)

## v2.3 — Material 3 grouped-list redesign (shipped 2026-06-16)

A structural + visual pass adopting one shared blueprint (modelled on the ReFra
gallery app) across Settings, the calendar manager and the navigation drawer.

- Shared `ui/common/GroupedList.kt`: `CollapsingScaffold` (a `LargeTopAppBar`
  whose title collapses on scroll) + `GroupedRow` (Position-based corner
  grouping, press-animated corners, `selected` + `minHeight` knobs).
- Settings: category hub with About card on top and sliding sub-pages
  (Appearance / New event form / Notifications); theme/week-start/language
  pickers moved from `DropdownMenu` to OptionCard dialogs; token-based icon
  chips; `ic_gitea.xml` for the About "Source" button.
- Calendar manager + drawer restyled to match; shared `CalendarColorChip`;
  drawer scrolls as one with the active view highlighted.
- Cards use `surfaceContainerHigh` for readable contrast.
- Donate button on the About card deferred (target TBD).

---

# Backlog (theme-based, post-v2.1)

The old v3.0 / "daily-driver polish" / "Locations & People" lists are
consolidated here by theme. Within a group, **(in progress)** /
**(next)** mark what is being or about to be worked; everything else is an
approved-but-unscheduled idea unless tagged **(idea)** /
**(go/no-go)** / **(rejected)**. Order across groups is not a commitment.

## Near-term sequence (ranked, 2026-06-16)

The theme groups below are the full menu; this is the committed *order* for
the next stretch. Ranking favours finishing the current create/edit + calendar
arc before opening new fronts, then cheap-relative-to-value items and ones that
unblock a later item. Order is a plan, not a contract — revisit after each lands.

**Tier 1 — finish the current arc (create/edit + calendars)**
1. Tap-to-create in day/week *(shipped v2.2.0)* — prefilled create from an empty slot
2. Local calendar management + "manage in source app" deep-links *(shipped v2.2.0)*
3. ~~Settings redesign & restructure~~ *(shipped v2.3.0 — grew into the full
   grouped-list blueprint across Settings + calendars + drawer; see "v2.3"
   above)*
4. ~~Per-event color~~ *(shipped v2.4.0)* — palette calendars write
   `EVENT_COLOR_KEY` (sync-safe); local/opted-in calendars write a raw
   `EVENT_COLOR`; off-by-default setting for no-palette synced calendars
5. **Duplicate event** *(next)* — detail action → prefilled create form; near-free on the tap-to-create prefill infra

(Tier 2+ numbering below shifts accordingly; ranking unchanged.)

### Settings redesign & restructure *(shipped v2.3.0)*

The original scope below is kept as a record; the implementation expanded from a
sub-screen restructure into the shared grouped-list blueprint (see "v2.3" above).

The settings screen has grown into a flat vertical scroll of divider-separated
sections (Appearance, Event form, Notifications, Calendars, Language, About) and
will keep accreting rows (per-event-color defaults, default reminder, more
calendar entries are all queued). It needs structure before it gets unwieldy.

**Decided (2026-06-16): sub-screens**, not flat-but-carded. The top level
becomes a category list; each category opens its own destination. More
M3-idiomatic for a settings surface that will keep growing, and it mirrors the
existing Calendars row, which already navigates out to its own screen.

Structure — top-level settings list → category destinations:
- **Appearance** → theme, dynamic colour, week start
- **Event form** → the 6 default-field toggles + the hint text
- **Notifications** → reminders toggle (POST_NOTIFICATIONS flow stays)
- **Calendars** → already its own screen (`CalendarsScreen`); just becomes a
  peer category row, no change to that screen
- **Language** → single control; keep as a top-level row that opens an
  OptionCard directly (a whole sub-screen for one choice is overkill)
- **About** → kept inline on the top-level list as a card (read-only info,
  not worth a navigation hop). Card layout, top → bottom:
  - **Identity** — app logo + name "Calendula", with "by Jean-Luc Makiola"
    as a subtitle beneath the name
  - **Action buttons** (small, button-styled, sit in a row):
    - **Source** — Gitea logo, opens the repo (`about_source_url`)
    - **License** — opens the LICENSE file on Gitea
    - **Donate** *(tentative)* — sits next to Source; target TBD (decide
      before building: Liberapay / Ko-fi / Gitea sponsor / etc.)
  - **Version** — small version number at the bottom of the card

Scope:
- **Navigation** — add the settings sub-screen destinations alongside the
  existing settings/calendars routes in `CalendarHost`; back pops to the
  settings list (mind the existing `BackHandler` that guards against falling
  through to the activity).
- **Fix the dialog-pattern violation** — theme, week-start and language use
  `DropdownMenu`; the project default is the full-width tonal OptionCard modal
  (radio/dropdown/text-list dialogs are banned, see
  `option-card-modal-style-default`). Migrate these selectors to OptionCard.
- **Visual pass** — top-level category rows with leading icons; consistent
  spacing and row affordances aligned with the event-form card design system.

Out of scope (no new settings *features* here) — this is a structure + style
pass on the existing controls; new toggles ride in with their own features.

**Tier 2 — navigation & daily-driver completeness**
5. Jump-to-date — drawer date picker (un-cut from V1); cheap, fills the nav gap
6. Agenda view — the missing 4th view; serves daily-driver users *and* becomes the data source for the widget

**Tier 3 — platform reach (depends on Tier 2)**
7. Home-screen widget — built on the agenda data source from #6
8. App shortcuts (launcher long-press → New event); cheap, optional quick-settings tile

**Tier 4 — interop & bigger-ticket**
9. Share event as .ics + receive/open .ics into a prefilled create form
10. Default reminder applied to new events; then snooze/dismiss notification actions
11. Drag & drop rescheduling in day/week — big-ticket, own slice (recurring drops reuse the scope dialog)

**Gated — explicit go/no-go before any work (mostly INTERNET-permission calls)**
- Remote calendar create/edit (re-implements DAVx5; INTERNET + credential storage)
- Locations & People — contact address picker (no-permission, one-shot) is the safe entry; OSM autocomplete needs INTERNET
- Move event to another calendar — sync-adapter minefield (copy+delete model)

**Unranked / fill-in** — pinch-to-zoom time scale, tablet/foldable layouts,
full-text search, ICS file import. Pulled in opportunistically, not sequenced.

Debatable calls worth a second look: widget (#7) vs .ics interop (#9) ordering;
whether drag-drop (#11) jumps ahead given its daily-driver impact.

## Navigation & views

- ~~Tap an empty slot in day/week → create form prefilled with that
  date+time, snapped to the hour~~ **shipped v2.2.0** (long-press variant
  not added — single tap covers it)
- Agenda view (fourth view: upcoming events grouped by day; also the
  natural data source for a future widget)
- Jump to date — drawer date picker (un-cut from V1)
- Pinch-to-zoom time scale in day/week
- Tablet / foldable layouts *(was v3.0)*
- Full-text search *(was v3.0)*

## Event editing & creation

- Drag & drop rescheduling in day/week (recurring drops reuse the scope
  dialog) — big-ticket, own slice
- Duplicate event (detail action → prefilled create form)
- **Per-event color** (`Events.EVENT_COLOR`, OptionCard picker in the form)
  *(next)* — chosen to follow the in-progress tap-to-create + calendar
  management work: reuses the color-picker component and palette plumbing
  being built for local calendar management, and finishes the create/edit
  theme. `EVENT_COLOR` / `EVENT_COLOR_KEY` from the calendar's color list
  (`Colors` table, `TYPE_EVENT`); falls back to the calendar color when unset.

## Calendars & accounts

- ~~Create / manage local (device-only) calendars~~ **shipped v2.2.0** —
  name + color + description; rename / recolor / delete the calendars the app
  owns. Inserted under `ACCOUNT_TYPE_LOCAL` as a sync adapter; description in
  `CAL_SYNC1`. Full-screen "Calendars" editor reached from Settings.
- ~~Per-calendar "manage in source app" deep-link~~ **shipped v2.2.0** — for
  synced calendars, open the app the calendar actually came from based on
  its `ACCOUNT_TYPE` (DAVx5 `bitfire.at.davdroid`, Google `com.google`,
  …); fall back to system account/sync settings. Plus an "add account"
  entry into system Accounts. Honest boundary for remote calendars.
- **Remote calendar create/edit** *(go/no-go)* — creating a CalDAV
  collection (`MKCALENDAR`) or a Google calendar means an in-app sync
  client: **INTERNET permission, credential storage, the full server
  round-trip** — i.e. re-implementing DAVx5. DAVx5 exposes no public
  intent to delegate the create to it. Cosmetic local edits (color/name)
  to an existing synced row are possible but don't propagate to the server
  and may be overwritten on next sync — not promised. Same explicit
  go/no-go gate as the OSM/INTERNET item below.
- Move event to another calendar (copy+delete model with a consequences
  warning — deferred from v2.0; `CALENDAR_ID` is sync-adapter-owned) *(was v3.0)*

## Reminders, round two

- Snooze + dismiss actions on the notification (snooze needs an
  exact-alarm / WorkManager decision)
- Settings default reminder applied to new events

## Sharing & interop

- Share event as .ics + open/receive .ics into a prefilled create form
  (front-runs the import below)
- ICS file import (drag-and-drop) *(was v3.0, optional)*

## Platform & launchers

- Home-screen widget *(was v3.0)*
- App shortcuts (launcher long-press → New event), maybe a quick-settings tile

## Locations & People *(go/no-go, captured 2026-06-11)*

Beyond classic calendar-client scope; discussed, deliberately not planned
in detail yet:

- **Contact address picker** for the location field via the system picker
  (`ACTION_PICK` on postal addresses) — one-shot, needs no READ_CONTACTS,
  fits the privacy story. Same mechanism later for picking emails.
- **OSM address autocomplete** in the location field (type "Brandenburger
  Tor" → tap suggestion → resolved address inserted). Backend would be
  Photon (Nominatim's public policy forbids autocomplete). **Requires the
  INTERNET permission** — first dent in the "no network access" promise;
  if built: opt-in (off by default), honest copy, configurable endpoint
  for self-hosters, onboarding footnote + F-Droid copy reworded. This
  trade-off is an explicit go/no-go decision before any work starts.
- **Inline contact suggestions** while typing (needs READ_CONTACTS) — only
  if the picker proves clunky.
- **Attendee editing / invites from contacts** — own milestone; writing
  `Attendees` rows touches sync-adapter invitation behavior (Google vs
  DAVx5 differ).

## Consciously rejected

- Travel time / weather / smart suggestions (network, core-promise conflict)
- Natural-language quick entry (high effort, locale-fragile; the prefilled
  form already covers fast entry)
- Quick-add sheet (the prefilled full form already covers it — cut in v2.0)
