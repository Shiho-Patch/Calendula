# Calendula - Plan 04: Reminder Notifications (v1.4)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Calendula stellt Erinnerungen selbst als Notification zu (Etar-Modell).
Der Provider plant die Alarme und broadcastet
`android.intent.action.EVENT_REMINDER` — die sichtbare Notification postet er
**nicht**, das muss eine Kalender-App tun. Für Nutzer, deren einzige
Kalender-App Calendula ist, ist das essenziell, kein Nice-to-have.
`./gradlew lint test assembleDebug` bleibt grün; Release erst nach
On-Device-Review.

**Architecture:** Eigenes kleines Datenmodul `data/reminders/` neben
`data/calendar/` — der Receiver braucht weder Repository noch Flows. Schichtung
wie gehabt: `EventReminderReceiver` (Hilt-EntryPoint) →
`ReminderAlertStore` (Interface, Android-Impl auf `CalendarAlerts`) →
`ReminderNotifier` (NotificationManager). Domain bleibt pure Kotlin
(`ReminderAlert`-Modell, JVM-testbare Textformatierung).

**Recherche-Befunde (AOSP `CalendarAlarmManager` + Etar, 2026-06-11):**

1. Der Provider legt `CalendarAlerts`-Rows **nur für `METHOD_ALERT`-Reminder**
   an (AOSP-Query: `AND method=1`). Der im Roadmap-Eintrag geforderte
   METHOD-Filter (E-Mail überspringen) passiert also schon upstream — wir
   filtern nicht doppelt. Calendula schreibt eigene Reminder ohnehin als
   `METHOD_ALERT`.
2. Der Broadcast ist implizit (Action + `content://com.android.calendar/…`-URI,
   Extra `alarmTime`). Etars Manifest-Receiver ist `exported="true"` mit
   `<data android:scheme="content"/>` — das übernehmen wir (plus Host,
   enger gefasst). Den URI-Inhalt werten wir nicht aus; wir queryen selbst
   "fällig & noch SCHEDULED".
3. Etar postet aus dem Zustand `SCHEDULED ∪ FIRED` und verwaltet Dismiss über
   eigene Services. Wir vereinfachen: nur `STATE_SCHEDULED AND alarmTime <= now`
   posten, danach best-effort auf `FIRED` setzen (braucht `WRITE_CALENDAR`;
   `SecurityException` wird geschluckt). Weggewischte Notifications kommen so
   nie wieder, ohne deleteIntent-Maschinerie: FIRED-Rows fassen wir nicht an.
   Re-Broadcasts ohne Write-Recht ersetzen still (Tag pro Alert +
   `setOnlyAlertOnce`).

**Leitentscheidungen:**

1. **Kein eigenes Alarm-Scheduling** (kein `SCHEDULE_EXACT_ALARM`, kein
   `BOOT_COMPLETED`, kein WorkManager): Zustellung hängt am Provider-Broadcast.
   Etars Zusatz-Maschinerie (eigener AlarmScheduler) kommt erst, wenn sich
   Zuverlässigkeit auf echten Geräten als Problem zeigt (Roadmap: bewusst
   verschoben, ebenso Snooze-/Dismiss-Actions und Battery-Exemption).
2. **Toggle default ON, Onboarding-Schritt danach:** Nach dem Kalender-Grant
   folgt ein zweiter Onboarding-Screen (gleiche Shell wie der Permission-
   Screen): erklärt Reminder, warnt vor Duplikaten (zweite Kalender-App mit
   aktiven Notifications), fragt `POST_NOTIFICATIONS` an (nur API 33+ zeigt
   einen Dialog; minSdk 29). "Später" schaltet den Toggle aus. Der Schritt
   erscheint genau einmal (`reminder_onboarding_done`-Pref) — auch für
   v1.0–v1.3-Upgrader, die das Feature so entdecken.
3. **Settings-Spiegel:** Abschnitt "Erinnerungen" mit demselben Toggle +
   Duplikat-Hinweis. Einschalten fordert `POST_NOTIFICATIONS` kontextuell an,
   wenn sie fehlt.
