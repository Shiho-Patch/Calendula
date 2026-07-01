# Design: Contact special-dates calendars (+ per-calendar multiple reminders)

Status: **implemented** on `feat/contact-special-dates` (#14 prerequisite merged in)
Date: 2026-06-30 (implemented 2026-07-01)
Tracking: Codeberg #15 (feature), Codeberg #14 (prerequisite)

Implementation notes / deviations from this design:

- **Event identity key is a deterministic `Events.UID_2445`**
  (`contact-<type>:<lookupKey>@calendula`), not `SYNC_DATA1`. On a LOCAL calendar
  the provider drops `_sync` columns unless written through a sync-adapter URI;
  `UID_2445` is written/read through the normal event URIs the app already uses
  (and doubles as a stable iCal UID). The managed **calendar** still carries a
  `CAL_SYNC2` marker for re-adoption (calendar-row writes already use the
  sync-adapter URI).
- **Age is a sync-time snapshot.** A `FREQ=YEARLY` row has one static title, so
  `{age}` reflects the upcoming birthday at the last sync (refreshed daily / on
  foreground), not a per-occurrence value. Because of this, `{age}` is **kept out
  of the default templates**; users can add it, and the title-format editor shows
  a disclaimer once they do.
- New managed calendars seed a per-calendar all-day reminder default of
  on-the-day + one week before, so birthdays get lead time out of the box.
- Title template is user-editable from day one (`{name}`, `{age}`).

This document captures the full design for surfacing contact birthdays —
and, by extension, anniversaries and custom dates — as auto-updating local
calendars in Calendula. It also specifies #14 (per-calendar *multiple*
default reminders), which is a hard prerequisite and ships first.

The two pieces are deliberately split across **two branches / two
shipments**:

- `feat/per-calendar-multi-reminders` — #14, ships first.
- `feat/contact-special-dates` — #15, builds on #14.

They *may* release together depending on timing, but #14 is independently
useful and is the foundation the special-dates calendars lean on.

---

## Why this is worth doing carefully

Calendula's identity is privacy: **no INTERNET permission**, and historically
**no `READ_CONTACTS`** — guest-picking uses a one-shot `ACTION_PICK` so the app
never holds the contacts permission (`EventEditScreen.kt:446-460`).

This feature **declares `READ_CONTACTS`** for the first time. That is a
deliberate, owner-approved shift, mitigated by:

- The permission is **opt-in and feature-gated** — requested only when the user
  enables the feature, never at startup.
- Everything stays **offline**. Contacts are read locally and mirrored into a
  local calendar; nothing leaves the device.
- The mirror is **one-way** (contacts → calendar). Calendula never writes to
  Contacts.

Store listing / About copy will need to explain the optional permission.

---

## Prerequisite — #14: per-calendar *multiple* default reminders

### Today

- Events already support **multiple reminders** — `EventForm.reminders:
  List<Int>` (`EventForm.kt:24`), each written as a separate
  `CalendarContract.Reminders` row (`CalendarDataSource.kt:620-630`), each
  firing independently. The runtime/notification path is done.
- The **defaults** layer is single-valued, though:
  - Global: `defaultReminderMinutes` / `defaultAllDayReminderMinutes` — single
    `Int` (`SettingsPrefs.kt:287,302`).
  - Per-calendar override: `perCalendarReminderOverride: Map<Long, Int?>` and
    the all-day variant (`SettingsPrefs.kt:350-381`), resolved by
    `resolveDefaultReminder()` (`SettingsPrefs.kt:509-524`).
  - UI: expandable per-calendar override section
    (`SettingsScreen.kt:879-953`); `ReminderDefaultPicker` (`:956+`).

### The change

Widen the **defaults**, not the runtime, from one reminder to a list:

1. Resolution model `Int` → `List<Int>` in global defaults + per-calendar
   override + `resolveDefaultReminder()`. The DataStore string format already
   parses `id=value;id=value` (`SettingsPrefs.kt:576-577`); `value` becomes a
   comma-separated list. Keep the `Inherit / None / Minutes` override states
   (`CalendarReminderOverride`, `SettingsPrefs.kt:493-500`) — `Minutes` carries
   a list.
2. `ReminderDefaultPicker` → multi-select. Mirror the event-form multi-reminder
   UI rather than inventing new interaction.
3. New events seed `EventForm.reminders` from the **resolved list** instead of
   a single value.

No new permissions, no provider changes, no scheduling changes. Type widening
plus one picker upgrade.

### Why it gates #15

