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
| v1.3 | Edit: Formular wiederverwendet, Serien-Edit, Reminder-Edit, einfacher Recurrence-Picker | offen |
| v2.0 | Quick-Add, Occurrence-Edit, Konflikt-Dialog, Polish-Pass, Release | offen |

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

## v1.3 — Edit (Skizze)

- Formular lädt `EventDetail`, Dirty-Check, `update` auf Events-Row
- Reminder hinzufügen/entfernen (`Reminders`-Insert/Delete)
- Einfacher Recurrence-Picker (FREQ + INTERVAL + UNTIL/COUNT)

## v2.0 — Abschluss (Skizze)

- Quick-Add-Sheet (Titel + Zeit, Rest Defaults)
- Occurrence-Edit (Exception mit geänderten Werten)
- Konflikt-Dialog beim Speichern
- Changelog, F-Droid-Metadaten, Release-Tag
