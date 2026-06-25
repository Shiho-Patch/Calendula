<div align="center">

<img src="fastlane/metadata/android/en-US/images/icon.png" width="112" alt="Calendula icon">

<h1>Calendula</h1>

<p><strong>A modern Material 3 Expressive calendar for Android.</strong><br>
Reads, writes, and reminds — on top of the system calendar, with zero network access.</p>

<p>
<a href="https://gitea.jeanlucmakiola.de/makiolaj/calendula/actions"><img src="https://gitea.jeanlucmakiola.de/makiolaj/calendula/actions/workflows/ci.yaml/badge.svg?branch=main" alt="CI"></a>
<img src="https://img.shields.io/badge/Android-10%2B-3DDC84?logo=android&logoColor=white" alt="Android 10+">
<img src="https://img.shields.io/badge/Kotlin-Compose-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin + Compose">
<img src="https://img.shields.io/badge/Material%203-Expressive-4285F4" alt="Material 3 Expressive">
<a href="LICENSE"><img src="https://img.shields.io/badge/License-MIT-green" alt="MIT License"></a>
</p>

<p>
<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/01-week.png" width="19%" alt="Week view">&nbsp;
<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/02-month.png" width="19%" alt="Month view">&nbsp;
<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/04-detail.png" width="19%" alt="Event detail">&nbsp;
<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/05-edit.png" width="19%" alt="Event form">&nbsp;
<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/06-onboarding.png" width="19%" alt="Reminder onboarding">
</p>

</div>

Calendula is named after the flower whose name — like the word *calendar* —
comes from the Latin *kalendae*, the first day of the month. It lives
entirely on top of Android's `CalendarContract`: any calendar synced to your
device (CalDAV via DAVx5, Google, local, WebCal subscriptions, …) simply
appears, and everything you create or edit syncs back the same way. No own
database, no sync stack reinvented.

## ✨ Features

**Calendar**

- Month, week, and day views with a one-tap view switcher
- Full event details — attendees and their responses, reminders, recurrence
  (humanized), availability, visibility, foreign time zones
- Per-calendar visibility toggle, grouped by account

**Editing**

- Create, edit, and delete events — including recurring events with scoped
  writes: *only this event*, *this and all following*, or *the whole series*
- Recurrence picker with one-tap presets and custom rules (interval, weekday
  toggles, end conditions); rules it can't express are preserved verbatim
- Conflict-safe saves: if an event changed elsewhere while you were editing,
  Calendula asks instead of silently overwriting
- Read-only calendars (WebCal, birthdays) are detected and respected

**Reminders**

- Event reminders delivered by Calendula itself as notifications —
  essential when it's your only calendar app, since Android delegates
  reminder delivery to calendar apps
- Tap a reminder to land on the event

**Design & privacy**

- Real Material 3 Expressive throughout — dynamic color (Android 12+),
  expressive motion and shapes, light/dark theme
- German and English UI, per-app language setting
- **Zero telemetry, zero analytics, no internet permission** — your data
  never leaves the device

## 📦 Install

Calendula ships through a self-hosted F-Droid repository; every version tag
is built, signed, and published there automatically.

1. Install an F-Droid client ([F-Droid](https://f-droid.org), Droid-ify, Neo
   Store, …).
2. Add the repository — open this link on your phone, or paste it under
   *Settings → Repositories → Add*:

   ```
   https://apps.dev.jeanlucmakiola.de/dev/fdroid/repo?fingerprint=C2C0640402BF458FC0ED957AF0B37AA4C14022E72F89CE90B5965B458CF73425
   ```

   <sub>Repo: `https://apps.dev.jeanlucmakiola.de/dev/fdroid/repo` ·
   fingerprint (SHA-256):
   `C2C0 6404 02BF 458F C0ED 957A F0B3 7AA4 C140 22E7 2F89 CE90 B596 5B45 8CF7 3425`</sub>

3. Refresh, search for **Calendula**, install. Updates arrive like any
   other F-Droid app.

Alternatively, build from source — see below.

## 🛠 Building

Requires Android SDK 36+ and JDK 17. The Gradle wrapper is checked in:

```bash
./gradlew assembleDebug   # debug APK
./gradlew test            # JVM unit tests
./gradlew lint            # Android lint
```

If your default JDK is not 17, set `JAVA_HOME` explicitly.

## 🏗 Architecture

Single-activity Compose app, layered `UI → Repository → DataSource →
CalendarContract`, observer-driven refresh, JVM-first tests. The full tour —
including the recurring-write and reminder pipelines — lives in
[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## 🗺 Roadmap

Shipped: read (v1.0), write (v1.1–v2.0), reminder delivery (v1.4).
Next up: power-user features — widget, search, tablet layouts. The living
roadmap is in [.planning/ROADMAP.md](.planning/ROADMAP.md), the release
history in [CHANGELOG.md](CHANGELOG.md).

## 💛 Support

Calendula is free, open source, and built by one person in their own time —
with no ads, no tracking, and no internet permission. If it makes your days a
little calmer and you'd like to support its continued development, you can buy
me a coffee:

[**☕ ko-fi.com/jeanlucmakiola**](https://ko-fi.com/jeanlucmakiola)

Entirely optional, always appreciated. 🌼

## 📜 License

[MIT](LICENSE) — Jean-Luc Makiola, 2026
