# Building from source

Calendula builds with the standard Android Gradle toolchain — no extra setup
beyond the SDK and a JDK.

## Requirements

- **Android SDK 36+**
- **JDK 17** — if your default JDK is not 17, set `JAVA_HOME` explicitly.

The Gradle wrapper is checked in, so you don't need a system Gradle.

## Common tasks

```bash
./gradlew assembleDebug   # debug APK → app/build/outputs/apk/debug/
./gradlew test            # JVM unit tests
./gradlew lint            # Android lint
```

## Release builds

Release signing and the publishing pipeline are documented separately in
[RELEASING.md](RELEASING.md).
