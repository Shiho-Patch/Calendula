# Calendula - Plan 03: Write Support (Milestone 2 / v2.0)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Calendula kann Events anlegen, bearbeiten und löschen — direkt über
`CalendarContract`-Writes, ohne eigene DB. Der V1-Spec dient als Leitplanke,
nicht als Gesetz: Ausgeliefert wird in vier Slices (v1.1 → v2.0), jeder Slice
ist für sich releasebar und lässt `./gradlew lint test assembleDebug` grün.

**Architecture:** Writes laufen durch dieselbe Schichtung wie Reads:
`ui/` → `CalendarRepository` (Interface) → `CalendarDataSource` →
`ContentResolver.insert/update/delete`. Kein neuer Layer, keine Transaktions-
Abstraktion — der Provider notified nach jedem Write selbst, der bestehende
`ContentObserver`-Tick aktualisiert alle Views automatisch (F3 gilt unverändert).
Domain bleibt pure Kotlin.

**Leitentscheidungen (Abweichungen / Präzisierungen ggü. Spec §2 "V2"):**

1. **Permission-Strategie:** `WRITE_CALENDAR` kommt ins Manifest. Das Onboarding
   fragt READ+WRITE zusammen an (eine System-Dialog-Gruppe), zwingend bleibt
   nur READ — wer Write ablehnt, nutzt die App weiter read-only.
   v1.0-Upgrader (haben nur READ) bekommen den WRITE-Request kontextuell beim
   ersten Schreib-Versuch. Onboarding-Footnote verliert die "Nur Lesezugriff"-
   Behauptung (wäre mit Manifest-Eintrag gelogen).
2. **Read-only-Kalender respektieren:** `Calendars.CALENDAR_ACCESS_LEVEL` wird
   mitgelesen (`canModifyContents` = Level ≥ `CAL_ACCESS_CONTRIBUTOR`).
   Edit/Delete-Actions erscheinen gar nicht erst für WebCal-Subscriptions,
   Geburtstags- und andere read-only-Kalender.
3. **Recurring Events:** Löschen bietet "Nur dieser Termin" (Exception-Insert
   via `Events.CONTENT_EXCEPTION_URI` mit `STATUS_CANCELED` +
   `ORIGINAL_INSTANCE_TIME`) vs. "Ganze Serie" (Delete der Events-Row).
   Bearbeiten startet mit "ganze Serie"; Occurrence-Edit (Exception mit neuen
   Werten) folgt erst, wenn das Serien-Edit stabil ist.
4. **Kein RRULE-Editor in v1.2:** Create startet ohne Wiederholungs-UI
   (einmalige Events). Ein einfacher Recurrence-Picker (täglich/wöchentlich/
   monatlich/jährlich + Ende) kommt mit v1.3/v2.0.
5. **Conflict UX (Spec V2 "event modified externally during edit"):** kein
   Locking. Beim Speichern wird gegen die beim Laden gemerkte Row verglichen
   (Dirty-Check auf den editierten Feldern); bei externem Konflikt Dialog
   "Überschreiben / Verwerfen". Mehr ist YAGNI.

---

## Slices

| Slice | Inhalt | Status |
|---|---|---|
| v1.1 | Write-Fundament: `WRITE_CALENDAR`, `canModifyContents`, Delete (Serie + einzelnes Vorkommen) | ausgeliefert (v1.1.0, 2026-06-11) |
| v1.2 | Create: Event-Formular (Titel, Kalender, ganztägig, Start/Ende, Ort, Beschreibung), FAB, Default-Kalender-Pref | ausgeliefert (v1.2.0, 2026-06-11) |
| v1.3 | Edit: Formular wiederverwendet, Serien-Edit, Reminder-Edit, einfacher Recurrence-Picker | ausgeliefert (v1.3.0, 2026-06-11) |
| v2.0 | Konflikt-Dialog, Polish-Pass (Store-Copy, Screenshots), Release | ausgeliefert (v2.0.0, 2026-06-11) |

## v1.1 — Write-Fundament + Delete

**Build/Manifest:**
- [x] `AndroidManifest.xml`: `WRITE_CALENDAR` ergänzen

**Data layer:**
- [x] `Projections.kt`: `CALENDAR_ACCESS_LEVEL` in `CalendarProjection`
- [x] `Models.kt`: `CalendarSource.canModifyContents: Boolean` (Default `false`).
      Kein neuer `FailureReason` — Delete-Fehler sind ein Snackbar-Fall, kein
      Full-Screen-Failure
- [x] `CalendarMapper.kt`: Access-Level → `canModifyContents`
- [x] `CalendarDataSource`: `deleteEvent(eventId)`, `deleteOccurrence(eventId, beginMillis)`
      — Impl in `AndroidCalendarDataSource` (`delete` auf Events-URI bzw.
      Exception-Insert), `WriteFailedException` bei 0 rows / null-Uri
- [x] `CalendarRepository(+Impl)`: beide Methoden durchreichen, auf `io`

