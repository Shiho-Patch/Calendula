# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Changed
- Month-view event titles no longer end in an ellipsis. The "…" took the width
  of a couple of characters and told you nothing you couldn't already see, so
  the title now simply runs to the edge of its chip — a few more letters per
  cell, which is often the difference between two events you can tell apart and
  two you can't ([#164]).

## [2.19.1] — 2026-08-11

### Added
- **Drag to reschedule can be turned off.** Settings → Views → *Drag to
  reschedule*, on by default. Off, no event can be picked up in the month, week
  or day view — for anyone who kept moving appointments by accident while
  scrolling ([#173]).
- An early Brazilian Portuguese translation. Calendula has started speaking
  Brazilian Portuguese, contributed as a community translation through
  [Calendula's Weblate](https://weblate.dev.jeanlucmakiola.de/projects/calendula/).
  About a third of the app is covered, so the rest still shows in English — you
  can pick it under Settings → Language or in Android's per-app language
  settings. Thanks to
  [bkrz](https://weblate.dev.jeanlucmakiola.de/user/bkrz/) for getting it
  started; help finishing it is very welcome.

### Fixed
- **The language picker forgot which language was picked.** Android hands the
  applied language back with its script filled in — Simplified Chinese goes in
  as `zh-CN` and comes out as `zh-Hans-CN` — so after a restart no entry in
  Settings → Language showed as selected, and the row above it named the
  language the long way round. Only languages that imply a script were
  affected.

## [2.19.0] — 2026-08-10

### Added
- **Drag an event to reschedule it.** Press and hold an event in the month,
  week or day view and drop it on another time or day — the only way to move an
  appointment used to be opening it and editing its start and end by hand. The
  block follows your finger, the timeline scrolls when you carry it to an edge,
  and the event's length is preserved: a 90-minute meeting dropped on 09:00 ends
  at 10:30. In week and day view the drop snaps to 15-minute steps; in month
  view it moves the event to the day you dropped it on and leaves the time
  alone. A confirmation names the new slot and offers **Undo** wherever the move
  can be taken back in one step. Events you can't edit — read-only calendars, and
  birthdays and anniversaries mirrored from your contacts — can't be picked up.
  Rescheduling isn't pointer-only: a screen reader gets a **Move…** action on
  every event that can be moved ([#68]).
- Dragging a **recurring** event asks what it should apply to, the same *this
  event / this and following / all events* choice a save does. Where moving the
  whole series would change which weekday it falls on, the rule is rewritten to
  match — a weekly Monday event dropped on a Wednesday becomes a weekly
  Wednesday event. Where it can't be rewritten safely, the prompt says so and
  offers to move just the one occurrence, rather than quietly leaving the
  series behind ([#68]).
- **Delete several search results at once.** Long-press a hit in search to enter
  selection mode, tap the rest (or **Select all**), and delete the lot in one
  action — finding and removing, say, every leftover event with the same title
  no longer means opening them one at a time. Recurring hits in the batch ask
  once for the whole selection instead of once each, results on read-only
  calendars can't be selected, and the deletion is confirmed before it runs
  ([#80]).

### Changed
- **First launch is now a short wizard** instead of a single permission screen.
  It counts its steps, each one can be stepped back out of, and beyond granting
  calendar access it offers two things worth deciding once: turning on automatic
  backup — chosen inline, folder picker and all, since events that live only on
  the phone are lost with it — and choosing which view Calendula opens on, with
  a live preview of the month, week, day and agenda layouts rather than four
  names in a list. Both are skippable, everything chosen is changeable in
  Settings afterwards, and the backup step is offered only where nothing
  writable is being synced anywhere. Existing installs are not re-onboarded —
  revoking and re-granting the calendar permission later does not restart the
  wizard ([#163]).
- The one-time notice about calendars switched off for this device is now a step
  in the flow rather than a dialog thrown over the app on first open.

### Fixed
- Removed the deprecated `statusBarColor` / `navigationBarColor` theme
  attributes the Play Console flags on Android 15 and up, where they no longer
  do anything. The launch backdrop now picks its status-bar icons from the
  light/dark resource instead, so they stay legible over it either way
  ([#123]).

## [2.18.1] — 2026-08-06

### Fixed
- Tapping an empty slot in week or day view creates the event at the hour you
  tapped, after the timeline has been zoomed. Pinching the day taller or shorter
  — or changing **Hour height** in Settings — left the tap still being measured
  against the old spacing, so the new event landed at some other hour entirely
  ([#148]).

## [2.18.0] — 2026-07-31

### Added
- A new event no longer always lasts an hour. **Settings → New event form →
  Default duration** sets how long one opens, and each calendar may keep its own
  length underneath — 8 hours for the calendar you keep work shifts in, 30
  minutes for the one you book calls in. A calendar without its own length
  follows the default, switching calendars mid-form re-stretches the event, and
  setting an end time by hand keeps it. An event another app hands over with only
  a start gets the default too; one that names its own end — an `.ics` file, a
  duplicate — keeps that length. All-day events are unaffected ([#54]).
- Week and day view can be set to show more or less of the day at once, under
  Settings → Views → Week & day → **Hour height**. **Fit whole day** sizes an
  hour to your screen so all 24 hours are visible without scrolling — on a tall
  phone the old fixed spacing showed only about half a day, so appointments
  could sit below the fold all week. Compact and Comfortable are fixed steps
  either side of the previous spacing, which stays the default. Both views share
  the setting ([#56]).
- Week and day view can be **pinched** with two fingers to set the hour height
  directly, anywhere between and beyond the named steps. The time under your
  fingers stays put as it zooms, so you keep your place in the day. A pinched
  height is remembered and appears as **Custom** in the Hour height setting, so
  tapping a named step there takes you back to it ([#56]).

### Changed
- The date in the top bar is now the way to jump: tapping the month, week or day
  title opens the same date picker the sidebar's **Jump to date** offers, seeded
  on whatever the bar is naming. A drop-down caret marks it as tappable. The
  sidebar entry stays where it is ([#57]).
- A reminder in the status bar is now Calendula's own mark — the calendar with
  the bloom — instead of the generic calendar glyph it shared with the system's
  date surfaces and every other calendar app. The status bar draws that icon as
  a plain silhouette, so its shape is the only thing that could tell them apart
  ([#83]).
- Event blocks now only draw text they can draw whole. One too short for a full
  line shows no title rather than a sliced one, a block that cannot fit both its
  title and its time keeps the title, and a block too narrow to hold more than a
  syllable stays on one ellipsised line instead of stacking letters down the
  block. Tapping and the spoken description are unchanged ([#56]).
- Calendar colours are reworked so text on an event is always readable. They
  were shaped by a brightness setting that does not match what the eye sees, so
  whether a block got dark or light text depended on which hue you happened to
  pick — an orange calendar took dark text while a red one beside it took light
  — and colours landing in between were hard to read either way. Each colour now
  keeps its hue and is moved clear of that middle: most become deep blocks with
  light text, while naturally pale colours such as yellow stay pale and take
  dark text, rather than being forced into a muddy brown. Dots, stripes and
  icons are tuned separately from blocks, so they stay visible against the
  background instead of sharing a colour meant to sit behind text. The setting
  is now called **Harmonise calendar colours**; turning it off still shows the
  raw colours from your calendar source ([#21], [#36]).

### Fixed
- The back gesture closes the sidebar instead of the app. With the drawer open,
  swiping back left Calendula altogether rather than putting the sidebar away
  ([#114]).
- The sidebar lines up. "Calendula", "View" and "Calendars" now share the left
  edge of the rows under them, and the view, jump-to-date, Settings and calendar
  rows sit on one vertical axis instead of each icon finding its own — the same
  alignment the Settings screens use ([#114]).
- The status- and navigation-bar icons follow Calendula's own light/dark choice.
  Setting the app dark while the system stayed light — or the other way round —
  left the clock and battery drawn for the system's theme, so they could sit
  near-invisible against the app's own bar ([#70]).

## [2.17.1] — 2026-07-30

### Added
- Settings → Calendars now says what is different about a calendar instead of
  leaving you to guess. Ones you can only view — a subscribed calendar, a
  calendar shared with you read-only — are marked **Read-only** ([#76]).
- Calendars your device isn't syncing are marked **Not synced**, moved to the
  bottom of their account and left without a switch. None of their events are on
  the device, so the switch they used to have could not have shown you anything
  — the calendar simply looked broken. They are no longer offered when you pick
  a calendar for a new or an imported event either: an event saved there would
  never reach the account. Whether an account syncs a calendar stays that
  account's own app's decision ([#78]).
- The birthday and anniversary calendars Calendula fills from your contacts are
  marked **Filled from your contacts**, which is why they can't be picked for a
  new event: anything you put there would be removed again on the next sync.
  Deleting one is held back while special dates are switched on — Calendula
  would simply create it again — and the calendar's editor says so; turn the
  feature off under Settings → Special dates and the delete works as usual
  ([#76]).
- The calendar picker in the event form and in the .ics import screen now ends
  with a **"Missing a calendar?"** row that opens Settings → Calendars, where
  those marks then explain why a calendar isn't offered ([#76]).
- The agenda widget's text size is yours to set. **Settings → Widgets & tiles →
  Agenda widget size** offers Small, Medium, Large and Extra large, replacing the guess
  the widget used to make from its own measurements. Small is what it looks like
  today, so nothing changes until you turn it up ([#51]).
- A repeating event now shows you its next few dates, not just a description of
  the rule. Both the preset list and the custom recurrence picker carry a
  **Next:** line — "Next: 30 Jul, 6 Aug, 13 Aug" — under the rule they would
  save. A phrase like "monthly" on the 31st, or "every 2 weeks on Mon & Fri",
  can mean something other than it sounds like, and only the dates say so. A
  rule that can never fire says that instead ([#69]).
- The event visibility options now say who they affect. **Public**, **Private**
  and **Confidential** each carry a line about what other people on a shared
  calendar see — the part the four words on their own leave out ([#69]).

### Changed
- **Settings has been reorganised so each setting sits where you would look for
  it.** One long undifferentiated list is now three labelled groups — Look &
  behaviour, Data, and App — whose rows open sub-screens and say what they do
  rather than only naming themselves. Appearance, Views, New event form,
  Notifications and the new Widgets & tiles are separate screens now, so the
  settings for a calendar view are no longer mixed in with the ones for the
  app's colours or for the home-screen widgets ([#69]).
- Settings that are hard to picture from their name now show you what they do.
  The week-start picker rearranges a real month grid as you choose, the
  past-events setting previews a sample agenda day for Show, Dim and Hide, the
  font pickers set a specimen line in the face you are choosing, and the Agenda
  range options carry the dates each one actually covers. Options that follow
  the system additionally name which way they currently fall ([#69]).
- **Backup & restore** is now its own Settings entry instead of living inside
  the calendar manager, where it was easy to miss — keeping a copy of your
  calendars is a different question from which calendars you have. The calendar
  manager keeps a row pointing to it, and nothing about how backup or automatic
  backup works has changed ([#69]).
- Calendula's source code now lives on **Codeberg**, where its issues already
  were. The **Source code** and **License** links in Settings → About point
  there, so reporting a bug and reading the code no longer land on two different
  sites. Nothing about the app itself changes, and the F-Droid repository is
  unaffected.

### Fixed
- A month-grid widget stays a month grid, and draws all seven days again. Since
  2.16.0 a placed month widget could redraw itself as the agenda widget a little
  after any change to your events, and could draw only about four day columns
  with the last one cut off part-way through. Both came from the release build
  merging the two widgets into a single class, so Android could no longer tell
  which of them a widget on your home screen was — and the month grid was handed
  the wrong widget's measurements to lay its columns out against ([#89], [#103]).
- The back gesture on **Settings → Views** returns to Settings instead of
  leaving Settings altogether and dropping you on the calendar. Special dates
  did the same ([#81]).
- The dots standing in for the events that didn't fit a day in the month view
  now dim with everything else when **Dim completed events** is on. A past day
  with four or more events kept its last events at full strength while the rest
  faded ([#79]).
- Two accounts that happen to share a name — a Google account and a DAVx5
  account for the same address, say — are no longer merged into one group.
  They were listed together in Settings → Calendars and in the drawer's filter,
  which also meant the group's source icon and its "manage in app" button could
  send you to the wrong app, "toggle all" spanned both accounts at once, and
  collapsing one collapsed the other. Where a name really is shared, each group
  now names the app it comes from ([#77]).
- In the month view's **Split** style, the new-event button now starts on the
  day you have selected. It always started on today, whichever day was selected
  and listed below the grid ([#87]).
- Search results now show an all-day event's real date. West of UTC — anywhere in
  the Americas, say — a search hit was dated one day early, disagreeing with the
  day the month, week and agenda views file the same event under ([#82]).
- Reminders no longer depend on Android telling Calendula when they are due.
  Calendula now works out each reminder's time itself and sets its own alarm for
  it. On some phones — Samsung's among them — the system's calendar storage never
  sends the signal a calendar app is meant to wake up on, and no amount of
  battery or notification settings helps: the reminder is simply never announced.
  None of that is visible from inside an app that waits to be told, which is why
  it took a second pass to find ([#75]).

  Reminders also survive things that used to lose them quietly. After a restart
  or an app update Calendula re-arms its alarms, and a reminder whose moment
  passed while the phone was off still arrives, as long as the event has not
  ended yet.

- All-day reminders now arrive at the time you chose in **Settings →
  Notifications**, on every occurrence. A yearly birthday could drift an hour
  either way depending on daylight saving, and all-day reminders on calendars
  from an account fired in the middle of the night instead of in the morning
  ([#75]).

- Reminders now arrive for every calendar you have switched on. A calendar that
  was hidden at system level — switched off in another calendar app, or never
  switched on after being added — still showed its events and listed their
  reminders in Calendula, but never notified: Android only schedules reminder
  alarms for calendars marked visible, and Calendula kept its own separate
  on/off list that had no say in it. There is now one switch: **Settings →
  Calendars** turns a calendar on or off for the whole device, so what you see
  and what reminds you can no longer disagree ([#75]).

  Calendars you had switched off in Calendula are switched off here too on first
  launch. Calendars that were already off — hidden in another calendar app, or
  never switched on after being added — stay off, and Calendula says so once
  rather than quietly switching them on for every app on your device; you can
  turn any of them back on in Settings → Calendars.

  If you gave Calendula read-only access to your calendars, the switch still
  works: your choice is kept in the app until it can be written.

  The drawer's filter is unchanged and still app-only: hiding a calendar there
  tidies your view without silencing its reminders.

## [2.16.0] — 2026-07-24

### Added
- Choose how the month view is laid out. A new **Month view style** setting
  (Settings → Views) offers three ways to read a month, each shown with a
  preview of the layout it produces:
  - **Pages** — what you have today: one month at a time, swiped sideways.
  - **Continuous** — scroll up and down through the weeks without a break
    between months. Because the weeks run on unbroken, no month is cut off and
    no day appears twice, where paging repeats a boundary week at the end of one
    month and the start of the next. The 1st of each month names itself so you
    always know where you are, and the title bar keeps up as you scroll ([#38]).
  - **Split** — a compact grid showing coloured dots for the days that have
    something on them, with the day you tap listed in full underneath. Tap the
    date above the list to open the whole day ([#53]).

  The Agenda view is untouched by this and stays available in all three styles —
  the split layout lists a single day, while Agenda remains a rolling multi-day
  window with its own range settings.
- Give an event its own time zone. A new **Time zone** field (under "more
  fields" in the event form) pins an event to a specific zone, so a call set for
  8:00 AM in New York stays 8:00 AM in New York wherever you open it — and keeps
  tracking that zone across daylight-saving changes instead of drifting an hour.
  The form edits the event in its own zone and shows the local equivalent under
  the times ("2:00 PM – 3:00 PM your time"); the event's details keep your local
  time first and note the original beneath it, so both are always clear. Pick a
  zone from a full-screen picker with your device zone and recent choices on top,
  searching by city ("new york"), IANA id ("europe/berlin"), or abbreviation
  ("CEST") to gather every matching zone at once. All-day events stay
  date-anchored and carry no zone, as before ([#31]).
- Put the "jump to today" button in the toolbar. A new **Today button in
  toolbar** setting (Settings → Appearance, off by default) swaps the floating
  button that fades into the corner while you're away from today for a permanent
  today icon in the top bar — always there, on today or not, matching the
  familiar calendar-app pattern. Leave it off to keep the floating button as
  before ([#60]).
- Choose what Calendula calls itself on your home screen. A new **App name**
  setting (Settings → Appearance) switches the launcher label between
  **Calendula** and **Calendar**, for launchers that can't rename apps
  themselves. Pick from a full-screen chooser that previews both names as
  launcher marks; the change applies at once ([#44]).
- Calendula now speaks **Arabic**, laid out right-to-left, and its French and
  Italian translations have been brought up to date — thanks to the community
  translators on Weblate. Pick a language under Settings → Language (or leave it
  on the system default).

### Changed
- Dates in the Month, Week and Day title bars now follow your language and
  region instead of one hardcoded layout. Every date was rendered in a fixed
  German-style order with a trailing dot on the day number, whatever your
  settings: US English showed "Fri, 17. Jul 2026" where it should read
  "Fri, Jul 17". The Agenda view already formatted correctly, so the two
  disagreed about the same date. All four views now share one formatter, and the
  day/month order, the separators and the ordinal all come from your locale —
  so English-in-Germany reads "Fri, 17 Jul" and English-in-the-US "Fri, Jul 17",
  each correct for where you are ([#60]).
- The title bar drops the year while you're in the current one — "July" rather
  than "July 2026". The year reappears the moment you page out of the current
  year, which is when it tells you something you didn't already know.
- The Week view's title now names the month instead of spelling out the day range.
  "24. Jun – 31. Jun" restated the day numbers already printed in the column
  headers right below it, in the widest string in the bar. A week that straddles
  two months keeps the outgoing month until it is fully gone ([#60]).
- The custom recurrence picker has been redesigned and tightened up. As you
  build a rule — "every 2 weeks on Mon & Wed, until a date" — the live summary
  now describes exactly what will be saved rather than a near-copy that could
  drift from it, the amount fields accept being left blank (reading as their
  shown default instead of greying out OK), and the read-out no longer jumps
  around as you tap weekdays ([#42]).

### Fixed
- An all-day event no longer shows up again the day after it happened. In time
  zones east of UTC, an all-day event — a birthday, say — set for one day also
  appeared under the *next* day's heading in the Agenda (and the agenda widget),
  because all-day events are anchored to UTC midnight and the following day's
  window reached back across that boundary and pulled the event forward onto
  "today". Each all-day event now lists only on the day it actually falls on
  ([#65]).
- A multi-day event now shows under every day it spans in the Agenda, not just
  its first day, so a trip or a multi-day booking appears on each day it covers.
- The "Upcoming" agenda widget now scales its text and rows to the size you give
  it. Previously it was laid out once for the smallest size and simply stretched
  when enlarged, so the text stayed small no matter how big you made the widget.
  Now a bigger widget gets bigger, more readable type and roomier rows, while the
  default size looks exactly as before — no new setting; it follows the size you
  already chose ([#51]).
- Calendula now appears under other apps' "Add to calendar" / "Save to calendar"
  actions. Some apps (e.g. DB Navigator) fire the widely-used "insert event"
  intent with the singular `vnd.android.cursor.item/event` type, which Calendula
  didn't advertise — so it was left out of the chooser, and if it was your only
  calendar app the save silently did nothing. It now accepts that form, plus the
  `INSERT_OR_EDIT` action, and opens the new event prefilled for review ([#74]).
- Opening a `.ics`/`.vcs` file now works even when another app hands it over
  mislabelled as a generic download (`application/octet-stream`), as some mail
  clients, browsers and file managers do — Calendula recognises it by its file
  extension instead of relying on the declared type ([#74]).
- A recurrence end date no longer lands a day late. West of UTC, setting a rule
  to end "until" a given day could save and show the day after the one picked;
  the end date now reads back as chosen ([#42]).
- The status- and navigation-bar icons stay legible over full-screen pickers in
  dark theme. They could render dark-on-dark — a near-invisible black clock
  against the dark picker — instead of switching to light ([#70]).

## [2.15.0] — 2026-07-15

### Added
- Show raw calendar colours. Calendula normally softens each calendar and event
  colour toward a theme-fitting pastel so harsh sync colours read well on both
  light and dark; a new **Soften calendar colours** setting (Settings → Design,
  on by default) lets you turn that off and paint the exact colours your calendar
  source publishes — matching DAVx5/CalDAV and other calendar apps. Thanks to
  @leonp5 for the report ([#36]).
- Readable titles on dark event colours. An event bar's title now shows in white
  on a dark colour and near-black on a light one, chosen automatically from the
  colour's brightness, so a deep blue or purple event is legible at a glance in
  the busy Week and Month views instead of dark-on-dark. This applies whether or
  not colours are softened. Thanks to @ptab for the suggestion ([#21]).
- A custom snooze duration. The **Snooze duration** setting (Settings →
  Notifications) gains a **Custom…** option next to the minute presets: pick any
  amount and switch between minutes and hours, so a snoozed reminder comes back
  after exactly the delay you want instead of only a preset one ([#40]).
- Move an event to another calendar. When editing an existing event, the
  calendar row is now tappable — pick a different calendar and saving moves the
  event across, instead of having to delete it and recreate it elsewhere.
  Recurring series move as a whole, keeping their individually-edited and
  cancelled occurrences, and any reminders and guests come along too. A calendar
  can't simply be reassigned underneath an event, so Calendula recreates it on
  the target and removes the original — the same approach other calendar apps
  take. Thanks to @prismplex for the suggestion ([#39]).
- Open an event straight into the edit form from another app. Calendula already
  answered the "new event" and "open this event" hand-offs from other apps and
  widgets; it now also answers the "edit this event" one, so an assistant, task
  app, or widget can send an existing event to Calendula and land on its edit
  screen rather than the read-only details. A hand-off with no event attached
  opens the same prefilled create form as "new event". Calendula also recognises
  a couple more file labels the same calendar data arrives under (`.vcs`
  vCalendar files and the `application/ics` type), so opening or sharing those
  into Calendula works too.
- Keep today at the top of the agenda. A new **Always show today** setting
  (Settings → Agenda, on by default) anchors today as the first entry in both the
  Agenda screen and its home-screen widget even once nothing is left today —
  under today's header a "No more events today" note appears — so the first
  events you see are clearly today's rather than a future day's. Turn it off to
  keep the agenda purely upcoming. Thanks to @ptab for the suggestion ([#35]).
- Duplicate an event. The event details now carry a **Duplicate** action that
  opens the editor pre-filled with a copy of the event as a new, unsaved one, so
  a one-off like a shift or an appointment can be recreated by just changing the
  day and time instead of re-typing every field. The copy keeps the original's
  time, and its title, location, notes, colour, guests and reminders come along;
  it's saved as its own single event (any repeat is left off — add one in the
  editor if you want it). Duplicate works from read-only calendars too, dropping
  the copy into a writable one. Thanks to @internet-rando for the suggestion
  ([#52]).
- French and Polish, in early form. Calendula has started speaking French and
  Polish, both contributed as community translations through
  [Calendula's Weblate](https://weblate.dev.jeanlucmakiola.de/projects/calendula/).
  They are partway there, so untranslated parts still show in English until they
  fill out — you can already pick either under Settings → Language or in Android's
  per-app language settings. Thanks to Thomas Tref (French) and Bazyli Cyran
  (Polish) for getting them started; help finishing them is very welcome.

### Fixed
- Reminders for events on another day no longer read as if they were today. A
  reminder fired ahead of time — say, the day before — used to show only the
  event's time, making it look like it was happening now. The notification now
  says which day: **Tomorrow** or **Yesterday**, the weekday for another day this
  week, or the date for anything further out. Thanks to @moonj for the report
  ([#46]).
- Agenda dates now read in your locale's format. The agenda's range bar and its
  day headers used a fixed day-month-year layout — and the range span even mixed
  two orders (e.g. "15 Jul – Aug 13, 2026") — instead of following your language's
  conventions. Dates across the agenda and its widget now use your locale's own
  field order, matching the rest of the app. The range bar also no longer repeats
  the range's name from the selector button beside it, showing just the dates.
- Calendar gutters line up with the menu button. The Month view's week-number
  column, and the Week and Day views' hour labels, sat a few pixels left of the
  hamburger menu above them; they now line up with it. In Month view the day
  cells also sit squarely under their weekday letters.

## [2.14.1] — 2026-07-13

### Fixed
- Deleting one occurrence of a repeating event no longer breaks the series.
  Choosing "This event" when deleting an occurrence of a recurring event could
  wipe out every *other* occurrence while leaving the one you deleted behind as
  a stale, still-tappable ghost — and deleting it again brought the series back.
  A single-occurrence delete now removes exactly that occurrence and leaves the
  rest of the series untouched, and the deleted occurrence disappears from the
  grid straight away. This holds on every kind of calendar, including the
  on-device ones Calendula keeps for contact birthdays and anniversaries, where
  the series is a yearly repeat. Thanks to @moonj for the report ([#47]).
- Tapping an event in a third-party widget opens it in Calendula. v2.13.1 taught
  Calendula to answer the "new event" hand-off from other apps and widgets; now
  it also answers the "open this event" one, so tapping an existing event in a
  widget such as Todo Agenda offers Calendula and lands on that event's details.
  Thanks to @bushrang3r for the report ([#48]).
- Events created from other apps get your default reminder. An event handed over
  by another app or widget — Google Maps' "add to calendar", the Todo Agenda
  widget's "+" — opened with no reminder at all, ignoring the default set in
  Settings. It now starts with your default reminder, the same as an event you
  create in Calendula. An event opened from an `.ics` file is treated differently,
  because the file has its own say: Calendula keeps whatever reminders it carries
  (including none at all) and asks you once whether to apply your default instead
  — it never quietly overrides the file. If you have no default set, it doesn't
  ask ([#49]).
- A tidy colour picker on CalDAV calendars. For calendars synced by a CalDAV
  app (such as DAVx5), the event colour picker showed every colour the account
  publishes — nearly 150 swatches in alphabetical order, many of them
  duplicates or near-identical shades. The picker now shows only visually
  distinct colours, arranged as a rainbow; near-duplicate shades and the
  washed-out neutrals are folded away so no two swatches look alike. Picked
  colours still sync exactly as before, and calendars with hand-picked
  palettes (like Google's) are unaffected. Thanks to @ptab for the report
  ([#22]).

## [2.14.0] — 2026-07-06

### Added
- Restore events from a backup file. The backup section of Settings can now read
  events back **in** from an `.ics` file, not just write one out: pick a file,
  choose which calendar to import into, and Calendula adds the events — skipping
  any that are already there and telling you how many it skipped. Export gained a
  per-calendar selector at the same time, so you can back up just the calendars
  you pick instead of everything at once ([#32]).
- Week numbers in Month view. A new **Week numbers** setting (off by default)
  adds a slim gutter down the left of the Month grid showing the calendar-week
  number for each row, sized to match the day cells. Handy if you plan or refer
  to dates by week number ([#25]).
- Tap a date header to open that day. In Week and Agenda view, tapping a date
  header now opens that date in Day view — the same drill-in that Month view and
  the agenda widget already offered, so every view behaves the same way. It makes
  jumping to a specific day quicker: switch to Week, swipe to the week you want,
  then tap the date to open it. Thanks to @ptab for the suggestion ([#37]).
- An early Simplified Chinese translation. Calendula has started speaking
  Simplified Chinese, contributed as a community translation through
  [Calendula's Weblate](https://weblate.dev.jeanlucmakiola.de/projects/calendula/).
  It is still an early effort, so many parts of the app show in English until it
  fills out — you can already pick it under Settings → Language or in Android's
  per-app language settings. Thanks to
  [zh-cn](https://weblate.dev.jeanlucmakiola.de/user/zh-cn/) for getting it
  started; help finishing it is very welcome.

### Changed
- Long event titles wrap in the edit screen. When editing an event, a long title
  now wraps onto multiple lines instead of being clipped to a single line, so you
  can see and edit the whole thing ([#33]).

## [2.13.1] — 2026-07-06

### Added
- Create events from other apps and widgets. Calendula now registers the
  standard "insert event" intent (`ACTION_INSERT` on the calendar events type),
  so other apps and home-screen widgets — such as the Todo Agenda widget — can
  hand off to Calendula to create a new event. It opens the new-event form
  prefilled with whatever they passed (title, start/end time, all-day, location,
  description, recurrence), and picks your last-used or first writable calendar.
  Thanks to @dschuermann for the suggestion ([#30]).

### Fixed
- Some recurring events could not be opened. Events in a series that started
  before 1970 — for example yearly birthdays or anniversaries synced over CalDAV
  — showed "Something went wrong" instead of opening, because their stored start
  time is a negative value that was wrongly treated as invalid. They now open
  normally and appear in search again. A related case (an event whose stored end
  precedes its start) is now kept and openable instead of failing the same way.
  Thanks to @dschuermann for the report ([#34]).
- The time picker now follows your 24-hour setting. With Calendula set to
  24-hour time, the clock dial for choosing an event's start and end time still
  showed AM/PM instead of a 24-hour dial; it now matches your setting (and the
  same fix applies to the all-day reminder time in Settings). Thanks to
  @abrossimow for the report ([#27]).

## [2.13.0] — 2026-07-03

### Added
- Calendula now speaks Spanish and Italian. Both arrived as community
  translations through [Calendula's Weblate](https://weblate.dev.jeanlucmakiola.de/projects/calendula/)
  — a **huge thank you** to
  [kikerw](https://weblate.dev.jeanlucmakiola.de/user/kikerw/) for the Spanish
  translation and to
  [corrent](https://weblate.dev.jeanlucmakiola.de/user/corrent/) for the Italian
  one! Pick your language under Settings → Language or in Android's per-app
  language settings. Strings added in this very release may still show in
  English until the translations catch up. Want Calendula in your language?
  Translating happens entirely in the browser — every contribution is welcome.
- See your contacts' birthdays and anniversaries in your calendar. A new,
  **optional** feature (Settings → Contact special dates) mirrors your contacts'
  birthdays, anniversaries and other dates into local "Birthdays",
  "Anniversaries" and "Other dates" calendars that stay in sync as your contacts
  change. Each is a normal local calendar, so you set its colour, visibility and
  reminders the usual way — new birthdays even start with a reminder a week before
  *and* on the day. The title format is yours to customise (`{name}`, `{year}`).
  This is the first feature to use the contacts permission: it is requested only
  when you turn the feature on, everything stays on your device (Calendula has no
  internet access), and your contacts are only ever read, never changed. Thanks
  to @moonj for the suggestion ([#15]).
- Set more than one default reminder per calendar. A calendar's default
  reminders — and the global defaults under Settings → Notifications — can now
  hold several lead times instead of just one, so new events can start with, say,
  a reminder a week before *and* one on the day. The reminder pickers are now
  multi-select; per-calendar overrides can still inherit the global default or
  turn reminders off entirely. Thanks to @moonj for the suggestion ([#14]).
- Widget headers now open the app. Tapping the month/year title on the month
  widget opens the app on the month view, and tapping the "Upcoming" title on
  the agenda widget opens it on your default view — so there's a one-tap way
  back into the app that lands where you'd expect, instead of only through a day
  or event. On the month widget, tapping anywhere on a day — not just the small
  date number — now opens that day, and the "today" button snaps the grid back
  to the current month in place. Thanks to @rgz46vic and @ptab for the
  suggestions ([#18], [#20]).
- Make Calendula's text your own. Settings → Appearance gains **Headings font**
  and **Body font** pickers: keep the system default, choose a bundled face
  (Atkinson Hyperlegible, Lora, JetBrains Mono — each previewed in its own
  face), or load any `.ttf`/`.otf` file from your device, independently per
  role. Font size and colour stay with Android's accessibility scaling and the
  app theme, as discussed on the issue. Thanks to @abrossimow for the
  suggestion ([#19]).
- Choose what the view-switch button cycles through. Settings → Appearance now
  lets you pick which views the top-right quick-switch button rotates between
  and drag them into your preferred order; views you switch off stay reachable
  from the navigation menu, which can be reordered the same way. Thanks to
  @abrossimow for the suggestion ([#24]).

### Fixed
- Month widget arrows and "today" button work again. On release builds the
  prev/next-month arrows and the jump-to-today control on the month widget did
  nothing when tapped — code shrinking had stripped the tap handlers behind
  them. They respond again. Thanks to @rgz46vic for the report ([#18]).
- Disabled calendars no longer notify. Reminders for events in a calendar you
  have disabled (Settings → Calendars) are now suppressed instead of still
  popping up — matching how a disabled calendar's events already stay hidden
  everywhere else in the app ([#17]).
- Editing a single occurrence of a recurring event works again. Choosing **Only
  this event** and saving a change to one event in a repeating series silently
  did nothing — the change was rejected and the edit form simply reappeared with
  nothing applied. The edited occurrence is now stored correctly, so the change
  lands on just that one event and leaves the rest of the series untouched
  ([#16]).
- Every calendar can be picked when creating an event. The event editor's
  calendar picker was a fixed-height dialog, so with many calendars anything
  past the first nine or so was simply unreachable. It is now a full-screen,
  scrollable list grouped by account, with each calendar's colour shown.
  Thanks to @dschuermann for the report ([#29]).

## [2.12.0] — 2026-06-28

### Added
- See past events your way. Two new settings change how events that have already
  ended are shown. **Past events** (Settings → Appearance → Agenda) lets the
  agenda — both in the app and the home-screen "Upcoming" widget — keep them as
  usual, dim them, or hide them from the list entirely. **Dim completed events**
  (Settings → Appearance) separately fades finished events in the month and week
  views. Both are off by default, an event only counts as finished once it has
  actually ended (events still in progress are never dimmed), and the lists
  update on their own as the day goes on. Thanks to @ptab for the suggestion
  ([#12]).

### Changed
- Readable titles on overlapping events. In the week and day views, events that
  overlap split a day into slim columns where the title used to be clipped to a
  character or two. The title now wraps across as many lines as the block can
  fit, and the time is hidden on those narrow blocks so the name can fill the
  space — so you can tell events apart without opening each one. Thanks to @ptab
  for the suggestion ([#13]).

## [2.11.2] — 2026-06-28

### Added
- Jump straight to typing a title. When you start a new event, Calendula now puts
  the cursor in the title field and opens the keyboard right away, so you can type
  the name without an extra tap. It's on by default and only affects creating an
  event — editing never grabs focus — and a new **Focus title on new event**
  switch (Settings → New event form) turns it off. Thanks to @abrossimow for the
  suggestion ([#10]).

## [2.11.1] — 2026-06-28

### Fixed
- Calendula can now be set as your default calendar app. It registers the
  calendar-app intent filters the system uses, so it appears in the chooser when
  you tap a date in a launcher or clock — and opening one takes you straight to
  that day. Android has no way for an app to make itself the default, so you pick
  it once from the system picker. Thanks to @abrossimow for the report ([#9]).

## [2.11.0] — 2026-06-27

### Added
- Start the week on any day. The **Week starts on** setting (Settings →
  Appearance) now offers every weekday — not just Monday or Sunday — alongside
  the automatic, locale-based default. Thanks to @zmaherdev for the suggestion
  ([#3]).
- Choose a 12- or 24-hour clock. A new **Time format** setting (Settings →
  Appearance) lets you force a 12-hour (2:00 PM) or 24-hour (14:00) clock, or
  follow the system setting automatically. It applies everywhere times appear —
  the week and day timelines, agenda, event details, search and reminders — so
  the format is now consistent across the whole app. Thanks to @zmaherdev for
  the suggestion ([#6]).
- Optional hour lines in the timeline. A new **Hour lines** switch (Settings →
  Appearance) draws a faint separator at each hour in the week and day views,
  making it easier to see when events start and end. Off by default. Thanks to
  @zmaherdev for the suggestion ([#5]).
- Limit how far ahead the agenda looks, and switch it on the fly. New **Agenda
  range** and **Agenda widget range** settings (Settings → Appearance) let the
  agenda screen and its home-screen widget each show just today, the rest of this
  week, the rest of this month, a rolling 7 or 30 days, or a custom number of
  days — "This week" follows your week-start. The agenda also gains a bar at the
  top naming the exact dates in view, with a button to switch the range just for
  the current session (it resets when you reopen the app); the bar can be turned
  off. Thanks to @zmaherdev for the suggestion ([#4]).
- Automatic backup of local calendars. A new **Automatic backup** option
  (Settings → Calendars → Backup) periodically exports your local calendars to an
  `.ics` file in a folder you choose, on an interval you set (from 30 minutes up).
  Useful for file-based syncing such as Syncthing, or simply as a safety net — it
  is a one-way export and never touches your synced accounts. Thanks to @shield
  for the discussion that inspired it ([#7], [#8]).
- Field icons in the event-form settings. Each optional-field toggle (Settings →
  Event form) now shows the same icon the field uses in the new-event form, so
  the list is easier to scan.
- Help translate Calendula. A **Help translate** link at the top of **Settings →
  App language** opens the project's Weblate, where you can add or improve a
  language in your browser — no coding needed. Contributions in any language are
  welcome.

### Changed
- Tidied **Settings → Appearance** into clearer groups (theme & colour, calendar,
  agenda), and the agenda's empty state now reads "You're all caught up".
- Reworked **Settings → Calendars**. Local and synced calendars are now grouped
  into collapsible, source-branded cards — each account shows its app's icon —
  with a per-account menu to enable or disable all of its calendars at once or
  open the account in its source app.
- Reorganised **Settings → Notifications**: reliable-delivery and snooze settings
  moved up with the other global reminder options, and the per-calendar reminder
  overrides now fold into a single expandable section.
- Moved **Add Quick Settings tile** out of the event-form section to its own
  top-level entry in Settings.

## [2.10.0] — 2026-06-25

### Added
- Turn a calendar off across the whole app. Settings → Calendars now has a
  switch on every calendar — both your own and synced ones. Switching one off
  removes it everywhere: its events disappear from all views and search, and it
  drops out of the drawer's hide/show filter, the event-form calendar picker and
  the import target picker. Unlike hiding (the quick per-view checkbox in the
  drawer), a disabled calendar leaves the app entirely until you turn it back on.
  Nothing is deleted and no other app is affected — it's a Calendula-only view
  choice — so you can re-enable it any time from the same screen, where disabled
  calendars stay listed but dimmed.
- An optional way to support development. Settings → About now has a "Support
  development" button that opens Ko-fi in your browser. It's a plain donation
  link with no perks attached, and it needs no new permissions — Calendula still
  has no internet access of its own and just hands the link to your browser.

## [2.9.0] — 2026-06-25

### Added
- Choose the view Calendula opens on. A new **Default view** setting (Settings →
  Appearance) lets you pick Month, Week, Day or Agenda as the view shown each
  time you start the app, instead of always opening on Week. Thanks to
  @devinside for the suggestion ([#1]).
- The month widget is now interactive: tap any day to open it, or tap an event to
  open its details. Previously only the month's prev/next/today controls
  responded. Thanks to @devinside for spotting this ([#2]).
- Predictive back. Swiping back from a screen — an event, the editor, search or
  settings — now follows your finger, shrinking the screen to preview what's
  behind so you can see where Back will take you before you let go; slide it back
  to cancel. The preview needs Android 14 or newer; on older versions Back works
  as before.
- Respect for "Remove animations". If you've turned animations off in your
  device's Accessibility settings, Calendula now honours it everywhere: motion
  collapses to a quick fade and the back preview is skipped.

### Changed
- Smoother, more consistent motion throughout. Expanding sections, list updates
  in search and agenda, and the onboarding screens now animate the same way
  across the app, instead of some places sliding or growing while others popped
  in. Switching between Month, Week, Day and Agenda now cross-fades rather than
  snapping, while paging within a view keeps its slide.

### Fixed
- Home-screen widgets now back out the way you came in. A day or event opened
  from the Agenda widget keeps you in the agenda context, and from the Month
  widget in the month context — pressing Back returns there instead of dropping
  you on the week view. More broadly, the app now keeps a real view history:
  switching views and drilling into a day are retraced by Back one step at a
  time, down to your default view before the app exits. Thanks to @devinside for
  reporting ([#2]).

## [2.8.0] — 2026-06-23

### Added
- Find events fast. A search button in the top bar of every calendar view opens
  a search box — type a couple of letters and matching events (by title,
  location or description) appear, soonest first with past events below. Tap a
  result to open it. Search covers your whole calendar, not just what's on
  screen, and skips calendars you've hidden. A recurring event shows its next
  occurrence rather than the date the series first started.
- A current-time line in the day and week views. A thin coloured line marks the
  present moment across today's column, so you can see at a glance where you are
  in the day. It updates every minute and only appears when today is in view.
- Add and remove event guests. The create/edit form now has a Guests section:
  add people by email or pick them from your contacts, mark each as required or
  optional, and remove them. Calendula never sends invitations itself (it has no
  internet access) — it only records the guests; if the event lives on a synced
  account, that account may email them when it syncs, and on a local calendar no
  one is notified. The form tells you which applies. Picking a guest from
  contacts needs no contacts permission.
- Pick a location from your contacts. A contacts button beside the location
  field drops a contact's address straight into an event — handy for a meeting
  at someone's home or office. Like the guest picker, it needs no contacts
  permission.
- A "New event" Quick Settings tile. Add it to your quick settings to jump
  straight into the new-event form from anywhere. Settings → New event has a
  one-tap button to add the tile (Android 13+); on older versions you can add it
  from the quick-settings editor.
- Snooze and dismiss buttons on reminder notifications. Dismiss clears the
  reminder; snooze hides it and brings it back after a delay you pick in
  Settings → Notifications (5 to 60 minutes, default 10). Android's calendar
  system won't re-post a reminder on its own, so Calendula schedules an exact
  alarm to bring a snoozed one back on time.

### Changed
- Event details now show each guest's email beneath their name, instead of only
  when no name is available.
- Crash and problem reports now open on the project's public Codeberg tracker,
  where anyone can register and file an issue. Nothing is sent automatically —
  you still review the report and submit it yourself in the browser.

## [2.7.5] — 2026-06-21

### Changed
- Further build cleanup for the official F-Droid repository: stopped embedding
  AGP's dependency-metadata block in the APK, which F-Droid's reproducible-build
  scanner rejects as an extra signing block. No functional or visible changes —
  the same app as 2.7.4, just without that Play-oriented metadata blob.

## [2.7.4] — 2026-06-21

### Changed
- Build cleanup that lets Calendula ship in the official F-Droid repository:
  removed an unused Gradle toolchain-resolver plugin, which F-Droid's offline,
  reproducible build process disallows. No functional or visible changes — this
  is the same app as 2.7.3.

## [2.7.3] — 2026-06-21

### Fixed
- Home-screen widgets no longer get stuck on a loading spinner in the published
  (F-Droid) release build. They render via Android's background-work system, and
  release optimisation (R8) was stripping a helper class it loads by name, so the
  render job never ran. Added the missing keep rule — widgets now load normally.

## [2.7.2] — 2026-06-21

### Added
- Crash reporting you control. If Calendula closes unexpectedly, it now captures
  a technical report and, on the next launch, offers to send it as an issue on
  the project's tracker. Nothing is uploaded automatically — the report stays on
  your device until you choose to share it, it contains no personal data or
  calendar content (only the app, Android and device versions plus the stack
  trace), and you see the full text before sending. There's also a "Report a
  problem" entry in Settings, and if the app ever fails to start repeatedly, a
  minimal recovery screen still lets you send the report.

## [2.7.1] — 2026-06-21

### Fixed
- Fixed the app crashing immediately on launch whenever calendar access hadn't
  been granted yet (a fresh install, or after revoking the permission). The app
  set up its live calendar-change listener before the permission screen could
  appear, which newer Android versions reject outright — so the app died before
  you could grant access. The listener now waits for the permission and attaches
  itself the moment it's granted.

## [2.7.0] — 2026-06-18

### Added
- Share a single event as an `.ics` file from the event detail screen — hands a
  standard calendar file to any app via the system share sheet.
- Back up your local (device-only) calendars: Settings → Calendars → Export as
  `.ics` file writes every event of your on-device calendars to a file you
  choose. Local calendars aren't synced anywhere, so this is their only backup.
- Open or share an `.ics` file into Calendula: a single event opens the create
  form prefilled for review, while a file with many events (e.g. a backup) opens
  a bulk import — pick a calendar and import them all. Re-importing a backup
  won't create duplicates (events are matched by their unique identifier), and
  anything Calendula can't represent (changed recurring occurrences, guest
  lists) is reported rather than silently dropped.

### Fixed
- All-day events that cover a single day (e.g. a birthday) no longer show up on
  the following day as well — in the day, week and month views or on the event
  detail screen. The extra day came from interpreting the all-day date range in
  the device's time zone instead of UTC.
- Fixed the app crashing immediately on every launch in the optimized release
  build: release code-shrinking (R8) was stripping a database class the
  home-screen widget framework needs, so the app died at startup before showing
  anything. Added the missing keep rule.

## [2.6.0] — 2026-06-18

### Added
- App language can now be set from Android's system per-app language settings
  (Android 13+), in addition to the in-app picker in Settings — and the app is
  set up so further languages can be added by community translators

### Fixed
- Changing the app language in Settings now takes effect immediately; the
  picker previously had no effect

## [2.5.0] — 2026-06-17

### Added
- Home-screen widgets (two of them): an "Upcoming" agenda widget — a scrolling
  list of the next month of events grouped under day headers, with refresh and
  "New event" buttons — and a month-grid widget showing the full month with
  today highlighted, connected multi-day event bars, and prev/next/today
  navigation. Both reuse the in-app grouping and layout so they match the app
  exactly, respect your hidden-calendar choices, and refresh automatically when
  the calendar changes or the day rolls over. Tapping a day opens that day;
  tapping an event opens its details
- App shortcut: long-press the Calendula icon for a "New event" action that
  jumps straight into the create-event form
- Agenda view — a fourth top-level view alongside Month/Week/Day: a
  forward-looking list of upcoming events grouped under "Today"/"Tomorrow"/date
  headers, reachable from the view switcher
- Jump to date — a "Jump to date" row in the navigation drawer opens a date
  picker and moves the active view (Month/Week/Day/Agenda) to the chosen day

## [2.4.0] — 2026-06-17

### Added
- Per-event colors: give a single event its own color, instead of always
  inheriting its calendar's. Add the new "Color" field from "More fields" in
  the event form. On calendars that publish their own color set — such as
  Google — you pick from that calendar's palette, so the color is stored
  with the event and shows correctly on every synced device. On local
  calendars you pick from Calendula's palette. "Reset" returns an event to
  its calendar's color
- A new "Allow colors on unsupported calendars" setting (New event form,
  off by default) extends per-event colors to calendars that publish no
  color set of their own (some CalDAV). Such a color is kept on the device
  and may be dropped or overwritten on that calendar's next sync — a
  limitation of those calendars, called out plainly in the setting and on
  the color picker

## [2.3.0] — 2026-06-16

### Changed
- Redesigned Settings around the Material 3 grouped-list pattern: a large
  title that collapses into the toolbar as you scroll, category cards on the
  main screen, and dedicated sub-pages for Appearance, the new-event form, and
  Notifications. The theme, week-start and language pickers now use the app's
  standard option-card dialogs instead of dropdown menus
- About moved to the top of Settings as a card — app icon, author, and quick
  links to the source code and licence — with the version shown plainly at the
  foot of the list
- The Calendars screen now uses the same grouped-card layout and collapsing
  title, and each calendar shows a soft pastel-tinted calendar glyph rather
  than a plain colour swatch
- Redesigned the navigation drawer to match: a branded header, the
  Month / Week / Day switch and your calendars as grouped cards (with the
  active view highlighted), and the whole drawer now scrolls as one

## [2.2.0] — 2026-06-16

### Added
- Tap an empty slot in the day or week view to create an event there: the
  create form opens prefilled with that day and the tapped hour (snapped to
  the hour, one hour long). Tapping an existing event still opens it
- Local calendars: create and manage device-only calendars that live
  entirely on this phone — no account, no sync — from a new "Calendars"
  screen in Settings. Give each a name, a colour, and an optional
  description; rename, recolour, or delete them later. Useful when you want
  a calendar without setting up an account
- The Calendars screen also lists your synced calendars (DAVx5, ICSx5, …)
  grouped by account, each with a "Manage" button that opens the app the
  calendar actually comes from, plus an "Add account" shortcut to the
  system account settings. Calendula never touches a synced calendar's
  server itself — that stays with its own app

### Changed
- Colour swatches in the calendar editor now preview the soft, pastel tone
  a calendar is actually drawn with, instead of a bright raw colour
- The calendar editor reuses the event form's field and button styling for
  a consistent look

## [2.1.0] — 2026-06-15

### Added
- The month view now shows real events in each day instead of coloured
  dots: all-day and multi-day events render as continuous bars at the top
  (a multi-day event is one connected bar across the days it spans, not a
  chip per day), with single-day timed events as filled pills beneath.
  Up to three rows show per day, then a "+N" dot indicator for the rest.
  Each day keeps a rounded surface background, matching the week and day
  views; today is marked with a filled circle on its number
- The slide-out panel now has a "View" section to switch between Month,
  Week, and Day, mirroring the top-bar switcher pill — tapping a view
  selects it and closes the drawer. The current view is highlighted

### Fixed
- Typing in the event title, location, and description fields no longer
  makes the cursor jump around: the form state's round-trip to the UI was
  hopping to a background dispatcher, so the text field saw a lagging value
  while typing. Only the calendar/preferences reads stay off the main
  thread now; the keystroke path is synchronous again

## [2.0.0] — 2026-06-11

### Added
- Conflict handling when saving an edit: if the event changed elsewhere
  (sync, another device) while the form was open, saving now asks whether
  to keep or discard your changes instead of silently overwriting the
  edited fields — and tells you when the event was deleted in the meantime.
  "Keep" still writes only the fields you touched; external changes to
  untouched fields survive either way
- F-Droid store screenshots (German + English), captured with demo data

### Changed
- F-Droid description and README no longer claim the app is read-only —
  they now describe write support and reminder delivery

### Fixed
- `versionName`/`versionCode` bumped to 2.0.0 / 13 — closing out the
  write-support milestone (v1.1 through v2.0)

## [1.4.0] — 2026-06-11

### Added
- Reminder notifications (v1.4): Calendula now delivers event reminders as
  notifications itself — the system schedules them but posts nothing, so a
  calendar app must (essential when Calendula is the only one installed).
  Due reminders appear on a dedicated "Event reminders" channel; tapping one
  opens the event's detail screen. Email reminders are never posted (the
  provider only schedules alert-type reminders)
- A one-time onboarding step after the calendar grant introduces reminders,
  requests the notification permission (Android 13+), and warns that a second
  calendar app with notifications on will duplicate them. "Not now" leaves
  the feature off
- Settings gained a "Notifications" section mirroring the choice: an event-
  reminders toggle (default on) with the duplicate-reminders hint; turning it
  on re-requests the notification permission when missing

### Fixed
- `versionName`/`versionCode` bumped to 1.4.0 / 12

## [1.3.0] — 2026-06-11

### Added
- Event editing: a pencil action on the detail screen (writable calendars
  only) opens the event form prefilled with the event. Only fields you
  actually changed are written back; saving an untouched form is a no-op.
  Sections holding data are always shown, regardless of the form-field
  defaults; the calendar itself can't be changed while editing
- Recurring events — scoped writes, chosen when saving (Google model):
  "only this event" (a modified-occurrence exception), "this and all
  following" (the series is split at the occurrence), or "all events in
  the series". Changing the recurrence rule rules out "only this event"
- Deleting a recurring event gained the middle option too: "this and all
  following events" ends the series just before the chosen occurrence
- Recurrence picker (create and edit): one-tap daily/weekly/monthly/yearly
  presets plus a custom step with interval + unit, weekday toggles for
  weekly rules ("every week on Mon and Fri"), and an end condition (never /
  on a date / after a number of times). Rules the picker can't express
  (e.g. "second Thursday monthly") are shown humanized and preserved
  verbatim unless replaced. Recurrence also joined the optional form
  fields and their settings defaults
- Validation: a repeat that would end before the event starts is flagged
  (it would otherwise vanish from every view)

### Changed
- Editing reminders reconciles against the provider's actual rows:
  reminders you didn't touch keep their method (e.g. email reminders on
  synced events survive unrelated edits)
- The contextual WRITE_CALENDAR upgrade for v1.0 installs covers the edit
  action like delete

### Fixed
- Splitting a series ("this and following") sends the complete time-column
  set in one update, so the provider regenerates its cached instances — an
  RRULE-only update left a stale duplicate of the tapped occurrence on the
  split day
- RRULE UNTIL values are written as the local end of day expressed in UTC
  (instead of a flat `T235959Z`), so recurrences can't leak an extra day in
  timezones ahead of UTC
- `versionName`/`versionCode` bumped to 1.3.0 / 11

## [1.2.1] — 2026-06-11

### Added
- Optional event-form fields with user-controlled defaults: reminders,
  availability (busy/free), and visibility (default/public/private/
  confidential) joined location and description as form sections. Settings
  gained a "New event form" section choosing which show by default; the rest
  unfold via a "More fields" picker
- Reminders editor: stacked rows with right-bound remove, full-width add
  action; the picker offers one-tap presets and a custom amount + unit
  (minutes/hours/days/weeks) step
- `OptionCard` — the app's standard selection-dialog row (full-width tonal
  card, optional icon + supporting line, highlighted selection). All dialogs
  (calendar, visibility, more-fields, reminder presets, recurring-delete)
  now use it; radio-row dialogs are retired

### Changed
- Event form redesigned onto the detail screen's design system: tonal cards
  with gutter icons (top-aligned on tall cards), borderless inline text
  fields, calendar-coloured accent bar under the title, no dividers, no
  top-bar title; placeholders render clearly fainter than input
- M3 Expressive motion: the theme now provides a MotionScheme
  (`MaterialExpressiveTheme`, standard springs — expressive bounce reviewed
  as overdone), the FAB stack and "more fields" reveals animate on theme
  springs
- The jump-to-today slide is direction-aware (future → today slides in from
  the left, past → from the right)
- `versionName`/`versionCode` bumped to 1.2.1 / 10

### Fixed
- The keyboard no longer pans the whole event form; the screen stays
  anchored and the focused field scrolls into view (`adjustResize` +
  `imePadding`)

## [1.2.0] — 2026-06-11

### Added
- Create events (milestone 2, slice 2):
  - A "+" FAB on the month, week, and day views opens a new full-screen event
    form, prefilled with the visible day (today at the next full hour, or
    09:00 on other days)
  - The form covers title, all-day toggle, start/end with Material 3 date and
    time pickers (moving the start drags the end along, preserving duration),
    target calendar, location, and description
  - The calendar picker offers only writable calendars and preselects the one
    you last created an event in
  - Validation on save ("ends before it starts", no writable calendar), with
    the same contextual write-permission upgrade as delete
  - All-day events are stored provider-correctly (UTC midnights, exclusive
    end), timed events in the device time zone

### Changed
- The jump-to-today pill now stacks above the new "+" FAB instead of being
  the only floating action
- `versionName`/`versionCode` bumped to 1.2.0 / 9

## [1.1.0] — 2026-06-11

### Added
- Write foundation (milestone 2, slice 1): Calendula can now **delete events**.
  - Delete action on the event detail screen, with a confirmation dialog;
    recurring events choose between "Only this event" (a cancelled exception,
    so the rest of the series survives) and "All events in the series"
  - `WRITE_CALENDAR` permission: onboarding asks for read+write in one system
    dialog, but only read access is required — declining write keeps the app
    fully usable read-only. Existing v1.0 installs are asked for the write
    upgrade in place, on their first delete
  - Read-only calendars (WebCal subscriptions, birthday calendars, …) are
    detected via `CALENDAR_ACCESS_LEVEL` and show no edit/delete actions at all

### Changed
- Onboarding copy no longer claims "read-only"; it now says your data stays on
  the device (still no internet permission, still zero telemetry)
- The placeholder Edit button on the detail screen (a no-op since v0.4) is
  removed until editing ships in a later slice
- `versionName`/`versionCode` bumped to 1.1.0 / 8

## [1.0.0] — 2026-06-11

First public release. Calendula is a read-only, Material 3 Expressive calendar
that lives entirely on top of Android's `CalendarContract` — every calendar
synced to the device (CalDAV via DAVx5, Google, local, WebCal, …) shows up
automatically, with zero telemetry and no internet permission.

### Highlights (accumulated across v0.1 → v0.6)
- Month, week, and day views with a view switcher, swipe navigation, and
  Loading / Failure / Success states on every screen
- Full-screen event detail surfacing every readable `CalendarContract` field —
  times, recurrence (humanised), location, description (with tappable links),
  attendees + roles + your own response, reminders, status, availability,
  access level, and foreign time zones
- Per-calendar visibility filter (grouped by account, persisted) and a Settings
  screen (theme, Material You dynamic colour, week start, app language)
- Material 3 Expressive first-run onboarding for calendar access
- German + English localization throughout

### Changed
- `versionName`/`versionCode` bumped to 1.0.0 / 7

## [0.6.0] — 2026-06-11

### Added
- Full event read (v0.6): the detail screen now surfaces every readable
  `CalendarContract` field that V1 had been dropping —
  - **Reminders** — each configured lead time, humanised ("10 minutes before",
    "1 day before", "At time of event"), read from `CalendarContract.Reminders`
  - **Status** — Tentative / Cancelled chip under the title; a cancelled event
    also strikes through its title (Confirmed shows no chip)
  - **Availability** — a "Free" pill pinned top-right of the title when the
    event doesn't block your time (`Events.AVAILABILITY`, the iCal TRANSP
    field); the default "Busy" is left implicit to avoid noise on every event
  - **Access level** — a Private / Confidential chip when the event isn't public
  - **Attendee role** — organizer / optional / resource badge under each
    attendee, plus the device user's own response ("Your response: …") from
    `Events.SELF_ATTENDEE_STATUS`
  - **Time zone** — shown only for timed events pinned to a zone other than the
    device's, so cross-zone events read unambiguously
  - **Linked URLs** — http(s) links in the description are now tappable
- Domain model rounded out with `Reminder`, `EventStatus`, `Availability`,
  `AccessLevel`, `AttendeeRelationship`, `AttendeeType`, and the attendee/self
  status fields; mappers + unit tests cover every new column's integer codes

### Changed
- Redesigned the first-run grant-access screen — the onboarding a new user
  sees. Material 3 Expressive layout: branded launcher-mark hero, an app-name
  eyebrow, a benefit-led headline, three trust rows (on-device, every calendar,
  no tracking) with tonal icon chips, a full-width filled CTA with a trailing
  arrow, and a "Read-only · no internet permission" footnote (the app declares
  only `READ_CALENDAR`). The denied/recovery state shares the same shell with a
  lock-badged hero and Open-settings / Try-again actions
- `versionName`/`versionCode` bumped to 0.6.0 / 6

### Notes
- A dedicated event **URL** field was dropped from scope: `CalendarContract`
  has no `Events.URL` column (only `CUSTOM_APP_URI`, an app deep-link), so URLs
  are surfaced by linkifying the description instead

## [0.5.0] — 2026-06-10

### Added
- Calendar filter (M3): the navigation drawer now hosts the calendar list
  inline — every calendar grouped by account, each with a colour swatch and a
  visibility switch. Hiding a calendar is persisted app-side (DataStore,
  separate from the system VISIBLE flag) and applied centrally in the
  repository, so month/week/day re-filter live the moment a switch flips.
  The drawer was trimmed to just Today, the calendar filter, and Settings
  (the stubbed jump-to-date entry was removed; jump-to-date was later cut
  from scope entirely)
- Settings (M4): a full-screen destination with
  - **Appearance** — theme (System / Light / Dark), Material You dynamic colour
    (auto-disabled below Android 12), week start (Automatic / Monday / Sunday)
  - **Language** — app language (System / Deutsch / English) via per-app
    locales, persisted across cold starts down to Android 10
  - **About** — version, license, and a link to the source on Gitea
- Week-start preference now drives the month grid and week view; "Automatic"
  follows the active locale (Monday in DE, Sunday in en-US)

### Changed
- Theme is driven by one activity-scoped settings source, so a theme or
  dynamic-colour change applies app-wide immediately
- `versionName`/`versionCode` bumped to 0.5.0 / 5 (the in-repo version had
  lagged behind the release tags); the About screen reads it directly

## [0.4.0] — 2026-06-10

### Added
- Event detail (S4): full-screen destination (MD3 list→detail, not a bottom
  sheet) opened by tapping an event in the week/day timeline — title with a
  calendar-colour accent line, a card per field (when, calendar, location,
  description, attendees, recurrence) with leading icons, location tap opens a
  maps intent, Loading/Failure/Success states, slide-in/out over the calendar
- Human-readable recurrence: RRULE rendered as e.g. "Every week on _Tue_ and
  _Thu_ until 31 Dec 2026" (FREQ/INTERVAL/BYDAY/UNTIL/COUNT, abbreviated +
  italicised day names, localized list formatting), with a generic fallback
- Month → day navigation: tapping a day cell opens the day view on that date

### Fixed
- Recurring events failed to open in the detail view: the series row stores
  DURATION instead of DTEND, so the mapper dropped it (EventNotFound). The
  detail now keeps such events and shows the tapped occurrence's own times
  (from CalendarContract.Instances) instead of the series start

## [0.3.0] — 2026-06-10

### Added
- Month view (S1): Material 3 Expressive card-per-day grid (only the current
  month's weeks; neighbouring days left blank), per-day event dots with "+N"
  overflow, today emphasised via `primaryContainer`, spring-based press
  feedback from the active motion scheme, swipe + drawer navigation,
  Loading/Failure/Success states
- Week view (S2): vertical time schedule with overlap-resolved lanes,
  separate all-day strip, midnight-spanning events clipped per day, swipe
  navigation, Loading/Failure/Success states
- Day view (S3): single-column slice of the week schedule reusing its
  overlap-lane layout, per-day swipe navigation, noon-centred scroll that
  persists across swipes, animated all-day strip, compact top bar with the
  full date, Loading/Failure/Success states
- Functional view-switcher (M1) cycling Month ↔ Week ↔ Day
- Shared calendar UI building blocks in `ui/common/` (navigation drawer,
  failure screen, view-switcher pill, color pastelizer, observable locale)

### Removed
- Throwaway debug screen — superseded by the month view

## [0.2.1] — 2026-06-09

### Changed
- Regenerated the F-Droid catalog `icon.png` (512x512, both locales) so it
  is pixel-faithful to the on-device adaptive launcher icon: same slate
  background (`#5C6B7A`), off-white mark (`#FAF6F0`), and the foreground
  group transform (`scale 0.5`, pivot `114,108`, translate `2,8`) baked in.
- Added `design/icon/calendula_launcher.svg` — the composed full-bleed
  icon (background + transformed mark) as the single source of truth for
  store/F-Droid renders.

## [0.2.0] — 2026-06-08

### Added
- Domain models for calendars, event instances, event detail, attendees
- `CalendarContract`-backed `CalendarRepository` with `ContentObserver`-driven live updates
- DataStore preference for app-side hidden-calendar visibility
- `READ_CALENDAR` permission flow (rationale + denied recovery + system-settings shortcut)
- Wegwerfbarer Debug-Screen: zeigt alle Kalender + die nächsten 50 Termine ab heute
- Hilt-Wiring für Data-Layer (Repository, DataSource, DataStore, IO-Dispatcher)
- Unit-Tests für Cursor-Mapping (alle §8-Defensiv-Cases), Repository-Flows mit Turbine, DataStore round-trip
- Instrumented smoke test against the real CalendarContract provider

### Changed
- Redesigned launcher icon: line-art calendar with a stylized "1" inside
  (kalendae reference) and a small calendula bloom badge in the
  bottom-right corner. Replaces the simple "1"-only foreground from
  v0.1.0. Source SVG checked in at `design/icon/calendula_mark.svg`,
  also used to regenerate the F-Droid catalog `icon.png` (512x512)
  per locale.

## [0.1.1] — 2026-06-08

### Fixed
- F-Droid metadata format: renamed locale dirs from `de/` to `de-DE/`,
  `short_description.txt` to `summary.txt`, `full_description.txt` to
  `description.txt` (fastlane format that fdroidserver actually reads,
  matching the working HouseHoldKeaper convention)
- Added `icon.png` (512x512) per locale; fdroidserver does NOT
  auto-extract icons from APKs that only contain XML adaptive icons
  (which is what minSdk-29 apps produce), so the app was rendered
  blank-iconed in F-Droid clients

### Changed
- CI pipeline cleanup: `lintDebug`/`testDebugUnitTest` instead of full
  `lint`/`test` (cuts ~50% of lint work since release variant lint is
  redundant for V1 single-variant build)
- Release workflow drops the lint step from its CI-sanity job since
  the same lint already ran via `ci.yaml` when the underlying commit
  hit main

## [0.1.0] — 2026-06-08

### Added
- Initial project scaffold (Gradle Kotlin DSL, Version Catalog, Hilt, DataStore)
- Material 3 Expressive theme with Dynamic Color (API 31+) and slate-derived fallback
- Adaptive launcher icon — stylized "1" on slate squircle (references *kalendae*)
- German + English localization infrastructure
- Permission declaration for `READ_CALENDAR` (no UI flow yet — that's Plan 02)
- Gitea CI workflow: lint, unit tests, debug build, Trivy scan
- Gitea release workflow: signed release APK + F-Droid metadata sync to Hetzner
- F-Droid metadata stubs (DE + EN short/full descriptions)
- `.planning/` project-tracking documents

[#1]: https://codeberg.org/jlmakiola/calendula/issues/1
[#2]: https://codeberg.org/jlmakiola/calendula/issues/2
[#3]: https://codeberg.org/jlmakiola/calendula/issues/3
[#4]: https://codeberg.org/jlmakiola/calendula/issues/4
[#5]: https://codeberg.org/jlmakiola/calendula/issues/5
[#6]: https://codeberg.org/jlmakiola/calendula/issues/6
[#7]: https://codeberg.org/jlmakiola/calendula/issues/7
[#8]: https://codeberg.org/jlmakiola/calendula/issues/8
[#9]: https://codeberg.org/jlmakiola/calendula/issues/9
[#10]: https://codeberg.org/jlmakiola/calendula/issues/10
[#12]: https://codeberg.org/jlmakiola/calendula/issues/12
[#13]: https://codeberg.org/jlmakiola/calendula/issues/13
[#14]: https://codeberg.org/jlmakiola/calendula/issues/14
[#15]: https://codeberg.org/jlmakiola/calendula/issues/15
[#16]: https://codeberg.org/jlmakiola/calendula/issues/16
[#17]: https://codeberg.org/jlmakiola/calendula/issues/17
[#18]: https://codeberg.org/jlmakiola/calendula/issues/18
[#19]: https://codeberg.org/jlmakiola/calendula/issues/19
[#20]: https://codeberg.org/jlmakiola/calendula/issues/20
[#22]: https://codeberg.org/jlmakiola/calendula/issues/22
[#24]: https://codeberg.org/jlmakiola/calendula/issues/24
[#25]: https://codeberg.org/jlmakiola/calendula/issues/25
[#27]: https://codeberg.org/jlmakiola/calendula/issues/27
[#29]: https://codeberg.org/jlmakiola/calendula/issues/29
[#21]: https://codeberg.org/jlmakiola/calendula/issues/21
[#30]: https://codeberg.org/jlmakiola/calendula/issues/30
[#31]: https://codeberg.org/jlmakiola/calendula/issues/31
[#32]: https://codeberg.org/jlmakiola/calendula/issues/32
[#33]: https://codeberg.org/jlmakiola/calendula/issues/33
[#34]: https://codeberg.org/jlmakiola/calendula/issues/34
[#36]: https://codeberg.org/jlmakiola/calendula/issues/36
[#37]: https://codeberg.org/jlmakiola/calendula/issues/37
[#39]: https://codeberg.org/jlmakiola/calendula/issues/39
[#35]: https://codeberg.org/jlmakiola/calendula/issues/35
[#40]: https://codeberg.org/jlmakiola/calendula/issues/40
[#46]: https://codeberg.org/jlmakiola/calendula/issues/46
[#47]: https://codeberg.org/jlmakiola/calendula/issues/47
[#48]: https://codeberg.org/jlmakiola/calendula/issues/48
[#49]: https://codeberg.org/jlmakiola/calendula/issues/49
[#51]: https://codeberg.org/jlmakiola/calendula/issues/51
[#52]: https://codeberg.org/jlmakiola/calendula/issues/52
[#38]: https://codeberg.org/jlmakiola/calendula/issues/38
[#53]: https://codeberg.org/jlmakiola/calendula/issues/53
[#60]: https://codeberg.org/jlmakiola/calendula/issues/60
[#65]: https://codeberg.org/jlmakiola/calendula/issues/65
[#74]: https://codeberg.org/jlmakiola/calendula/issues/74
[#42]: https://codeberg.org/jlmakiola/calendula/issues/42
[#44]: https://codeberg.org/jlmakiola/calendula/issues/44
[#70]: https://codeberg.org/jlmakiola/calendula/issues/70
[#75]: https://codeberg.org/jlmakiola/calendula/issues/75
[#76]: https://codeberg.org/jlmakiola/calendula/issues/76
[#78]: https://codeberg.org/jlmakiola/calendula/issues/78
[#77]: https://codeberg.org/jlmakiola/calendula/issues/77
[#79]: https://codeberg.org/jlmakiola/calendula/issues/79
[#81]: https://codeberg.org/jlmakiola/calendula/issues/81
[#82]: https://codeberg.org/jlmakiola/calendula/issues/82
[#83]: https://codeberg.org/jlmakiola/calendula/issues/83
[#87]: https://codeberg.org/jlmakiola/calendula/issues/87
[#89]: https://codeberg.org/jlmakiola/calendula/issues/89
[#103]: https://codeberg.org/jlmakiola/calendula/issues/103
[#69]: https://codeberg.org/jlmakiola/calendula/issues/69
[#54]: https://codeberg.org/jlmakiola/calendula/issues/54
[#57]: https://codeberg.org/jlmakiola/calendula/issues/57
[#56]: https://codeberg.org/jlmakiola/calendula/issues/56
[#114]: https://codeberg.org/jlmakiola/calendula/issues/114
[#148]: https://codeberg.org/jlmakiola/calendula/issues/148
[#68]: https://codeberg.org/jlmakiola/calendula/issues/68
[#80]: https://codeberg.org/jlmakiola/calendula/issues/80
[#123]: https://codeberg.org/jlmakiola/calendula/issues/123
[#163]: https://codeberg.org/jlmakiola/calendula/issues/163
[#173]: https://codeberg.org/jlmakiola/calendula/issues/173
[#164]: https://codeberg.org/jlmakiola/calendula/issues/164
