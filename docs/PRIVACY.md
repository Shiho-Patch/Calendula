---
title: Privacy Policy — Calendula
description: What Calendula does with your data. It collects nothing, sends nothing, and has no internet permission at all.
updated: 2026-09-15
---

<!--
  THIS FILE IS THE POLICY. Edit it here, in a PR, reviewed like any other
  change — it is the source of truth.

  It is published at https://jeanlucmakiola.de/calendula/privacy, which is what
  the app's Settings → About → Privacy policy row opens and what the Play
  Console field must hold. That page holds no copy of the prose: the website
  build checks this repository out beside itself and renders this file through
  an Astro content collection, so there is exactly one copy of the policy
  anywhere and the two cannot drift. An edit here reaches the live page on the
  site's next build.

  The page supplies its own <h1> from the `title` above, so this body starts at
  the first section — do not add one. This comment is HTML so that it cannot
  render on the published page.
-->

**Last updated:** 28 July 2026  
Applies to the Android app **Calendula** (package `de.jeanlucmakiola.calendula`), all versions and all distribution channels.

## In short

Calendula collects nothing, sends nothing, and has no user accounts. It has **no internet permission at all** — the app is technically incapable of transmitting your data anywhere. Everything it shows you is read from the calendars that already exist on your device.

## 1. Controller

IT-Dienstleister | Jean-Luc Makiola  
Mahlerstraße 10  
14772 Brandenburg an der Havel  
Email: [support@jeanlucmakiola.de](mailto:support@jeanlucmakiola.de)

## 2. No data collection

Calendula contains **no analytics, no tracking, no advertising, no crash-reporting SDK and no third-party services of any kind**. No user profile is created, no identifier is generated, and no data is shared with or sold to anyone.

The app does not request the `android.permission.INTERNET` permission. Without it, Android prevents the app from opening any network connection. This is verifiable: inspect the permission list of any released APK, or read the [source code](https://codeberg.org/jlmakiola/calendula).

## 3. Data Calendula accesses on your device

All of the following is processed **locally on your device only**. None of it is transmitted, and none of it is stored by the developer.

### Calendar data

Calendula is a viewer and editor for the calendars Android already manages. It reads and writes events, reminders and calendar settings through Android's system calendar provider. The app keeps **no database of its own** — your events live in the system calendar, exactly where they lived before you installed Calendula, and they remain there if you uninstall it.

Note: if one of those system calendars is itself synchronised with an online account (for example a Google account, or a CalDAV server via DAVx5), that synchronisation is performed by Android and that other app — not by Calendula. The privacy policy of the respective provider applies to it.

### Contacts (optional)

The “Contact special dates” feature reads birthdays and anniversaries from your contacts and mirrors them one-way into a local calendar, so they appear alongside your other events. This feature is switched off by default, the contacts permission is requested only when you enable it, and it is never requested at startup. Contacts are only ever read, never modified, and the data does not leave your device.

### Notifications

Reminders are displayed as local notifications on your device. Nothing is sent to a push service.

### Files

When you import or export an ICS file, Calendula reads or writes exactly the file you select through Android's system file picker. The optional automatic backup writes an ICS export to the folder you choose. The app has no access to other files.

### App settings

Your preferences (view options, theme, reminder defaults and similar) are stored locally on your device and are removed when you uninstall the app.

## 4. Crash reports — the only case where data can leave your device

If Calendula crashes, it offers to report the problem. Nothing is sent automatically. The report is copied to your clipboard and your browser is opened with the project's issue tracker, the text pre-filled. **You see the full content, you decide whether to submit it, and you can edit or discard it.**

Such a report contains:

- app version,
- Android version,
- device manufacturer and model,
- your device language,
- the timestamp,
- and the technical stack trace.

It contains **no** event data, **no** contacts and **no** personal identifiers.

If you choose to submit it, the report becomes a public issue on the project's issue tracker at Codeberg, operated by Codeberg e. V. Their privacy policy then applies to that submission.

## 5. External links

The settings screen contains links to the source code, the licence, the issue tracker and a voluntary donation page (Ko-fi). Following one of these links opens your browser and leaves the app; the privacy policy of the respective website then applies. Calendula transmits no data of yours in the process — it only opens the address.

## 6. Permissions and why they exist

- `READ_CALENDAR`, `WRITE_CALENDAR` — display and edit your events; the core function.
- `POST_NOTIFICATIONS` — show reminders.
- `READ_CONTACTS` — optional, only for the “Contact special dates” feature.
- `USE_EXACT_ALARM`, `SCHEDULE_EXACT_ALARM` — deliver reminders at the exact time, including after snoozing.
- `RECEIVE_BOOT_COMPLETED` — re-register pending reminders after a restart.
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` — only to open the system dialog for the “Reliable delivery” setting.
- `WAKE_LOCK`, `FOREGROUND_SERVICE`, `ACCESS_NETWORK_STATE` — required by the Android system component used for scheduled background work (WorkManager).

`ACCESS_NETWORK_STATE` allows reading *whether* a network connection exists — it does **not** permit using one. Without `INTERNET`, no connection is possible.

## 7. Distribution channels

Calendula is distributed via F-Droid, Obtainium, a self-hosted repository and, where applicable, the Google Play Store. When you download or update the app, the operator of that channel processes data (such as your IP address) under their own privacy policy. This is outside the developer's control and unrelated to the app's own behaviour.

## 8. Children

Calendula is suitable for all ages. Since it collects no data at all, no data of children is processed either.

## 9. Your rights

Because the developer processes no personal data of yours, there is no stored data to which rights of access, rectification, erasure, restriction, data portability or objection (Art. 15–21 GDPR) could apply. You may nevertheless contact the address above at any time with any question. You also have the right to lodge a complaint with a supervisory authority.

## 10. Changes to this policy

Should the app's functionality change in a way that affects data processing, this policy will be updated and the date at the top adjusted. The version history is publicly traceable in the project's source repository.
