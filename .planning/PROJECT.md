# Calendula

## What This Is

A modern Material 3 Expressive Android calendar app, read-only V1. Lives
entirely on top of Android's `CalendarContract` — any calendar synced to the
device (CalDAV via DAVx5, Google, local, WebCal, …) shows up automatically.
The differentiator is visual: real Material 3 Expressive design that no
existing FOSS calendar app delivers.

## Core Value

A calendar app that is genuinely pleasant to look at and use, without
re-inventing the calendar sync stack — leave that to DAVx5 and the system.

## Current Milestone

**v0.1 — Foundation & CI:** Buildable Android project scaffold with theme,
icon, i18n, Hilt, DataStore, green CI.

## Stack

Kotlin 2.3, Jetpack Compose + Material 3 Expressive (alpha for 1.5 APIs),
Hilt, DataStore. Gradle Kotlin DSL with Version Catalog. AGP 9.1, Gradle 9.5.

Read-only V1, write support V2.

Android-only (minSdk 29, targetSdk 36). No iOS.

## Naming

"Calendula" — Latin *kalendae* ("first day of the month", root of "calendar")
is also the etymological root of the marigold flower Calendula. The icon
shows a stylized "1" on a slate squircle.

## Source

Hosted on self-hosted Gitea, released through self-hosted F-Droid repo on
Hetzner. Same infrastructure as `HouseHoldKeaper`.