**UI:**
- [x] `EventDetailUiState.Success.canModify` (Kalender-Lookup im ViewModel)
- [x] `EventDetailViewModel`: `delete(mode)` mit eigenem One-Shot-State
      (Idle/Deleting/Deleted/Failed); `SecurityException` → kontextueller
      WRITE-Request statt Failure-Screen
- [x] `EventDetailScreen`: Edit/Delete nur wenn `canModify`; Delete →
      Confirm-Dialog (recurring: "Nur dieser Termin" / "Ganze Serie"),
      Erfolg → zurück, Fehler → Snackbar
- [x] Onboarding (`PermissionScreen`): `RequestMultiplePermissions` READ+WRITE,
      Gate bleibt READ; Copy-Anpassung (Footnote, Rationale-Body) DE+EN

**Tests:**
- [x] `FakeCalendarDataSource`: Write-Ops aufnehmen
- [x] `CalendarRepositoryImplTest`: delete-Pfade (Erfolg, Fehler)
- [x] `CalendarMapperTest`: Access-Level-Mapping

## v1.2 — Create

- [x] `EventForm`-Domain-Modell + Validierung (`problems()`: EndBeforeStart,
      NoCalendar; leerer Titel und Instant-Events erlaubt)
- [x] `EventEditScreen` (ein Formular, ab v1.3 auch für Edit), M3-Date/Time-Picker
- [x] FAB-Stack auf allen drei Hauptansichten (`CalendarFabColumn`: "+" immer,
      Heute-Pill darüber), vorbelegt mit dem sichtbaren Tag
- [x] Kalender-Vorauswahl: explizit > zuletzt benutzt
      (`CalendarPrefs.lastUsedCalendarId` statt Settings-Eintrag) > erster
      beschreibbarer; Picker bietet nur beschreibbare Kalender an
- [x] `insertEvent(form): Long` im DataSource; `EventWriteMapper` (JVM-testbar)
      normalisiert all-day auf UTC-Mitternächte mit exklusivem DTEND

## v1.3 — Edit

**Domain:**
- [x] `EventForm.rrule` (roher RRULE-Wert, null = einmalig); komplexe Regeln
      (ordinales BYDAY wie "2TH", BYMONTHDAY etc.) bleiben verbatim erhalten,
      solange der Picker sie nicht ersetzt
- [x] `SimpleRecurrence` (FREQ + INTERVAL + UNTIL/COUNT + wöchentliches
      BYDAY — Review-Feedback: "jede Woche Mo+Fr" muss gehen) mit
      `parseSimpleRecurrence`/`toRRule` (Recurrence.kt, JVM-getestet)
- [x] `EventDetail.toEditForm(begin, end, zone)` — Prefill inkl. all-day-
      Rückrechnung (exklusives DTEND → letzter abgedeckter Tag)
- [x] Validierung: `RecurrenceEndsBeforeStart` (UNTIL vor erstem Tag hieße
      null Vorkommen — Event würde unsichtbar)

**Data layer:**
- [x] `buildEventUpdateValues(original, updated, seriesDtStart, zone)` —
      Dirty-Check, nur geänderte Spalten; Zeitfelder als Einheit:
      einmalig → DTSTART/DTEND (RRULE/DURATION genullt), wiederkehrend →
      Serien-DTSTART verschiebt sich um das User-Delta, DURATION statt DTEND
- [x] `CalendarDataSource.updateEvent(eventId, original, updated)` — Events-Row-
      Update + Reminder-Diff nach Minuten (unberührte Rows behalten ihre Methode)
- [x] `insertEvent` versteht RRULE (RRULE+DURATION statt DTEND — Provider-Invariante)
- [x] `CalendarRepository(+Impl).updateEvent` durchgereicht, auf `io`
- [x] `EventDetailMapper`: Titel bleibt roh (kein "(Ohne Titel)"-Fallback mehr —
      der Detail-Screen ersetzt selbst lokalisiert, das Formular braucht den Rohwert)

**UI:**
- [x] `EventEditViewModel.openForEdit` (lädt Detail, merkt Original für
      Dirty-Check; unverändertes Formular speichert als No-Op); Felder mit
      Werten werden unabhängig vom Settings-Default eingeblendet
- [x] `EventEditScreen`: `editKey`-Parameter, Kalender im Edit-Modus fixiert,
      Repeat-Karte + `RecurrencePickerDialog` (Presets per Tap, Custom-Schritt
      mit Intervall/Einheit + Wochentags-Toggles bei "Wochen" (Wochenstart
      nach Locale, Start-Wochentag vorausgewählt) + Ende nie/Datum/Anzahl,
      OptionCard-Stil)
- [x] Recurrence-Humanizer nach `ui/common/RecurrenceText.kt` (Detail + Formular)
- [x] `EventDetailScreen`: Edit-Action (nur `canModify`, kontextueller
      WRITE-Request wie Delete); Save schließt Formular **und** Detail (die
      getappte Occurrence existiert danach evtl. nicht mehr)