4. **Tap öffnet das Event-Detail:** Notification-Intent trägt
   eventId/begin/end; `MainActivity` wird `singleTop`, reicht den Key als
   Compose-State an `CalendarHost` durch (gleiches LongArray-Key-Muster wie
   der Detail-Overlay selbst).
5. **Ein Kanal, einfache Inhalte:** Kanal "Erinnerungen"
   (`IMPORTANCE_HIGH`), pro Alert eine Notification (Tag = Alert-Id):
   Titel = Eventtitel (Fallback "(Ohne Titel)"), Text = Zeitspanne
   (ganztägig: Datum, UTC gelesen) + Ort. Kein Grouping/Summary, kein
   Vollbild-Alarm.

---

## Tasks

**Manifest / Resourcen:**
- [x] `POST_NOTIFICATIONS` ins Manifest; Receiver `.reminders.EventReminderReceiver`
      `exported="true"`, Intent-Filter `EVENT_REMINDER` + `data scheme=content
      host=com.android.calendar`; `MainActivity` → `launchMode="singleTop"`
- [x] Monochromes Notification-Icon `drawable/ic_notification.xml`
- [x] Strings DE+EN: Kanal, Onboarding-Copy, Settings-Abschnitt + Hinweis

**Prefs:**
- [x] `SettingsPrefs.remindersEnabled` (default **true**) + Setter;
      `reminderOnboardingDone` (default false) + Setter; `SettingsPrefsTest`

**Data layer (`data/reminders/`):**
- [x] `ReminderAlert`-Modell (in `data/reminders/`, nicht domain — Alerts
      erreichen nie einen Screen): alertId, eventId, begin/end als Millis,
      title, location, isAllDay
- [x] `ReminderAlertStore` (Interface) + `AndroidReminderAlertStore`:
      `dueAlerts(nowMillis)` = `CalendarAlerts` mit
      `STATE_SCHEDULED AND ALARM_TIME <= now`;
      `markFired(ids, nowMillis)` setzt STATE/RECEIVED_TIME/NOTIFY_TIME,
      `SecurityException` → Log (Write-Recht optional)
- [x] `ReminderNotifier`: Kanal lazy anlegen, eine Notification pro Alert
      (Tag = alertId, `setOnlyAlertOnce`, autoCancel, `when` = begin,
      Category EVENT), Content-PendingIntent auf `MainActivity` mit
      eventId/begin/end
- [x] Zeitspannen-Text als pure Funktion (JVM-testbar) + Test

**Receiver:**
- [x] `EventReminderReceiver` (`@AndroidEntryPoint`): Action prüfen,
      `goAsync()`; raus, wenn Pref aus, READ_CALENDAR fehlt oder
      Notifications systemseitig geblockt; sonst posten → `markFired`

**UI:**
- [x] Onboarding-Shell aus `PermissionScreen` extrahieren
      (`OnboardingScaffold` + BenefitRow, intern wiederverwendet)
- [x] `NotificationOnboardingScreen` + ViewModel: Benefit-Rows (verpasst
      nichts / Duplikat-Warnung), Primär-Button fordert `POST_NOTIFICATIONS`
      (API 33+) und lässt den Toggle an, "Später" schaltet ihn aus; beide
      setzen `reminder_onboarding_done`
- [x] `RootScreen`: Kalender-Gate → Reminder-Schritt (einmalig) → `CalendarHost`
- [x] `CalendarHost`: externer Detail-Key (Notification-Tap) wird wie ein
      Event-Tap konsumiert; `MainActivity` parst Intent (onCreate +
      onNewIntent) in Compose-State
- [x] Settings: Abschnitt "Benachrichtigungen" — Toggle (mit kontextuellem
      Permission-Request beim Einschalten) + Duplikat-Hinweistext

**Abschluss:**
- [x] `./gradlew lint test assembleDebug` grün
- [x] CHANGELOG (`[Unreleased]`), ROADMAP-Status; **kein** Tag/Release vor
      On-Device-Review
