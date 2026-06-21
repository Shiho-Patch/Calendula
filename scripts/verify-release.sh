#!/usr/bin/env bash
#
# Build the release-candidate APK and install it on a connected device for the
# mandatory pre-tag on-device check (see docs/RELEASING.md).
#
# It builds the `releaseTest` variant: the same R8 shrinking + obfuscation and
# resource shrinking as the published `release` build, but debug-signed and
# with a `.releasetest` applicationId suffix so it installs alongside the
# production and debug apps. This is what surfaces release-only breakage (R8
# stripping) and first-run states (permission not yet granted) that the
# unminified debug build — or a device that already holds the permission —
# silently hides.
#
# Usage: scripts/verify-release.sh
set -euo pipefail

cd "$(dirname "$0")/.."

PKG="de.jeanlucmakiola.calendula.releasetest"
APK="app/build/outputs/apk/releaseTest/app-releaseTest.apk"

echo "==> Building release-candidate APK (releaseTest, R8 minified)…"
./gradlew :app:assembleReleaseTest

echo "==> Installing $PKG …"
adb install -r "$APK"

echo "==> Resetting to a first-run state (revoking calendar permission)…"
# The release build crashed at launch precisely because this state was never
# tested. Force it so the permission gate / onboarding is exercised every time.
adb shell pm revoke "$PKG" android.permission.READ_CALENDAR  2>/dev/null || true
adb shell pm revoke "$PKG" android.permission.WRITE_CALENDAR 2>/dev/null || true

echo
echo "Installed and reset. Now verify ON THE DEVICE before tagging:"
echo "  1. Launch from a clean state — the permission screen must appear (no crash)."
echo "  2. Grant calendar access — the calendar must load with events."
echo "  3. Add both home-screen widgets and confirm they render (not a spinner)."
echo "  4. Exercise the release's headline changes end to end."
echo
echo "Watch for crashes with:  adb logcat -b crash"
echo "Only merge the release branch to main once all of the above pass on a device"
echo "(the merge is what publishes the release — see docs/RELEASING.md)."
