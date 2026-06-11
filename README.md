# Calendula

A modern Material 3 Expressive calendar app for Android.

Calendula is named after the flower of the same name, whose name comes from
the Latin *kalendae* — the first day of the month — the same root as the
word "calendar". Calendula reads from Android's built-in `CalendarContract`,
so any calendar source synced to your device (CalDAV via DAVx5, Google,
local, WebCal subscriptions, ...) is shown.

## Features

- Month, Week, and Day views
- Full event details — attendees, reminders, recurrence, availability, and more
- Create, edit, and delete events — recurring events with scoped writes
  (only this event / this and all following / whole series) and a simple
  recurrence picker
- Reminder notifications, delivered by Calendula itself (tap opens the event)
- Multi-calendar visibility toggle
- Material You Dynamic Color (Android 12+)
- Light/Dark theme follows system
- German + English UI

## Building

Requires Android SDK 36 and JDK 17. The Gradle wrapper is checked in, so no host Gradle install is needed:

```bash
# Build debug APK
./gradlew assembleDebug

# Run unit tests
./gradlew test

# Run lint
./gradlew lint
```

If your default JDK is something other than 17, set `JAVA_HOME` explicitly:

```bash
JAVA_HOME=/path/to/jdk-17 ./gradlew assembleDebug
```

## License

[MIT](LICENSE) — Jean-Luc Makiola, 2026