Each special-dates calendar is a normal local calendar, so it inherits the
**per-calendar reminder default** for free. A birthdays calendar is useless
without lead time (the reporter literally asked for "a week before *and* on the
day") — that requires *multiple* defaults, i.e. #14.

---

## #15: Contact special-dates calendars

### Core model: one-way mirror, one local calendar per type

Contacts → **separate local calendars per type**:

- "Birthdays"
- "Anniversaries"
- "Custom dates" (everything else / `TYPE_OTHER` / `TYPE_CUSTOM`)

Separate-per-type is the key architectural decision: each is a normal local
calendar (`createLocalCalendar()`, `CalendarDataSource.kt:238-260`), so the
**existing per-calendar infra applies for free**:

- **Color** → existing per-calendar color.
- **Show/hide** → existing calendar visibility.
- **Default reminders** → per-calendar override from #14.

This shrinks the new surface dramatically: anniversaries and custom dates are
near copy-paste of birthdays (same engine, different `ContactsContract` data
kind, one extra local calendar each), and the settings sub-page does **not**
need to reinvent color/visibility/reminders.

### Events: field-level managed, not read-only

Calendula manages a few fields; the user owns the rest.

- **Managed** (overwritten on every sync, disabled in the editor for these
  events): title, start date, recurrence (`FREQ=YEARLY`), existence.
- **User-owned** (seeded once on insert, never touched again by sync):
  location, reminders, notes, busy/free.

The rule that makes this safe: sync performs a **targeted column update**
(managed columns only), **never delete-and-reinsert**. Reminders live in
separate rows, so once seeded they are simply never re-touched. A user can move
a birthday reminder to "2 weeks before" or add a location and neither is
clobbered.

In the editor, when an event belongs to a managed calendar: disable the
title/date/recurrence fields; allow the rest.

### Reconciliation identity

Stamp each event with the contact's `LOOKUP_KEY` in `Events.SYNC_DATA1` (ours,
since it's a local calendar). Sync becomes an idempotent diff keyed on it:

- contact added → insert
- birthday/date changed → targeted update of managed columns
- contact or date removed → delete the event

Getting this wrong = duplicate birthdays on every sync, so the identity key is
non-negotiable.

### Data source

`ContactsContract.Data` rows of `Event.CONTENT_ITEM_TYPE`, split by
`Event.TYPE`:

- `TYPE_BIRTHDAY` → Birthdays calendar
- `TYPE_ANNIVERSARY` → Anniversaries calendar
- `TYPE_OTHER` / `TYPE_CUSTOM` (+ label) → Custom dates calendar

### Dates: year-less + age

Many contacts store `--MM-DD` (no year). Handle both:

- **Year present:** anchor `DTSTART` at that year; `FREQ=YEARLY`. We can show
  age ("Jane turns 30") when the show-age option is on.
- **Year absent:** `FREQ=YEARLY` from an arbitrary anchor year; no age shown.

Recurrence support is already there: `FREQ=YEARLY` + `UNTIL`/`COUNT`/`INTERVAL`
(`Recurrence.kt:32-174`).

### Auto-update cadence

Birthdays change rarely, so nothing live/expensive:

- Sync **on enable**, **on app foreground**, and a **daily WorkManager** job
  (catches new/edited/deleted contacts even if the app isn't opened).
- **No `ContentObserver`** — needs the process alive and buys almost nothing
  for once-a-year events.

### Permission + lifecycle

- **Enable:** request `READ_CONTACTS` (feature-gated, contextual — mirror the
  contextual `WRITE_CALENDAR` pattern at `EventEditScreen.kt:216-231`). On
  grant, create the enabled calendars and run the first sync.
- **Disable:** delete the managed calendars + their events (with a confirm).
- **Permission revoked in system settings:** detect on next sync; surface that
  the feature is stalled rather than failing silently.

### Configurable settings sub-page

Enabling the feature reveals a **dedicated sub-page** (not inline rows). Because
color/visibility/reminders already live in normal calendar settings, the
sub-page only carries the genuinely new knobs:

- Master enable toggle.
- Per-type toggles: Birthdays / Anniversaries / Custom dates.
- Title/display template, e.g. `"{name}'s birthday"` (new translatable string).
- Show-age toggle (only meaningful when the year is known).
- (Possibly) sync-cadence / "sync now".
- Pointer to the normal calendar settings for color, visibility, reminders.

The owner wants this broadly configurable — keep the sub-page the home for
display/behaviour options, and lean on existing per-calendar settings for the
rest.

---

## Open questions / deferred

- Title template: free-form vs a small set of presets. Start with one good
  localized default; revisit configurability.
- Whether "Custom dates" is one bucket or split further by label. Start: one
  bucket.
- Contact scope: all contacts vs per-account/group filtering. Start: all;
  filtering is a later refinement.

## Task outline

### #14 — per-calendar multiple default reminders (`feat/per-calendar-multi-reminders`)
- [x] Widen global + per-calendar reminder defaults `Int` → `List<Int>`
      (`SettingsPrefs.kt`), keep `Inherit/None/Minutes(list)`.
- [x] `resolveDefaultReminder()` returns a list.
- [x] `ReminderDefaultPicker` → multi-select (mirror event-form UI).
- [x] Seed new events' `EventForm.reminders` from the resolved list.
- [x] Migration / parse compatibility for existing single-value stored prefs.
- [x] Tests: resolution, parse round-trip, all-day variant.

### #15 — contact special-dates (`feat/contact-special-dates`)
- [x] Declare `READ_CONTACTS`; feature-gated contextual request on enable.
- [x] Per-type local calendars (create on enable, delete on disable+confirm).
- [x] Contacts reader: `Event` rows by `TYPE`, year-less handling.
- [x] Sync engine: idempotent diff keyed on the deterministic `UID_2445`
      (see notes; not `SYNC_DATA1`); targeted managed-column updates; seed
      user-owned fields on insert only.
- [x] `FREQ=YEARLY` event generation + age (when year known; sync-time snapshot).
- [x] Auto-update: on enable, on foreground, daily WorkManager.
- [x] Editor: disable managed fields for managed-calendar events.
- [x] Revoked-permission detection + stalled-state surface.
- [x] Settings sub-page: enable, per-type toggles, title template, show-age.
- [x] Translatable strings (titles, sub-page).
- [x] About / store copy for the optional `READ_CONTACTS` permission.
