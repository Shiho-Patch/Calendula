# Calendula

## What This Is

A modern Material 3 Expressive Android calendar app. Lives entirely on top
of Android's `CalendarContract` — any calendar synced to the device (CalDAV
via DAVx5, Google, local, WebCal, …) shows up automatically; creating,
editing, and deleting writes straight back, and reminders are delivered by
the app itself (Etar model). The differentiator is visual: real Material 3
Expressive design that no existing FOSS calendar app delivers.

## Core Value

A calendar app that is genuinely pleasant to look at and use, without
re-inventing the calendar sync stack — leave that to DAVx5 and the system.

## Current Milestone

Milestones 1 (read, v1.0) and 2 (write support, v1.1–v2.0.0 incl. reminder
delivery) are **complete** — v2.0.0 shipped 2026-06-11. Next is v3.0
(power-user features) plus an undecided "Locations & People" idea backlog;
see `ROADMAP.md`.

## Stack

Kotlin 2.3.21 (paired with KSP 2.3.9 — Kotlin 2.4.0 has no KSP release
yet, do not upgrade until one ships). Jetpack Compose + Material 3
Expressive 1.5.0-alpha21 (alpha is intentional — Expressive APIs only
live in the 1.5 alpha line). Hilt 2.59.2, DataStore. Gradle Kotlin DSL
with Version Catalog. AGP 9.1.1, Gradle 9.5.1. JVM target 17.

Android-only (minSdk 29, targetSdk 36). No iOS. No `INTERNET` permission —
any feature that would need one is an explicit product decision first.

## Naming

"Calendula" — Latin *kalendae* ("first day of the month", root of "calendar")
is also the etymological root of the marigold flower Calendula. The icon
shows a stylized "1" on a slate squircle.

## Source

Hosted on self-hosted Gitea, released through self-hosted F-Droid repo on
Hetzner. Same infrastructure as `HouseHoldKeaper`.
