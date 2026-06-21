#!/usr/bin/env bash
# Reproducibility guard for the official F-Droid repo.
#
# F-Droid only republishes OUR signed binary if a from-source build reproduces
# it byte-for-byte. AGP's VCS-info injection writes
# META-INF/version-control-info.textproto with build-environment git metadata
# (commit hash / path) — env-dependent, and the one thing that broke
# reproducibility before we disabled it. It MUST stay off on the release build,
# or new versions silently stop publishing to the official repo (fails safe, but
# you'd be stuck on an old version without noticing). Fail loudly if it regresses.
set -euo pipefail
F="app/build.gradle.kts"

# Match `vcsInfo { ... include = false ... }` within the block, tolerant of
# whitespace/newlines (-z: whole file as one record; [^}] stays inside the block).
if grep -Pzoq 'vcsInfo\s*\{[^}]*include\s*=\s*false' "$F"; then
  echo "OK: release build disables AGP VCS-info — stays reproducible for F-Droid."
else
  echo "ERROR: '$F' release build is missing 'vcsInfo { include = false }'." >&2
  echo "AGP would embed env-dependent git metadata (version-control-info.textproto)," >&2
  echo "breaking F-Droid reproducible builds. Restore it. See docs/fdroid-official/." >&2
  exit 1
fi