- [x] **Occurrence-Edit (aus v2.0 vorgezogen, Review-Feedback):** Die
      Scope-Frage kommt **beim Speichern** (Google-Modell, Review-Feedback):
      ein dirty wiederkehrender Termin parkt in `SaveUiState.AwaitingScope`,
      der Dialog bietet "Nur dieser Termin / Dieser und alle folgenden /
      Ganze Serie"; bei geänderter Wiederholungsregel entfällt "nur dieser"
      (eine Exception-Row trägt keine eigene Regel). "Nur dieser" schreibt
      eine Modified-Occurrence-Exception (`CONTENT_EXCEPTION_URI`, alle
      Formularwerte, leere Optionals als explizite NULLs weil der Provider
      die Serien-Row klont), Reminder werden gegen die tatsächlichen
      Provider-Rows abgeglichen. "Dieser und folgende" = Serien-Split:
      neues Event mit den Formularwerten (insert zuerst — schlägt es fehl,
      bleibt das Original unberührt), dann Original-RRULE per UNTIL gekappt;
      ab der ersten Occurrence = normales Serien-Update. Ein mitgenommenes
      COUNT zählt in der neuen Serie neu (kein Rest-COUNT-Rechnen wie AOSP)
- [x] **Delete dreistufig (Review-Feedback):** "Nur dieser Termin" /
      "Dieser und alle folgenden" (RRULE-Truncation via `rruleTruncatedAt`)
      / "Alle Termine der Serie"; ab der ersten Occurrence = ganze Serie
      löschen
- [x] **Split-Duplikat-Bugfix (On-Device-Review):** Nach dem Serien-Split
      blieb die getappte Occurrence doppelt sichtbar. Root cause (per
      adb-Probe verifiziert): der Provider regeneriert die Instances eines
      Events nur aus den **Values des Updates selbst** — ein RRULE-only-
      Update lässt die alten Instances stehen, und ein Teilset (nur DTSTART)
      erzeugt kaputte Nulllängen-Instanzen. Truncation-Updates schicken
      deshalb das komplette Zeit-Set (DTSTART/DURATION/RRULE/ALL_DAY/
      EVENT_TIMEZONE) zusammen (`truncateSeries`), wie AOSPs
      EditEventHelper. Zusätzlich (Robustheit, Google-Modell): Cutoff =
      Ende des Vortags in der Event-Zeitzone (`previousLocalDayEndUtcMillis`)
      statt Occurrence−1s, und der Recurrence-Picker rendert UNTIL als
      lokales Tagesende in UTC (`toRRule(zone)`) statt pauschal `T235959Z`
      (sonst kann bei UTC+x ein Extra-Tag hineinrutschen)
- [x] `CalendarHost`: Edit-Overlay mit Held-Key-Pattern
- [x] `EventFormField.Recurrence` (Formular, "Mehr Felder", Settings-Default)
- [x] Strings DE+EN

**Tests:**
- [x] `RecurrenceTest` (Parse/Render/Roundtrip, Ablehnung komplexer Regeln)
- [x] `EventFormTest`: Prefill (timed/all-day), `populatedFields`, UNTIL-Validierung
- [x] `EventWriteMapperTest`: Duration-Format, Dirty-Check-Pfade (Text-only,
      Zeit einmalig/wiederkehrend, Recurrence an/aus, Reminder-only)
- [x] `CalendarRepositoryImplTest` + `FakeCalendarDataSource`: update-Pfade
- [x] `EventDetailMapperTest`: roher Titel

Bewusst nicht in v1.3 (→ v2.0): Konflikt-Dialog, Kalender-Wechsel beim
Bearbeiten (Sync-Adapter-Minenfeld, sperren auch alle Stock-Apps).

## v2.0 — Abschluss (Scope-Recut 2026-06-11, nach v1.4)

- ~~Quick-Add-Sheet (Titel + Zeit, Rest Defaults)~~ — **gestrichen**: das
  Formular öffnet bereits vorbefüllt (sichtbarer Tag, zuletzt benutzter
  Kalender, optionale Felder versteckt); der Sheet spart nur einen
  Screen-Übergang und kostet eine zweite Create-Surface. Nur bei
  Praxis-Feedback wieder aufnehmen
- ~~Occurrence-Edit (Exception mit geänderten Werten)~~ — schon in v1.3
  ausgeliefert (vorgezogen)
- [x] Konflikt-Dialog beim Speichern (Leitentscheidung 5): `EditSnapshot`
      (Formular + rohe Row-Zeiten) wird beim Laden gemerkt und vor dem
      Schreiben gegen einen frischen Read verglichen; Abweichung parkt den
      Save in `AwaitingConflict` (Überschreiben/Verwerfen/Abbrechen,
      OptionCard-Stil), gelöschtes Event → `Gone`-Dialog. "Überschreiben"
      schreibt weiterhin nur dirty Felder
- Kalender-Wechsel beim Bearbeiten → v3-Backlog (copy+delete-Modell)
- [x] Polish: F-Droid-Description + README auf Write-Support + Reminder
      aktualisiert (DE+EN)
- [x] F-Droid-Screenshots (de-DE + en-US, je 6: Woche/Monat/Tag/Detail/
      Formular/Onboarding) — mit Demo-Kalendern auf dem Gerät aufgenommen
- [x] Changelog, Release-Tag v2.0.0 (ausgeliefert 2026-06-11 — Milestone 2
      damit abgeschlossen)
