# Contributing to Calendula

Calendula is a Material 3 Expressive calendar app that lives strictly on top of
Android's `CalendarContract` — no app database, no sync stack, no network access.
That constraint shapes most review comments, so
[`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) is worth skimming before you write
code. This file is the practical how.

**[Codeberg](https://codeberg.org/jlmakiola/calendula) is the canonical home** —
issues, pull requests, releases. The self-hosted Gitea instance referenced in the
release docs is build infrastructure only; there is nothing to contribute there.
Be decent to the people you meet in the tracker.

## Start with an issue

| You want to | Do this |
|---|---|
| Add a feature | **Open an issue first** and wait for a go-ahead |
| Fix a bug | Open an issue, then a pull request |
| Fix a typo, a comment, or docs | Just open the pull request |
| Add or fix a translation | **Don't** — [use Weblate](#translations) |

Features get an opinion before they get code: whether Calendula should do a
thing at all is the one decision a patch can't make. A feature PR that arrives
without a discussed issue may be closed unmerged even when the code is good —
please don't spend a weekend on one first.

Bugs are more straightforward, but still start with an issue: it's what carries
the milestone and gives the changelog something to link.

Issue templates cover bug, crash, feature and question. For a crash, let the app
do the work — **Settings → Report a problem**, or the prompt shown after a crash,
captures the stack trace and prefills the form. The report contains app, Android
and device versions plus the trace; no calendar content, no personal data.

## Which branch to target

Calendula releases by merging a version bump into `main`, so `main` is a release
trigger rather than a staging area. Work is assembled on release branches first.

Once your issue has a milestone, that milestone names your branch:

| Milestone | Target branch |
|---|---|
| `2.18.0` | `release/v2.18.0` |

Every milestone has a matching branch. If it's somehow missing, target `main` and
mention it in the PR — it will be retargeted. Don't pick an older release branch:
they're kept after shipping, so the newest one isn't necessarily yours.

## Translations

**Never edit a `values-*/strings.xml` file in a pull request** — German included.
Translations are owned by a self-hosted Weblate that writes to this repository
directly, and a hand-edit is overwritten on the next sync.

→ **[Translate Calendula on Weblate](https://weblate.dev.jeanlucmakiola.de/engage/calendula/)**

Adding a *new* English string to `values/strings.xml` is normal PR work; Weblate
picks it up and offers it to translators. Partial translations are expected and
fine — missing keys are informational. Stale and orphaned keys are not, so run

```sh
python3 scripts/check_translations.py
```

before pushing. It reports those more clearly than lint's `MissingTranslation`
does.

## Build & test

```sh
git clone --recurse-submodules https://codeberg.org/jlmakiola/calendula.git
```

The `floret-kit` submodule is a composite build compiled from source. An
existing clone needs `git submodule update --init --recursive`, or nothing
resolves.

- **JDK 17** — not newer; the Android Gradle Plugin requires exactly 17. Set
  `JAVA_HOME` if your default differs.
- **Android SDK** — platform 37 (`compileSdk`) and build-tools 36.0.0, located
  via `ANDROID_HOME` or a gitignored `local.properties` with `sdk.dir`. If you
  go the `local.properties` route the included build needs its own copy at
  `floret-kit/local.properties`; `ANDROID_HOME` covers both at once and is the
  easier path.

The Gradle wrapper is checked in, so no system Gradle is needed.

```sh
./gradlew lint test assembleDebug     # roughly what CI runs
```

A single test class, or a pattern:

```sh
./gradlew testDebugUnitTest --tests "de.jeanlucmakiola.calendula.domain.SimpleRecurrenceTest"
./gradlew testDebugUnitTest --tests "*SimpleRecurrence*"
```

CI reports one `CI` check per pull request: `lintDebug`, `testDebugUnitTest`,
`assembleDebug`, and a Trivy scan. Pull requests touching only docs, F-Droid
metadata or the licence skip the Android build and go green quickly. More detail
in [`docs/BUILDING.md`](docs/BUILDING.md).

## The rules

These are the ones that turn into review comments.

1. **No network.** Calendula holds no `INTERNET` permission, and that's a
   feature rather than an oversight. Anything that would need one is a product
   decision before it's a patch — the crash reporter deliberately opens a
   prefilled web issue instead of posting anything itself.
2. **The provider is the only database.** No Room, no cache, no local mirror of
   events. `CalendarContract` is the single source of truth, which is also why
   externally synced changes work for free.
3. **Don't patch UI state after a write.** A `ContentObserver` re-queries and
   views recompose from fresh provider state. Hand-patching a list after saving
   appears to work, then quietly diverges from what the provider actually stored.
4. **`domain/` has no Android imports.** Models, validation, recurrence
   rendering, conflict snapshots and the `.ics` codec stay pure Kotlin so they
   remain JVM-testable.
5. **Tests run on the JVM.** JUnit 5 + Truth + Turbine. The seams exist for you:
   fake the data source (`FakeCalendarDataSource`), and feed mappers plain maps
   through `ColumnReader` instead of cursors. Instrumented tests are a last
   resort, not a default.
6. **Read before touching the subtle pipelines.** Recurring writes (UNTIL vs
   DURATION, exception URIs, series splits), save-conflict detection and reminder
   delivery (post-before-mark) follow provider-driven rules that are documented
   in [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) and are not guessable from
   the code alone.
7. **Don't break reproducible builds.** `vcsInfo`, `dependenciesInfo` and the AGP
   metadata block are disabled on purpose so the official F-Droid repo can verify
   our binary against a from-source rebuild.
   `scripts/check_reproducible_release.sh` runs on every pull request, including
   docs-only ones.

## UI conventions

Material 3 Expressive throughout, built from the system's own tokens and
components — colour-scheme tokens rather than hardcoded colours, `ListItem` for
settings rows.

**Selection pickers are full-screen.** Every browse-style "choose one" surface
uses floret-kit's `FullScreenPicker` / `OptionPicker`; one that needs a commit or
extra action passes it through the picker's `actions` slot. The exception is the
recurring-scope chooser (*this / this and following / all*), which stays a
compact dialog — a two- or three-option decision reads better as a popup than as
a nearly empty screen. `AlertDialog` is for plain confirmations only, and radio-
or text-list dialogs aren't used at all.

Shared UI machinery lives in the `floret-kit` submodule and has
[its own contributing guide](https://codeberg.org/jlmakiola/floret-kit/src/branch/main/CONTRIBUTING.md);
changing it means a pull request against that repository plus a submodule bump
here.

## Commits & pull requests

Conventional commits, scoped to the area you touched:

```
fix(calendars): keep an event's own calendar when it is switched off
feat(month): pull-to-expand the split view (#38)
docs(architecture): record what the second review pass changed
```

Types in use: `feat` `fix` `docs` `refactor` `style` `chore` `ci` `build`
`revert`. Reference the issue in the subject or the body. Keep commits small —
small commits revert cleanly, which matters more here than a tidy history.

If your change is user-visible, add an entry under `## [Unreleased]` in
[`CHANGELOG.md`](CHANGELOG.md). Match the surrounding voice: entries describe
what changed *for the person using the app*, and why, not what changed in the
code. Link the issue and add its reference at the bottom of the file. It may get
reworded when the release is cut, so don't agonise over it.

Please don't commit planning or design documents. Code, tests, architecture notes
and the changelog land; the reasoning belongs in the commit message and the
issue.

## Not going to happen

Saving you the effort:

- **Drag-and-drop event rescheduling.** Considered repeatedly, settled as a no.
- **Anything requiring `INTERNET`**, including a sync stack of our own.
- **An app-side event database.**

## Licence

Calendula is [MIT](LICENSE). By contributing you agree your changes ship under
the same licence.
