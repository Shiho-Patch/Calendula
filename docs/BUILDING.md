# Building from source

Calendula builds with the standard Android Gradle toolchain — no extra setup
beyond the SDK, a JDK, and the submodule.

## Clone

```bash
git clone --recurse-submodules https://codeberg.org/jlmakiola/calendula.git
```

Calendula depends on **[floret-kit](https://codeberg.org/jlmakiola/floret-kit)**,
the shared Material 3 Expressive kit, as a git submodule wired in as a Gradle
composite build (`includeBuild("floret-kit")` in `settings.gradle.kts`) — it is
compiled from source, not resolved from a repository. A clone without the
submodule fails to configure. For an existing clone:

```bash
git submodule update --init --recursive
```

## Requirements

- **JDK 17** — not newer; the Android Gradle Plugin requires exactly 17. If your
  default JDK differs, set `JAVA_HOME` explicitly.
- **Android SDK** — platform **37** (`compileSdk`) and **build-tools 36.0.0**.
  `minSdk` is 29, `targetSdk` 36.

The SDK is located via `ANDROID_HOME` (or `ANDROID_SDK_ROOT`), or via a
gitignored `local.properties` with `sdk.dir`. If you use `local.properties`, note
that the composite build needs **its own** copy at `floret-kit/local.properties`;
setting `ANDROID_HOME` covers both builds at once and is the simpler route.

The Gradle wrapper is checked in, so you don't need a system Gradle.

## Common tasks

```bash
./gradlew assembleDebug   # debug APK → app/build/outputs/apk/debug/
./gradlew test            # JVM unit tests (JUnit 5 + Truth + Turbine)
./gradlew lint            # Android lint
```

A single test class, or a pattern:

```bash
./gradlew testDebugUnitTest --tests "de.jeanlucmakiola.calendula.domain.SimpleRecurrenceTest"
./gradlew testDebugUnitTest --tests "*SimpleRecurrence*"
```

Translation-key invariants (stale and orphaned keys are fatal; missing ones are
not) are checked outside Gradle:

```bash
python3 scripts/check_translations.py
```

## What CI runs

`.forgejo/workflows/ci.yaml` reports a single `CI` check per pull request on
Codeberg: the reproducible-release guard, then `lintDebug`,
`testDebugUnitTest`, `assembleDebug` and a Trivy filesystem scan. Pull requests
that touch only docs, F-Droid metadata or the licence skip the Android build and
still report green.

## Release builds

Release signing and the publishing pipeline are documented separately in
[RELEASING.md](RELEASING.md). To smoke-test a release candidate locally, the
`releaseTest` build type is an R8-shrunk twin of `release`, debug-signed with its
own `applicationId` suffix so it installs alongside the real app:

```bash
scripts/verify-release.sh
```

## Contributing

See [`../CONTRIBUTING.md`](../CONTRIBUTING.md) for the issue-first workflow,
which branch to target, and the architectural rules a change is reviewed against.
